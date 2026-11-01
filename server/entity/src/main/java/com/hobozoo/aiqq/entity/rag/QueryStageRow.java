package com.hobozoo.aiqq.entity.rag;

/**
 * query_stages 表的一行。
 */
public record QueryStageRow(String stage, Integer durationMs, String payload, String recordedAt) {
}
