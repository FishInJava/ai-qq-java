package com.hobozoo.aiqq.service.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hobozoo.aiqq.entity.rag.BlockDraft;
import com.hobozoo.aiqq.entity.rag.PipelineConfig;

class ChunkerTest {

    private static final PipelineConfig.Chunk CONFIG = new PipelineConfig.Chunk(1200, 600, 1600, 300, 60, 512);

    @Test
    void 章节变化时另起父块() {
        List<BlockDraft> blocks = List.of(
                block(1, "heading", "1. 前言", 0, "1. 前言", 12),
                block(1, "text", "甲".repeat(700), 1, "1. 前言", 9),
                block(2, "text", "乙".repeat(700), 0, "2. 模型回顾", 9));

        Chunker.Result result = new Chunker().chunk(blocks, CONFIG);

        assertThat(result.parents()).hasSize(2);
        assertThat(result.parents().get(0).text()).contains("1. 前言").contains("甲");
        assertThat(result.parents().get(1).text()).contains("乙");
        assertThat(result.parents().get(1).sectionPath()).containsExactly("2. 模型回顾");
    }

    @Test
    void 子块按窗口切且带重叠() {
        String text = "甲".repeat(700);
        List<BlockDraft> blocks = List.of(block(1, "text", text, 0, "1. 前言", 9));

        Chunker.Result result = new Chunker().chunk(blocks, CONFIG);

        assertThat(result.parents()).hasSize(1);
        assertThat(result.children().size()).isGreaterThan(1);
        assertThat(result.children()).allSatisfy(child -> {
            assertThat(child.parentIndex()).isZero();
            assertThat(child.text().length()).isLessThanOrEqualTo(300);
            assertThat(child.sha256()).hasSize(64);
        });
        String first = result.children().get(0).text();
        String second = result.children().get(1).text();
        assertThat(first).endsWith(second.substring(0, 60));
    }

    @Test
    void 超长块按目标长度切开() {
        List<BlockDraft> blocks = List.of(block(1, "text", "丙".repeat(4000), 0, "", 9));

        Chunker.Result result = new Chunker().chunk(blocks, CONFIG);

        assertThat(result.parents().size()).isEqualTo(4);
        assertThat(result.parents()).allSatisfy(parent -> assertThat(parent.text().length()).isLessThanOrEqualTo(1200));
    }

    @Test
    void 表格块单独成父块且只带一个子块() {
        List<BlockDraft> blocks = List.of(
                block(1, "text", "丁".repeat(200), 0, "1. 前言", 9),
                block(1, "table", "列A | 列B\n1 | 2", 1, "1. 前言", 9));

        Chunker.Result result = new Chunker().chunk(blocks, CONFIG);

        assertThat(result.parents()).hasSize(2);
        assertThat(result.parents().get(1).table()).isTrue();
        assertThat(result.children()).filteredOn(child -> child.parentIndex() == 1).hasSize(1);
    }

    private static BlockDraft block(int pageNo, String type, String text, int orderNo, String section,
                                    double fontSize) {
        return new BlockDraft(pageNo, type, 0, 0, 100, 20, text, orderNo,
                section.isEmpty() ? new String[0] : new String[]{section}, fontSize, "SimSun");
    }
}
