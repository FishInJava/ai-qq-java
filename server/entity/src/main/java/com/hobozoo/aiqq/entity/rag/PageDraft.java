package com.hobozoo.aiqq.entity.rag;

/**
 * 待写入 pages 的一页。
 */
public record PageDraft(int pageNo, double width, double height, int rotation, int charCount, boolean empty) {
}
