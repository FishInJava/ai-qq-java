package com.hobozoo.aiqq.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hobozoo.aiqq.entity.rag.JobRow;
import com.hobozoo.aiqq.service.rag.RagLibraryService;

/**
 * 任务列表与详情。
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final RagLibraryService libraryService;

    public JobController(RagLibraryService libraryService) {
        this.libraryService = libraryService;
    }

    public record JobView(long id, String type, String sourcePath, Long documentId, String status, String stage,
                          int progress, int total, int done, String error, String createdAt, String updatedAt) {
        static JobView of(JobRow row) {
            return new JobView(row.id(), row.type(), row.sourcePath(), row.documentId(), row.status(), row.stage(),
                    row.progress(), row.total(), row.done(), row.error(), row.createdAt(), row.updatedAt());
        }
    }

    public record ListResponse(List<JobView> items) {
    }

    @GetMapping
    public ListResponse jobs(@RequestParam(defaultValue = "100") int limit) {
        List<JobView> items = libraryService.jobs(limit).stream().map(JobView::of).toList();
        return new ListResponse(items);
    }

    @GetMapping("/{id}")
    public JobView job(@PathVariable long id) {
        return JobView.of(libraryService.job(id));
    }
}
