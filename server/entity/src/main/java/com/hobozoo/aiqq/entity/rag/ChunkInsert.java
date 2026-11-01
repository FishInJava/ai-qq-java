package com.hobozoo.aiqq.entity.rag;

/**
 * 写入 chunks 表的一组值。
 */
public record ChunkInsert(long documentId, long blockId, Long parentId, String level, String text, String textMd,
                          int pageFrom, int pageTo, String[] sectionPath, String sha256, int ord) {
}
