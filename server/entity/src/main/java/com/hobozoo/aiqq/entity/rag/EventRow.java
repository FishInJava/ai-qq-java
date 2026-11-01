package com.hobozoo.aiqq.entity.rag;

/**
 * pipeline_events 表的一行。
 */
public record EventRow(String node, String status, Integer durationMs, String detail, String message) {
}
