package com.hobozoo.aiqq.entity.rag;

/**
 * 单条待写入倒排索引的切块。
 */
public record IndexEntry(long chunkId, long documentId, long parentId, String text, int pageFrom, int pageTo,
                         String sectionPath) {
}
