package com.hobozoo.aiqq.entity.rag;

/**
 * 会话里的一条消息，用户与助手分开。
 */
public record MessageRow(String role, String content, String queryId, String createdAt) {
}
