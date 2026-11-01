package com.hobozoo.aiqq.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hobozoo.aiqq.entity.rag.EvalCaseRow;
import com.hobozoo.aiqq.service.rag.EvalService;

/**
 * 验收集与跑分。
 */
@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvalService eval;

    public EvalController(EvalService eval) {
        this.eval = eval;
    }

    public record CaseView(long id, String question, String kind, String expectedDoc, Integer pageFrom,
                           Integer pageTo, String[] keywords) {
        static CaseView of(EvalCaseRow row) {
            return new CaseView(row.id(), row.question(), row.kind(), row.expectedDoc(), row.pageFrom(),
                    row.pageTo(), row.keywords());
        }
    }

    public record CaseList(List<CaseView> items) {
    }

    @GetMapping("/cases")
    public CaseList cases() {
        return new CaseList(eval.cases().stream().map(CaseView::of).toList());
    }

    public record NewCaseResponse(long id) {
    }

    @PostMapping("/cases")
    public NewCaseResponse add(@RequestBody EvalService.NewCase request) {
        return new NewCaseResponse(eval.add(request));
    }

    @PostMapping("/run")
    public EvalService.RunResult run() {
        return eval.run();
    }
}
