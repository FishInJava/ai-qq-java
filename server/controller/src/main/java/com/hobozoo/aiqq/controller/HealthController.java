package com.hobozoo.aiqq.controller;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hobozoo.aiqq.service.rag.RagHealthService;

/**
 * 存活与数据库连通。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final RagHealthService healthService;

    public HealthController(RagHealthService healthService) {
        this.healthService = healthService;
    }

    public record Health(String status, boolean db, String postgres, String pgvector, int tables,
                         long latencyMs, String time) {
    }

    @GetMapping("/health")
    public ResponseEntity<Health> health() {
        RagHealthService.HealthStatus status = healthService.health();
        Health body = new Health(status.db() ? "ok" : "degraded", status.db(), status.postgres(),
                status.pgvector(), status.tables(), status.latencyMs(), Instant.now().toString());
        return status.db() ? ResponseEntity.ok(body)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
