package com.hobozoo.aiqq.service.rag;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.hobozoo.aiqq.dao.IngestDao;
import com.hobozoo.aiqq.dao.LibraryDao;
import com.hobozoo.aiqq.dao.SearchDao;
import com.hobozoo.aiqq.entity.rag.DocumentRow;
import com.hobozoo.aiqq.entity.rag.EventRow;
import com.hobozoo.aiqq.entity.rag.JobRow;
import com.hobozoo.aiqq.entity.rag.PipelineSnapshot;
import com.hobozoo.aiqq.entity.rag.RunRow;
import com.hobozoo.aiqq.entity.rag.UploadResult;

/**
 * 文档库的读写入口，controller 只经过这一层。
 */
@Service
public class RagLibraryService {

    public record Box(int pageNo, double x0, double y0, double x1, double y1) {
    }

    public record ChunkBoxes(long documentId, List<Box> boxes) {
    }

    private final LibraryDao library;
    private final IngestDao ingest;
    private final SearchDao search;
    private final LuceneIndexService lucene;
    private final IngestWorker worker;
    private final RagProperties properties;

    public RagLibraryService(LibraryDao library, IngestDao ingest, SearchDao search, LuceneIndexService lucene,
                             IngestWorker worker, RagProperties properties) {
        this.library = library;
        this.ingest = ingest;
        this.search = search;
        this.lucene = lucene;
        this.worker = worker;
        this.properties = properties;
    }

    public ChunkBoxes chunkBoxes(long chunkId) {
        SearchDao.ChunkBoxes boxes = search.chunkBoxes(chunkId);
        return new ChunkBoxes(boxes.documentId(),
                boxes.boxes().stream().map(box -> new Box(box.pageNo(), box.x0(), box.y0(), box.x1(), box.y1()))
                        .toList());
    }

    public List<DocumentRow> documents(String status, int limit) {
        return library.listDocuments(status, Math.min(Math.max(limit, 1), 1000));
    }

    public DocumentRow document(long id) {
        return library.findDocument(id).orElseThrow(() -> new IllegalArgumentException("文档不存在：" + id));
    }

    public PipelineSnapshot pipeline(long id) {
        DocumentRow document = document(id);
        List<RunRow> runs = library.runs(id);
        Map<Long, List<EventRow>> events = new HashMap<>();
        for (RunRow run : runs) {
            events.put(run.id(), library.events(run.id()));
        }
        return new PipelineSnapshot(document, ingest.pages(id), runs, events, ingest.blocks(id));
    }

    public Path documentFile(long id) {
        Path path = Path.of(document(id).path());
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("文件不存在：" + path);
        }
        return path;
    }

    public List<JobRow> jobs(int limit) {
        return library.listJobs(Math.min(Math.max(limit, 1), 500));
    }

    public JobRow job(long id) {
        return library.findJob(id).orElseThrow(() -> new IllegalArgumentException("任务不存在：" + id));
    }

    public long scan(String path) {
        String directory = path == null || path.isBlank() ? properties.scanRoot() : path;
        if (!Files.isDirectory(Path.of(directory))) {
            throw new IllegalArgumentException("目录不存在：" + directory);
        }
        long jobId = library.insertJob("scan", directory, null, 0);
        worker.submit(jobId);
        return jobId;
    }

    public void delete(long documentId) {
        document(documentId);
        library.softDeleteDocument(documentId);
        try {
            lucene.delete(documentId);
        } catch (IOException e) {
            throw new IllegalStateException("删除索引失败：" + e.getMessage(), e);
        }
    }

    public long reingest(long documentId) {
        DocumentRow document = document(documentId);
        long jobId = library.insertJob("ingest", document.path(), documentId, 1);
        worker.submit(jobId);
        return jobId;
    }

    public UploadResult upload(String filename, InputStream content) throws IOException {
        Path storage = Path.of(properties.storageRoot());
        Files.createDirectories(storage);
        Path temporary = Files.createTempFile(storage, "upload-", ".pdf");
        try (content) {
            Files.copy(content, temporary, StandardCopyOption.REPLACE_EXISTING);
        }
        String sha256 = LibraryScanner.sha256(temporary);
        Optional<DocumentRow> existing = library.findActiveBySha(sha256);
        if (existing.isPresent()) {
            Files.deleteIfExists(temporary);
            return new UploadResult(-1, existing.get().id(), true);
        }
        Path target = storage.resolve(sha256 + ".pdf");
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        long documentId = library.insertDocument(target.toString(), sha256, filename, Files.size(target), "general");
        long jobId = library.insertJob("ingest", target.toString(), documentId, 1);
        worker.submit(jobId);
        return new UploadResult(jobId, documentId, false);
    }
}
