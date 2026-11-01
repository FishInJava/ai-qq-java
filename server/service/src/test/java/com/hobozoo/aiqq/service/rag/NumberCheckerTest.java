package com.hobozoo.aiqq.service.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class NumberCheckerTest {

    @Test
    void marksNumbersMissingFromSources() {
        NumberChecker.Result result = NumberChecker.check(
                "年化收益 12.5%，回撤 0.35%，规模 1.2 亿元。",
                List.of("年化收益为 12.5%，最大回撤 3.5%。"));

        assertEquals(3, result.total());
        assertEquals(List.of("0.35%", "1.2 亿元"), result.missing());
    }

    @Test
    void treatsRatioAndPercentAsSame() {
        NumberChecker.Result result = NumberChecker.check("换手率 0.25", List.of("换手率为 25%"));

        assertEquals(1, result.total());
        assertTrue(result.missing().isEmpty());
    }

    @Test
    void convertsChineseNumerals() {
        NumberChecker.Result result = NumberChecker.check(
                "占比百分之二十五，杠杆两倍。",
                List.of("占比 25%，杠杆 2 倍。"));

        assertEquals(2, result.total());
        assertTrue(result.missing().isEmpty());
    }

    @Test
    void convertsWanAndYi() {
        NumberChecker.Result result = NumberChecker.check(
                "成交额 1.2 亿元。",
                List.of("成交额 120000000 元。"));

        assertTrue(result.missing().isEmpty());
    }

    @Test
    void ignoresPageReferences() {
        NumberChecker.Result result = NumberChecker.check(
                "见第 12 页。",
                List.of("无相关数字。"));

        assertEquals(0, result.total());
    }

    @Test
    void handlesFullWidthDigits() {
        NumberChecker.Result result = NumberChecker.check("占比 ２５％。", List.of("占比 25%。"));

        assertTrue(result.missing().isEmpty());
    }
}
