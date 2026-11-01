package com.hobozoo.aiqq.service.rag;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.hobozoo.aiqq.dao.SearchDao;
import com.hobozoo.aiqq.entity.rag.IndexEntry;

/**
 * 补写失败的 Lucene 索引，按退避重试。
 */
@Service
public class IndexRetryService {

    private static final Logger log = LoggerFactory.getLogger(IndexRetryService.class);
    private static final int BATCH = 50;

    private final SearchDao search;
    private final LuceneIndexService lucene;

    public IndexRetryService(SearchDao search, LuceneIndexService lucene) {
        this.search = search;
        this.lucene = lucene;
    }

    @Scheduled(fixedDelayString = "${rag.index-retry.interval-ms:60000}")
    public void retry() {
        for (SearchDao.RetryRow row : search.dueRetries(BATCH)) {
            IndexEntry chunk = search.findIndexChunk(row.chunkId());
            if (chunk == null) {
                search.deleteRetry(row.id());
                continue;
            }
            try {
                lucene.indexOne(chunk);
                search.deleteRetry(row.id());
            } catch (IOException e) {
                log.warn("索引重试失败 chunk={}", row.chunkId(), e);
                search.bumpRetry(row.id(), String.valueOf(e.getMessage()));
            }
        }
    }
}
