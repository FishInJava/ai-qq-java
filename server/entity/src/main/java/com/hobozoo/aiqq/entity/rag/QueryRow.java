package com.hobozoo.aiqq.entity.rag;

/**
 * queries 表的一行。
 */
public record QueryRow(String id, String sessionId, String question, String answer, String model, Integer tokensIn,
                       Integer tokensOut, Integer latencyMs, Integer firstTokenMs, String status, String error,
                       String createdAt) {
}
