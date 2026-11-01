package com.hobozoo.aiqq.entity.rag;

import java.util.List;

/**
 * 待写入 chunks 的父块。
 */
public record ParentDraft(String text, int pageFrom, int pageTo, String[] sectionPath, String sha256,
                          List<Integer> blockIndexes, boolean table) {
}
