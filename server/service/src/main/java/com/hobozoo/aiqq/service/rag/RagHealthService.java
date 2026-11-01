package com.hobozoo.aiqq.service.rag;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.hobozoo.aiqq.dao.LibraryDao;
import com.hobozoo.aiqq.entity.rag.HealthRow;

/**
 * 存活与数据库连通。
 */
@Service
public class RagHealthService {

    private final LibraryDao library;

    public RagHealthService(LibraryDao library) {
        this.library = library;
    }

    public record HealthStatus(boolean db, String postgres, String pgvector, int tables, long latencyMs) {
    }

    public HealthStatus health() {
        long startedAt = System.nanoTime();
        try {
            HealthRow row = library.health();
            return new HealthStatus(true, row.postgres(), row.pgvector(), row.tables(), elapsedMs(startedAt));
        } catch (DataAccessException e) {
            return new HealthStatus(false, null, null, 0, elapsedMs(startedAt));
        }
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
