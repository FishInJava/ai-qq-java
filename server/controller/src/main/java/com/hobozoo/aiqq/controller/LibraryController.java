package com.hobozoo.aiqq.controller;

import java.io.IOException;
import java.util.Optional;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.hobozoo.aiqq.entity.rag.UploadResult;
import com.hobozoo.aiqq.service.rag.RagLibraryService;

/**
 * 扫描目录与上传。
 */
@RestController
@RequestMapping("/api/library")
public class LibraryController {

    private final RagLibraryService libraryService;

    public LibraryController(RagLibraryService libraryService) {
        this.libraryService = libraryService;
    }

    public record ScanRequest(String path) {
    }

    public record ScanResponse(long jobId) {
    }

    @PostMapping("/scan")
    public ScanResponse scan(@RequestBody(required = false) ScanRequest request) {
        long jobId = libraryService.scan(request == null ? null : request.path());
        return new ScanResponse(jobId);
    }

    @PostMapping("/upload")
    public UploadResult upload(@RequestParam("file") MultipartFile file) throws IOException {
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("upload.pdf");
        if (!filename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("只接受 PDF");
        }
        return libraryService.upload(filename, file.getInputStream());
    }
}
