package com.hobozoo.aiqq.service.rag;

import java.util.Set;

import org.springframework.stereotype.Service;

import com.hobozoo.aiqq.dao.LibraryDao;

/**
 * 答案标注，供后续生成回归用例。
 */
@Service
public class FeedbackService {

    private static final Set<String> VERDICTS = Set.of("wrong", "incomplete", "ok");

    private final LibraryDao library;

    public FeedbackService(LibraryDao library) {
        this.library = library;
    }

    public void add(String queryId, String verdict, String note) {
        if (queryId == null || queryId.isBlank()) {
            throw new IllegalArgumentException("缺少 queryId");
        }
        if (!VERDICTS.contains(verdict)) {
            throw new IllegalArgumentException("verdict 只能是 wrong、incomplete 或 ok");
        }
        library.findQuery(queryId).orElseThrow(() -> new IllegalArgumentException("查询不存在：" + queryId));
        library.insertFeedback(queryId, verdict, note);
    }
}
