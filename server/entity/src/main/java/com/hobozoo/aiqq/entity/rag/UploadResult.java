package com.hobozoo.aiqq.entity.rag;

/**
 * 上传完成后的任务与文档。
 */
public record UploadResult(long jobId, long documentId, boolean duplicated) {
}
