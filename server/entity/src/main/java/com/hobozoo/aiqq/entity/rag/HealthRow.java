package com.hobozoo.aiqq.entity.rag;

/**
 * 健康检查读到的库版本。
 */
public record HealthRow(String postgres, String pgvector, int tables) {
}
