package com.hobozoo.aiqq.entity.rag;

import java.util.List;

/**
 * pipeline_def.definition 的映射。
 */
public record PipelineConfig(List<String> nodes, Chunk chunk, Heading heading, Header header,
                             List<Template> templates) {

    public record Chunk(int parentTarget, int parentMin, int parentMax, int childSize, int childOverlap,
                        int maxTokens) {
    }

    public record Heading(double sizeRatio, double boldSizeRatio, double lineGapRatio, int maxLevel) {
    }

    public record Header(double zoneRatio, double yTolerance, int minRepeatPages, int maxLength) {
    }

    public record Template(String name, String category, List<String> pathKeywords, Integer minPages) {
    }
}
