package com.hobozoo.aiqq.entity.rag;

/**
 * 待写入 chunks 的子块，parentIndex 指向同批父块。
 */
public record ChildDraft(int parentIndex, String text, int pageFrom, int pageTo, String sha256, int ord) {
}
