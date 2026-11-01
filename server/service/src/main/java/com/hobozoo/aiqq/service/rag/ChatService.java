package com.hobozoo.aiqq.service.rag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.hobozoo.aiqq.common.util.JsonText;
import com.hobozoo.aiqq.dao.LibraryDao;
import com.hobozoo.aiqq.entity.rag.Passage;
import com.hobozoo.aiqq.entity.rag.QueryRow;
import com.hobozoo.aiqq.entity.rag.TurnRow;

/**
 * 一次对话：可选检索、拼提示词、流式生成、数字自检、落链路。
 */
@Service
public class ChatService {

    public static final String REFUSAL = "现有资料里没有找到相关内容";

    private static final String RAG_SYSTEM = """
            你是研报检索助手。只依据用户消息里编号的原文回答，每个结论句末标 [n]。
            原文里没有的数字不要写；原文回答不了时只回「现有资料里没有找到相关内容」。
            """;
    private static final String STRICT_SUFFIX = "\n本次重答只能使用原文中出现过的数字，原文没有的数字一个都不要写。";
    private static final String CHAT_SYSTEM = "你是一个通用助手，回答准确、简洁。";
    private static final int HISTORY_TURNS = 3;
    private static final int HISTORY_CHARS = 1200;
    private static final int CANDIDATES = 3;

    private final LibraryDao library;
    private final RetrievalService retrieval;
    private final ArkChatClient client;

    public ChatService(LibraryDao library, RetrievalService retrieval, ArkChatClient client) {
        this.library = library;
        this.retrieval = retrieval;
        this.client = client;
    }

    public record ChatRequest(String question, String sessionId, Long documentId, String requestId,
                              boolean useRag, boolean strictNumbers) {
    }

    public record Outcome(String queryId, String sessionId, long latencyMs, int tokensIn, int tokensOut,
                          boolean refused, int passages, int firstTokenMs, int invalidCitations) {
    }

    public interface Listener {

        void onStart(String queryId, String sessionId);

        void onStage(String stage, long durationMs);

        void onCitation(int index, Passage passage, boolean candidate);

        void onDelta(String text);

        void onNumberCheck(int total, List<String> missing);

        void onDone(Outcome outcome);
    }

    public Outcome chat(ChatRequest request, Listener listener) {
        String question = request.question().strip();
        Outcome replay = replay(request, listener);
        if (replay != null) {
            return replay;
        }

        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? library.createSession(title(question))
                : request.sessionId();
        String queryId = library.insertQuery(sessionId, blankToNull(request.requestId()), question);
        listener.onStart(queryId, sessionId);

        long startedAt = System.nanoTime();
        long normalizeStart = System.nanoTime();
        library.insertQueryStage(queryId, "normalize", 0,
                JsonText.object(Map.of("chars", question.length(), "rag", request.useRag(),
                        "strict", request.strictNumbers())));
        listener.onStage("normalize", elapsed(normalizeStart));

        List<Passage> passages = List.of();
        try {
            if (request.useRag()) {
                RetrievalService.Retrieval result = retrieval.retrieve(question, request.documentId(), stage -> {
                    library.insertQueryStage(queryId, stage.stage(), stage.durationMs(), stage.payloadJson());
                    listener.onStage(stage.stage(), stage.durationMs());
                });
                if (result.refused()) {
                    return refuse(queryId, sessionId, listener, startedAt, result.passages());
                }
                passages = result.passages();
                int index = 1;
                for (Passage passage : passages) {
                    listener.onCitation(index++, passage, false);
                }
            }

            List<Map<String, String>> messages = messages(queryId, sessionId, question, passages,
                    request.strictNumbers());
            int[] firstToken = {0};
            StringBuilder answer = new StringBuilder();
            CitationFilter citations = new CitationFilter(passages.size());
            ArkChatClient.Usage usage = generate(messages, firstToken, answer, citations, listener, startedAt);
            String rest = citations.flush();
            if (!rest.isEmpty()) {
                answer.append(rest);
                listener.onDelta(rest);
            }

            long latency = elapsed(startedAt);
            library.insertQueryStage(queryId, "generate", latency,
                    JsonText.object(Map.of("tokensIn", usage.tokensIn(), "tokensOut", usage.tokensOut())));
            listener.onStage("generate", latency);

            if (!passages.isEmpty()) {
                long checkStart = System.nanoTime();
                NumberChecker.Result check = NumberChecker.check(answer.toString(),
                        passages.stream().map(Passage::text).toList());
                long checkMs = elapsed(checkStart);
                library.insertQueryStage(queryId, "number_check", checkMs,
                        JsonText.object(Map.of("total", check.total(), "missing", check.missing())));
                listener.onStage("number_check", checkMs);
                listener.onNumberCheck(check.total(), check.missing());
            }
            if (citations.invalid() > 0) {
                library.insertQueryStage(queryId, "citation", 0,
                        JsonText.object(Map.of("invalid", citations.invalid())));
            }

            String text = answer.toString();
            library.finishQueryAnswer(queryId, text, usage.tokensIn(), usage.tokensOut(), latency,
                    firstToken[0], "ok");

            Outcome outcome = new Outcome(queryId, sessionId, latency, usage.tokensIn(), usage.tokensOut(), false,
                    passages.size(), firstToken[0], citations.invalid());
            listener.onDone(outcome);
            return outcome;
        } catch (IOException | RuntimeException e) {
            library.finishQueryAnswer(queryId, null, null, null, elapsed(startedAt), null, "failed");
            library.finishQuery(queryId, "failed", elapsed(startedAt), e.getMessage());
            throw new IllegalStateException("生成失败：" + e.getMessage(), e);
        }
    }

