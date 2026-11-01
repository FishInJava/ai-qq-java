package com.hobozoo.aiqq.entity.rag;

/**
 * blocks 表的一行。
 */
public record BlockRow(long id, int pageNo, String type, String text, int orderNo, String[] sectionPath,
                       Double fontSize, String fontName, double x0, double y0, double x1, double y1) {
}
