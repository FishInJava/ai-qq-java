package com.hobozoo.aiqq.common.util;

/**
 * 把原文压成一行摘录。
 */
public final class Snippet {

    public static final int DEFAULT_CHARS = 40;

    private Snippet() {
    }

    public static String of(String text) {
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= DEFAULT_CHARS ? flat : flat.substring(0, DEFAULT_CHARS) + "…";
    }
}
