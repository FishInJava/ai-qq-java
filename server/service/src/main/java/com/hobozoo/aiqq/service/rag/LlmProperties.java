package com.hobozoo.aiqq.service.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 生成模型的连接参数。
 */
@ConfigurationProperties(prefix = "rag.llm")
public record LlmProperties(String baseUrl, String apiKey, String model, double temperature, int maxTokens,
                            int timeoutSeconds, String thinking) {
}
