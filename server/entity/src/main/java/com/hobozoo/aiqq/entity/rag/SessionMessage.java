package com.hobozoo.aiqq.entity.rag;

import java.util.List;

/**
 * 会话里的一条消息。
 */
public record SessionMessage(String role, String content, String queryId, String createdAt,
                             Integer latencyMs, Integer firstTokenMs, boolean refused,
                             List<CitationRow> citations, NumberCheckRow numberCheck) {
}
