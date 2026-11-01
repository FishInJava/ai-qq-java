package com.hobozoo.aiqq.entity.rag;

/**
 * documents 表的一行。
 */
public record DocumentRow(long id, String path, String sha256, String filename, String title, String category,
                          int pages, long bytes, String language, boolean encrypted, String structureSource,
                          Integer pipelineVersion, String status, String error, String createdAt) {
}
