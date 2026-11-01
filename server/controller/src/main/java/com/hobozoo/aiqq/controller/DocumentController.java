package com.hobozoo.aiqq.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hobozoo.aiqq.entity.rag.BlockRow;
import com.hobozoo.aiqq.entity.rag.DocumentRow;
import com.hobozoo.aiqq.entity.rag.EventRow;
import com.hobozoo.aiqq.entity.rag.PageRow;
import com.hobozoo.aiqq.entity.rag.PipelineSnapshot;
import com.hobozoo.aiqq.entity.rag.RunRow;
import com.hobozoo.aiqq.service.rag.RagLibraryService;

/**
 * 文档列表、详情、管线视图与 PDF 流。
 */
@RestController
@RequestMapping("/api")
public class DocumentController {

    private static final MediaType PDF = MediaType.parseMediaType("application/pdf");
    private static final long MAX_RANGE = 8L * 1024 * 1024;

    private final RagLibraryService libraryService;

    public DocumentController(RagLibraryService libraryService) {
        this.libraryService = libraryService;
    }

    public record DocumentView(long id, String filename, String title, String category, int pages, long bytes,
                               String language, String status, String structureSource, Integer pipelineVersion,
                               String error, String createdAt) {
        static DocumentView of(DocumentRow row) {
            return new DocumentView(row.id(), row.filename(), row.title(), row.category(), row.pages(), row.bytes(),
                    row.language(), row.status(), row.structureSource(), row.pipelineVersion(), row.error(),
                    row.createdAt());
        }
    }

    public record PageView(int pageNo, double width, double height, int rotation, int charCount, boolean isEmpty) {
        static PageView of(PageRow row) {
            return new PageView(row.pageNo(), row.width(), row.height(), row.rotation(), row.charCount(), row.empty());
        }
    }

    public record BlockView(long id, int pageNo, String blockType, String text, int orderNo, String[] sectionPath,
                            int charCount) {
        static BlockView of(BlockRow row) {
            return new BlockView(row.id(), row.pageNo(), row.type(), row.text(), row.orderNo(), row.sectionPath(),
                    row.text().length());
        }
    }

    public record EventView(String node, String status, Integer durationMs, String detail, String message) {
        static EventView of(EventRow row) {
            return new EventView(row.node(), row.status(), row.durationMs(), row.detail(), row.message());
        }
    }

    public record RunView(long id, int attempt, String status, String startedAt, String finishedAt, String stats,
                          List<EventView> events) {
    }

    public record PipelineView(DocumentView document, List<PageView> pages, List<RunView> runs,
                               List<BlockView> blocks) {
    }

    public record ListResponse<T>(List<T> items) {
    }

    @GetMapping("/documents")
    public ListResponse<DocumentView> documents(@RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "200") int limit) {
        List<DocumentView> items = libraryService.documents(status, limit).stream()
                .map(DocumentView::of)
                .toList();
        return new ListResponse<>(items);
    }

    @GetMapping("/documents/{id}")
    public DocumentView document(@PathVariable long id) {
        return DocumentView.of(libraryService.document(id));
    }

    @GetMapping("/documents/{id}/pipeline")
    public PipelineView pipeline(@PathVariable long id) {
        PipelineSnapshot snapshot = libraryService.pipeline(id);
        List<RunView> runs = new ArrayList<>();
        for (RunRow run : snapshot.runs()) {
            List<EventView> events = snapshot.events().getOrDefault(run.id(), List.of()).stream()
                    .map(EventView::of)
                    .toList();
            runs.add(new RunView(run.id(), run.attempt(), run.status(), run.startedAt(), run.finishedAt(),
                    run.stats(), events));
        }
        return new PipelineView(DocumentView.of(snapshot.document()),
                snapshot.pages().stream().map(PageView::of).toList(),
                runs,
                snapshot.blocks().stream().map(BlockView::of).toList());
    }

    public record ChunkBoxView(int pageNo, double x0, double y0, double x1, double y1) {
        static ChunkBoxView of(RagLibraryService.Box box) {
            return new ChunkBoxView(box.pageNo(), box.x0(), box.y0(), box.x1(), box.y1());
        }
    }

    public record ChunkBoxesView(long documentId, List<ChunkBoxView> boxes) {
    }

    @GetMapping("/chunks/{id}/boxes")
    public ChunkBoxesView chunkBoxes(@PathVariable long id) {
        RagLibraryService.ChunkBoxes boxes = libraryService.chunkBoxes(id);
        return new ChunkBoxesView(boxes.documentId(), boxes.boxes().stream().map(ChunkBoxView::of).toList());
    }

    public record ReingestResponse(long jobId) {
    }

    @DeleteMapping("/documents/{id}")
    public Map<String, Boolean> delete(@PathVariable long id) {
        libraryService.delete(id);
        return Map.of("ok", true);
    }

    @PostMapping("/documents/{id}/reingest")
    public ReingestResponse reingest(@PathVariable long id) {
        return new ReingestResponse(libraryService.reingest(id));
    }

    @GetMapping("/documents/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable long id,
                                         @RequestHeader(value = HttpHeaders.RANGE, required = false) String range)
            throws IOException {
        Path path = libraryService.documentFile(id);
        long length = Files.size(path);
        if (range == null || range.isBlank()) {
            return ResponseEntity.ok()
                    .contentType(PDF)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(length)
                    .body(new FileSystemResource(path));
        }
        long start = 0;
        long end = length - 1;
        String[] parts = range.replace("bytes=", "").split("-", 2);
        if (!parts[0].isBlank()) {
            start = Long.parseLong(parts[0]);
        }
        if (parts.length > 1 && !parts[1].isBlank()) {
            end = Long.parseLong(parts[1]);
        }
        end = Math.min(Math.min(end, start + MAX_RANGE - 1), length - 1);
        if (start > end || start >= length) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
        }
        byte[] data = new byte[(int) (end - start + 1)];
        try (var channel = Files.newByteChannel(path)) {
            channel.position(start);
            channel.read(java.nio.ByteBuffer.wrap(data));
        }
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(PDF)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + length)
                .contentLength(data.length)
                .body(new ByteArrayResource(data));
    }
}
