package com.hobozoo.aiqq.service.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.hobozoo.aiqq.entity.rag.CitationRow;
import com.hobozoo.aiqq.entity.rag.MessageRow;
import com.hobozoo.aiqq.entity.rag.NumberCheckRow;
import com.hobozoo.aiqq.entity.rag.SessionMessage;

class SessionServiceTest {

    @Test
    void attachesCitationsAndChecksToAssistantOnly() {
        MessageRow user = new MessageRow("user", "问题", "q_1", "t0", null, null, false);
        MessageRow assistant = new MessageRow("assistant", "答案", "q_1", "t1", 900, 300, false);
        CitationRow citation = new CitationRow("q_1", 1, 10L, 40L, "a.pdf", 3, 4, "1. 前言", "摘录", false);
        NumberCheckRow check = new NumberCheckRow("q_1", 2, List.of("1.5"));

        List<SessionMessage> messages = SessionService.assemble(
                List.of(user, assistant),
                Map.of("q_1", List.of(citation)),
                Map.of("q_1", check));

        assertEquals(List.of(), messages.get(0).citations());
        assertNull(messages.get(0).numberCheck());
        assertEquals(1, messages.get(1).citations().size());
        assertEquals("摘录", messages.get(1).citations().get(0).snippet());
        assertEquals(900, messages.get(1).latencyMs());
        assertEquals(List.of("1.5"), messages.get(1).numberCheck().missing());
    }

    @Test
    void leavesMessagesWithoutCitationsEmpty() {
        MessageRow assistant = new MessageRow("assistant", "答案", "q_2", "t1", 800, 200, true);

        List<SessionMessage> messages = SessionService.assemble(
                List.of(assistant), Map.of(), Map.of());

        assertEquals(List.of(), messages.get(0).citations());
        assertNull(messages.get(0).numberCheck());
    }
}
