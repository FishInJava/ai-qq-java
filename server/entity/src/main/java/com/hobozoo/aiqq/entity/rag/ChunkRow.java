package com.hobozoo.aiqq.entity.rag;

/**
 * chunks 表的一行。
 */
public record ChunkRow(long id, String level, long parentId, String text, int pageFrom, int pageTo,
                       String sectionPath, String sha256) {
}
