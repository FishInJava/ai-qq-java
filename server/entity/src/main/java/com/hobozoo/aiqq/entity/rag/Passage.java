package com.hobozoo.aiqq.entity.rag;

/**
 * 回取的父块，带引用所需的文件与页码。
 */
public record Passage(long chunkId, long parentId, long documentId, String filename, String text, int pageFrom,
                      int pageTo, String[] sectionPath, double score) {
}
