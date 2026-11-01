package com.hobozoo.aiqq.entity.rag;

/**
 * jobs 表的一行。
 */
public record JobRow(long id, String type, String sourcePath, Long documentId, String status, String stage,
                     int progress, int total, int done, String error, String createdAt, String updatedAt) {
}