    private Outcome replay(ChatRequest request, Listener listener) {
        String requestId = blankToNull(request.requestId());
        if (requestId == null) {
            return null;
        }
        QueryRow existing = library.findQueryByRequestId(requestId).orElse(null);
        if (existing == null) {
            return null;
        }
        if (existing.answer() == null) {
            throw new IllegalStateException("同一 request_id 已存在，状态：" + existing.status());
        }
        listener.onStart(existing.id(), existing.sessionId());
        listener.onDelta(existing.answer());
        Outcome outcome = new Outcome(existing.id(), existing.sessionId(),
                existing.latencyMs() == null ? 0 : existing.latencyMs(),
                existing.tokensIn() == null ? 0 : existing.tokensIn(),
                existing.tokensOut() == null ? 0 : existing.tokensOut(),
                "refused".equals(existing.status()), 0,
                existing.firstTokenMs() == null ? 0 : existing.firstTokenMs(), 0);
        listener.onDone(outcome);
        return outcome;
    }

    private ArkChatClient.Usage generate(List<Map<String, String>> messages, int[] firstToken,
                                         StringBuilder answer, CitationFilter citations, Listener listener,
                                         long startedAt) throws IOException {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return client.stream(messages, text -> {
                    if (firstToken[0] == 0) {
                        firstToken[0] = (int) elapsed(startedAt);
                    }
                    String filtered = citations.feed(text);
                    if (!filtered.isEmpty()) {
                        answer.append(filtered);
                        listener.onDelta(filtered);
                    }
                });
            } catch (IOException e) {
                if (attempt == 2 || firstToken[0] != 0) {
                    throw e;
                }
                answer.setLength(0);
            }
        }
        throw new IllegalStateException("生成失败");
    }

    private Outcome refuse(String queryId, String sessionId, Listener listener, long startedAt,
                           List<Passage> candidates) {
        int index = 1;
        for (Passage passage : candidates.stream().limit(CANDIDATES).toList()) {
            listener.onCitation(index++, passage, true);
        }
        long latency = elapsed(startedAt);
        listener.onDelta(REFUSAL);
        library.finishQueryAnswer(queryId, REFUSAL, 0, 0, latency, 0, "refused");
        Outcome outcome = new Outcome(queryId, sessionId, latency, 0, 0, true, 0, 0, 0);
        listener.onDone(outcome);
        return outcome;
    }

    private List<Map<String, String>> messages(String queryId, String sessionId, String question,
                                               List<Passage> passages, boolean strictNumbers) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (passages.isEmpty()) {
            messages.add(Map.of("role", "system", "content", CHAT_SYSTEM));
            List<TurnRow> turns = new ArrayList<>(library.recentTurns(sessionId, HISTORY_TURNS));
            Collections.reverse(turns);
            for (TurnRow turn : turns) {
                messages.add(Map.of("role", "user", "content", truncate(turn.question())));
                messages.add(Map.of("role", "assistant", "content", truncate(turn.answer())));
            }
            messages.add(Map.of("role", "user", "content", question));
            library.insertPromptSnapshot(queryId, 1, CHAT_SYSTEM, question, "[]");
            return messages;
        }

        String system = strictNumbers ? RAG_SYSTEM + STRICT_SUFFIX : RAG_SYSTEM;
        StringBuilder user = new StringBuilder("下面是检索到的原文：\n\n");
        List<Map<String, Object>> blocks = new ArrayList<>();
        int index = 1;
        for (Passage passage : passages) {
            user.append('[').append(index).append("] 文件：").append(passage.filename())
                    .append(" 第 ").append(passage.pageFrom());
            if (passage.pageTo() != passage.pageFrom()) {
                user.append('-').append(passage.pageTo());
            }
            user.append(" 页");
            if (passage.sectionPath().length > 0) {
                user.append(" 章节：").append(String.join(" / ", passage.sectionPath()));
            }
            user.append("\n原文：").append(passage.text()).append("\n\n");
            blocks.add(Map.of(
                    "index", index,
                    "doc", passage.filename(),
                    "pageFrom", passage.pageFrom(),
                    "pageTo", passage.pageTo(),
                    "section", String.join(" / ", passage.sectionPath()),
                    "chunkId", passage.parentId()));
            index++;
        }
        user.append("问题：").append(question);

        messages.add(Map.of("role", "system", "content", system));
        messages.add(Map.of("role", "user", "content", user.toString()));
        library.insertPromptSnapshot(queryId, 1, system, user.toString(), JsonText.array(blocks));
        return messages;
    }

    private static String title(String question) {
        return question.length() <= 40 ? question : question.substring(0, 40);
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= HISTORY_CHARS ? text : text.substring(0, HISTORY_CHARS) + "…";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static long elapsed(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
