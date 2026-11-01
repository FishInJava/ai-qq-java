package com.hobozoo.aiqq.service.rag;

/**
 * 文本向量化，阶段 5 换成真模型。
 */
public interface EmbeddingModel {

    String name();

    int dim();

    float[] embed(String text);
}
