package com.hobozoo.aiqq.entity.rag;

/**
 * pages 表的一行。
 */
public record PageRow(int pageNo, double width, double height, int rotation, int charCount, boolean empty) {
}
