package com.hobozoo.aiqq.entity.rag;

/**
 * 一路召回的子块，score 是重排前的原始分。
 */
public record ChunkHit(long chunkId, long parentId, double score) {
}
