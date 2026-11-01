package com.hobozoo.aiqq.service.rag;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.hobozoo.aiqq.service.rag.PipelineConfigService;
import com.hobozoo.aiqq.dao.LibraryDao;
import com.hobozoo.aiqq.entity.rag.DocumentRow;
import com.hobozoo.aiqq.entity.rag.JobRow;

/**
 * 扫描目录、按 sha256 去重、给每份 PDF 建导入任务。
 */
@Component
public class LibraryScanner {

    private static final Logger log = LoggerFactory.getLogger(LibraryScanner.class);

    private final LibraryDao library;
    private final PipelineConfigService configs;

    public LibraryScanner(LibraryDao library, PipelineConfigService configs) {
        this.library = library;
        this.configs = configs;
    }

    public List<Long> scan(JobRow job) throws IOException {
        Path root = Path.of(job.sourcePath());
        List<Path> files;
        try (Stream<Path> stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .sorted()
                    .toList();
        }

        List<Long> jobIds = new ArrayList<>();
        int done = 0;
        for (Path file : files) {
            done++;
            String sha256 = sha256(file);
            Optional<DocumentRow> existing = library.findActiveBySha(sha256);
            if (existing.isPresent()) {
                library.touchJobProgress(job.id(), "skip", percent(done, files.size()));
                continue;
            }
            long documentId = library.insertDocument(file.toString(), sha256, file.getFileName().toString(),
                    Files.size(file), category(file));
            long ingestJobId = library.insertJob("ingest", file.toString(), documentId, 1);
            jobIds.add(ingestJobId);
            library.touchJobProgress(job.id(), "enqueue", percent(done, files.size()));
            log.info("入队 document={} file={}", documentId, file.getFileName());
        }
        return jobIds;
    }

    public String category(Path file) {
        String path = file.toString();
        var config = configs.load("rag", configs.currentVersion("rag"));
        for (var template : config.templates()) {
            if (template.pathKeywords() == null) {
                continue;
            }
            for (String keyword : template.pathKeywords()) {
                if (!keyword.isBlank() && path.contains(keyword)) {
                    return template.category();
                }
            }
        }
        return "general";
    }

    public static String sha256(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = input.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int percent(int done, int total) {
        return total == 0 ? 100 : (int) Math.round(done * 100.0 / total);
    }
}
