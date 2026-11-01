package com.hobozoo.aiqq.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hobozoo.aiqq.service.rag.FeedbackService;

/**
 * 答案标注。
 */
@RestController
@RequestMapping("/api")
public class FeedbackController {

    private final FeedbackService feedback;

    public FeedbackController(FeedbackService feedback) {
        this.feedback = feedback;
    }

    public record FeedbackRequest(String queryId, String verdict, String note) {
    }

    @PostMapping("/feedback")
    public Map<String, Boolean> add(@RequestBody FeedbackRequest request) {
        feedback.add(request.queryId(), request.verdict(), request.note());
        return Map.of("ok", true);
    }
}
