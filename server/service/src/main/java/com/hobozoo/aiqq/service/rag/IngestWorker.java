package com.hobozoo.aiqq.service.rag;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.hobozoo.aiqq.dao.LibraryDao;
import com.hobozoo.aiqq.entity.rag.JobRow;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * 单线程跑任务，重启后把未完成任务重新排队。
 */
@Component
public class IngestWorker {

    private static final Logger log = LoggerFactory.getLogger(IngestWorker.class);

    private final LibraryDao library;
    private final IngestService ingest;
    private final LibraryScanner scanner;
    private final BlockingQueue<Long> queue = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "rag-ingest");
        thread.setDaemon(true);
        return thread;
    });

    public IngestWorker(LibraryDao library, IngestService ingest, LibraryScanner scanner) {
        this.library = library;
        this.ingest = ingest;
        this.scanner = scanner;
    }

    @PostConstruct
    void start() {
        library.requeueRunningJobs();
        for (Long jobId : library.unfinishedIngestJobs()) {
            queue.offer(jobId);
        }
        executor.submit(this::loop);
        log.info("导入线程启动，待处理 {} 个任务", queue.size());
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    public void submit(long jobId) {
        queue.offer(jobId);
    }

    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Long jobId = queue.poll(1, TimeUnit.SECONDS);
                if (jobId != null) {
                    process(jobId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("任务循环异常", e);
            }
        }
    }

    private void process(long jobId) {
        JobRow job = library.findJob(jobId).orElse(null);
        if (job == null || "done".equals(job.status())) {
            return;
        }
        library.updateJob(jobId, "running", "start", 0, 0, null);
        try {
            if ("scan".equals(job.type())) {
                List<Long> childJobs = scanner.scan(job);
                library.updateJob(jobId, "done", "done", 100, job.total() == 0 ? childJobs.size() : job.total(),
                        null);
                childJobs.forEach(this::submit);
            } else {
                if (job.documentId() == null) {
                    throw new IllegalStateException("导入任务缺少文档");
                }
                ingest.ingest(job.documentId(), jobId);
                library.updateJob(jobId, "done", "done", 100, 1, null);
            }
        } catch (Exception e) {
            log.warn("任务 {} 失败", jobId, e);
            library.updateJob(jobId, "failed", job.stage(), job.progress(), job.done(), e.getMessage());
        }
    }
}
