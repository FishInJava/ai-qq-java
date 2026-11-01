package com.hobozoo.aiqq.service.rag;

import org.springframework.stereotype.Component;

/**
 * 阶段 0-4 的确定性哈希桩，只验证链路不验证语义。
 */
@Component
public class HashEmbeddingModel implements EmbeddingModel {

    public static final String NAME = "hash-stub";
    public static final int DIM = 512;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int dim() {
        return DIM;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIM];
        if (text == null || text.isBlank()) {
            return vector;
        }
        for (int index = 0; index < text.length(); index++) {
            int hash = text.charAt(index) * 31 + index;
            hash = mix(hash);
            int bucket = Math.floorMod(hash, DIM);
            vector[bucket] += (hash & 1) == 0 ? 1f : -1f;
        }
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm > 0) {
            float length = (float) Math.sqrt(norm);
            for (int index = 0; index < vector.length; index++) {
                vector[index] /= length;
            }
        }
        return vector;
    }

    private static int mix(int value) {
        int mixed = value * 0x9E3779B9;
        mixed ^= mixed >>> 16;
        return mixed;
    }
}
