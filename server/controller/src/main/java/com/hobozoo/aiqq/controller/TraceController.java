package com.hobozoo.aiqq.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hobozoo.aiqq.entity.rag.QueryRow;
import com.hobozoo.aiqq.entity.rag.QueryStageRow;
import com.hobozoo.aiqq.service.rag.QueryService;

/**
 * 链路详情。
 */
@RestController
@RequestMapping("/api/trace")
public class TraceController {

    private final QueryService queries;

    public TraceController(QueryService queries) {
        this.queries = queries;
    }

    public record QueryView(String id, String sessionId, String question, String status, Integer latencyMs,
                            String error, String createdAt) {
        static QueryView of(QueryRow row) {
            return new QueryView(row.id(), row.sessionId(), row.question(), row.status(), row.latencyMs(),
                    row.error(), row.createdAt());
        }
    }

    public record StageView(String stage, Integer durationMs, String payload) {
        static StageView of(QueryStageRow row) {
            return new StageView(row.stage(), row.durationMs(), row.payload());
        }
    }

    public record TraceView(QueryView query, List<StageView> stages) {
    }

    @GetMapping("/{queryId}")
    public TraceView trace(@PathVariable String queryId) {
        QueryService.TraceView view = queries.trace(queryId);
        return new TraceView(QueryView.of(view.query()), view.stages().stream().map(StageView::of).toList());
    }
}
