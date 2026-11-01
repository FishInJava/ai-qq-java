package com.hobozoo.aiqq.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.hobozoo.aiqq.common.util.Snippet;
import com.hobozoo.aiqq.entity.rag.Passage;
import com.hobozoo.aiqq.service.rag.ChatService;

/**
 * 对话流：可选 RAG，逐字返回生成，附引用、数字自检与链路。
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    public record ChatRequest(String question, String sessionId, Long documentId, String requestId,
                              Boolean useRag, Boolean strictNumbers) {
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        SseEmitter emitter = new SseEmitter(0L);
        try {
            chat.chat(new ChatService.ChatRequest(request.question(), request.sessionId(), request.documentId(),
                    request.requestId(), Boolean.TRUE.equals(request.useRag()),
                    Boolean.TRUE.equals(request.strictNumbers())), new ChatService.Listener() {
                @Override
                public void onStart(String queryId, String sessionId) {
                    send(emitter, "start", Map.of("queryId", queryId, "sessionId", sessionId));
                }

                @Override
                public void onStage(String stage, long durationMs) {
                    send(emitter, "stage", Map.of("stage", stage, "ms", durationMs));
                }

                @Override
                public void onCitation(int index, Passage passage, boolean candidate) {
                    send(emitter, "citation", Map.of(
                            "index", index,
                            "doc", passage.filename(),
                            "documentId", passage.documentId(),
                            "pageFrom", passage.pageFrom(),
                            "pageTo", passage.pageTo(),
                            "section", String.join(" / ", passage.sectionPath()),
                            "chunkId", passage.chunkId(),
                            "snippet", Snippet.of(passage.text()),
                            "candidate", candidate));
                }

                @Override
                public void onDelta(String text) {
                    send(emitter, "delta", Map.of("text", text));
                }

                @Override
                public void onNumberCheck(int total, List<String> missing) {
                    send(emitter, "number_check", Map.of("total", total, "missing", missing));
                }

                @Override
                public void onDone(ChatService.Outcome outcome) {
                    send(emitter, "done", Map.of(
                            "queryId", outcome.queryId(),
                            "latencyMs", outcome.latencyMs(),
                            "firstTokenMs", outcome.firstTokenMs(),
                            "tokensIn", outcome.tokensIn(),
                            "tokensOut", outcome.tokensOut(),
                            "refused", outcome.refused(),
                            "passages", outcome.passages(),
                            "invalidCitations", outcome.invalidCitations()));
                }
            });
            emitter.complete();
        } catch (RuntimeException e) {
            send(emitter, "error", Map.of(
                    "code", "CHAT_FAILED",
                    "message", String.valueOf(e.getMessage()),
                    "retryable", true));
            emitter.complete();
        }
        return emitter;
    }

    private static void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
