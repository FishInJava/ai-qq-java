package com.hobozoo.aiqq.entity.rag;

import java.util.List;

/**
 * 回答的数字自检结果。
 */
public record NumberCheckRow(String queryId, int total, List<String> missing) {
}
