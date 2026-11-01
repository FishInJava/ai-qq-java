package com.hobozoo.aiqq.entity.rag;

/**
 * 会话里的一条原始消息。
 */
public record MessageRow(String role, String content, String queryId, String createdAt,
                         Integer latencyMs, Integer firstTokenMs, boolean refused) {
}
