package com.hobozoo.aiqq.service.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CitationFilterTest {

    @Test
    void keepsValidMarkers() {
        CitationFilter filter = new CitationFilter(8);

        assertEquals("答案[1]", filter.feed("答案[1]"));
        assertEquals(0, filter.invalid());
    }

    @Test
    void dropsOutOfRangeMarkers() {
        CitationFilter filter = new CitationFilter(8);

        assertEquals("答案", filter.feed("答案[9]"));
        assertEquals(1, filter.invalid());
    }

    @Test
    void waitsForSplitMarkers() {
        CitationFilter filter = new CitationFilter(8);

        assertEquals("答案", filter.feed("答案["));
        assertEquals("[2] 继续", filter.feed("2] 继续"));
        assertEquals(0, filter.invalid());
    }

    @Test
    void keepsNonNumericBrackets() {
        CitationFilter filter = new CitationFilter(8);

        assertEquals("见[图]", filter.feed("见[图]"));
    }
}
