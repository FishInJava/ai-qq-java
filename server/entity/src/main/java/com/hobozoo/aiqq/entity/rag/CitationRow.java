package com.hobozoo.aiqq.entity.rag;

/**
 * 一次问答落库的引用。
 */
public record CitationRow(String queryId, int index, long chunkId, long documentId, String doc,
                          int pageFrom, int pageTo, String section, String snippet, boolean candidate) {
}
