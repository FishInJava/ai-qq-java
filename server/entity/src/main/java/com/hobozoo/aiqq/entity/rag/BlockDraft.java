package com.hobozoo.aiqq.entity.rag;

/**
 * 待写入 blocks 的一块。
 */
public record BlockDraft(int pageNo, String type, double x0, double y0, double x1, double y1, String text,
                         int orderNo, String[] sectionPath, Double fontSize, String fontName) {
}
