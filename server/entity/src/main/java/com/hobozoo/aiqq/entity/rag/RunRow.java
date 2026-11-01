package com.hobozoo.aiqq.entity.rag;

/**
 * pipeline_runs 表的一行。
 */
public record RunRow(long id, int attempt, String status, String startedAt, String finishedAt, String stats) {
}
