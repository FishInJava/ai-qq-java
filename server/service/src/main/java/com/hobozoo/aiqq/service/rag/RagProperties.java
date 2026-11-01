package com.hobozoo.aiqq.service.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地运行路径。
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(String scanRoot, String storageRoot, String indexRoot) {
}
