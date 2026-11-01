package com.hobozoo.aiqq.service.rag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.hobozoo.aiqq.common.util.JsonText;
import com.hobozoo.aiqq.dao.LibraryDao;
import com.hobozoo.aiqq.entity.rag.EvalCaseRow;
import com.hobozoo.aiqq.entity.rag.Passage;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 验收集跑分，按首个引用与来源数量判分。
 */
@Service
public class EvalService {

    private final LibraryDao library;
    private final RetrievalService retrieval;
    private final ObjectMapper mapper;

    public EvalService(LibraryDao library, RetrievalService retrieval, ObjectMapper mapper) {
        this.library = library;
        this.retrieval = retrieval;
        this.mapper = mapper;
    }

    public record CaseResult(long id, String question, String kind, boolean passed, String detail) {
    }

    public record RunResult(int total, int passed, int failed, List<CaseResult> cases) {
    }

    public record NewCase(String queryId, String question, String kind, String expectedDoc, Integer pageFrom,
                          Integer pageTo, List<String> keywords, List<ExpectedSource> sources) {
    }

    public record ExpectedSource(String doc, Integer pageFrom, Integer pageTo) {
    }

    public List<EvalCaseRow> cases() {
        return library.evalCases();
    }

    public long add(NewCase request) {
        if (!Set.of("pos", "neg", "cross").contains(request.kind())) {
            throw new IllegalArgumentException("kind 只能是 pos、neg 或 cross");
        }
        String question = request.question();
        if ((question == null || question.isBlank()) && request.queryId() != null) {
            question = library.findQuery(request.queryId())
                    .orElseThrow(() -> new IllegalArgumentException("查询不存在：" + request.queryId()))
                    .question();
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("缺少 question");
        }
        List<Map<String, Object>> sources = new ArrayList<>();
        for (ExpectedSource source : request.sources() == null ? List.<ExpectedSource>of() : request.sources()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("doc", source.doc());
            item.put("pageFrom", source.pageFrom());
            item.put("pageTo", source.pageTo());
            sources.add(item);
        }
        String[] keywords = request.keywords() == null ? new String[0] : request.keywords().toArray(new String[0]);
        return library.insertEvalCase(question, request.kind(), request.expectedDoc(), request.pageFrom(),
                request.pageTo(), keywords, JsonText.array(sources));
    }

    public RunResult run() {
        List<CaseResult> results = new ArrayList<>();
        for (EvalCaseRow evalCase : cases()) {
            results.add(score(evalCase));
        }
        int passed = (int) results.stream().filter(CaseResult::passed).count();
        return new RunResult(results.size(), passed, results.size() - passed, results);
    }

    private CaseResult score(EvalCaseRow evalCase) {
        RetrievalService.Retrieval result;
        try {
            result = retrieval.retrieve(evalCase.question(), null, stage -> {
            });
        } catch (IOException e) {
            return new CaseResult(evalCase.id(), evalCase.question(), evalCase.kind(), false,
                    "检索失败：" + e.getMessage());
        }
        return switch (evalCase.kind()) {
            case "neg" -> result.refused()
                    ? pass(evalCase, "已拒答")
                    : fail(evalCase, "未拒答，返回 " + result.passages().size() + " 段原文");
            case "cross" -> cross(evalCase, result.passages());
            default -> firstCitation(evalCase, result.passages());
        };
    }

    private CaseResult firstCitation(EvalCaseRow evalCase, List<Passage> passages) {
        if (passages.isEmpty()) {
            return fail(evalCase, "没有召回任何原文");
        }
        Passage first = passages.get(0);
        if (!matchesDoc(first, evalCase.expectedDoc())) {
            return fail(evalCase, "首个引用是 " + first.filename() + "，期望 " + evalCase.expectedDoc());
        }
        if (evalCase.pageFrom() != null && evalCase.pageTo() != null
                && (first.pageFrom() > evalCase.pageTo() || first.pageTo() < evalCase.pageFrom())) {
            return fail(evalCase, "首个引用页码 " + first.pageFrom() + "-" + first.pageTo()
                    + "，期望 " + evalCase.pageFrom() + "-" + evalCase.pageTo());
        }
        return pass(evalCase, "首个引用 " + first.filename() + " 第 " + first.pageFrom() + " 页");
    }

    private CaseResult cross(EvalCaseRow evalCase, List<Passage> passages) {
        List<ExpectedSource> expected = new ArrayList<>();
        try {
            for (JsonNode source : mapper.readTree(evalCase.expectedSources())) {
                expected.add(new ExpectedSource(source.path("doc").asText(),
                        source.has("pageFrom") ? source.path("pageFrom").asInt() : null,
                        source.has("pageTo") ? source.path("pageTo").asInt() : null));
            }
        } catch (RuntimeException e) {
            return fail(evalCase, "expected_sources 不是合法 JSON");
        }
        Set<String> matched = new HashSet<>();
        for (ExpectedSource source : expected) {
            for (Passage passage : passages) {
                if (matches(source, passage)) {
                    matched.add(source.doc());
                    break;
                }
            }
        }
        if (matched.size() >= 2) {
            return pass(evalCase, "命中 " + matched.size() + " 家来源");
        }
        return fail(evalCase, "只命中 " + matched.size() + " 家来源，需要两家以上");
    }

    private static boolean matches(ExpectedSource source, Passage passage) {
        if (!matchesDoc(passage, source.doc())) {
            return false;
        }
        if (source.pageFrom() == null || source.pageTo() == null) {
            return true;
        }
        return passage.pageFrom() <= source.pageTo() && passage.pageTo() >= source.pageFrom();
    }

    private static boolean matchesDoc(Passage passage, String doc) {
        return doc != null && !doc.isBlank() && passage.filename().contains(doc);
    }

    private static CaseResult pass(EvalCaseRow evalCase, String detail) {
        return new CaseResult(evalCase.id(), evalCase.question(), evalCase.kind(), true, detail);
    }

    private static CaseResult fail(EvalCaseRow evalCase, String detail) {
        return new CaseResult(evalCase.id(), evalCase.question(), evalCase.kind(), false, detail);
    }
}
