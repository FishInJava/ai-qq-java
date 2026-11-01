package com.hobozoo.aiqq.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hobozoo.aiqq.entity.rag.CitationRow;
import com.hobozoo.aiqq.entity.rag.NumberCheckRow;
import com.hobozoo.aiqq.entity.rag.SessionMessage;
import com.hobozoo.aiqq.entity.rag.SessionRow;
import com.hobozoo.aiqq.service.rag.SessionService;

/**
 * 会话列表与历史消息。
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessions;

    public SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    public record SessionView(String id, String title, String createdAt, String updatedAt) {
        static SessionView of(SessionRow row) {
            return new SessionView(row.id(), row.title(), row.createdAt(), row.updatedAt());
        }
    }

    public record CitationView(int index, String doc, long documentId, int pageFrom, int pageTo,
                               String section, long chunkId, String snippet, boolean candidate) {
        static CitationView of(CitationRow row) {
            return new CitationView(row.index(), row.doc(), row.documentId(), row.pageFrom(), row.pageTo(),
                    row.section(), row.chunkId(), row.snippet(), row.candidate());
        }
    }

    public record NumberCheckView(int total, List<String> missing) {
        static NumberCheckView of(NumberCheckRow row) {
            return new NumberCheckView(row.total(), row.missing());
        }
    }

    public record MessageView(String role, String content, String queryId, String createdAt,
                              Integer latencyMs, Integer firstTokenMs, boolean refused,
                              List<CitationView> citations, NumberCheckView numberCheck) {
        static MessageView of(SessionMessage row) {
            return new MessageView(row.role(), row.content(), row.queryId(), row.createdAt(),
                    row.latencyMs(), row.firstTokenMs(), row.refused(),
                    row.citations().stream().map(CitationView::of).toList(),
                    row.numberCheck() == null ? null : NumberCheckView.of(row.numberCheck()));
        }
    }

    public record SessionList(List<SessionView> items) {
    }

    public record MessageList(List<MessageView> items) {
    }

    @GetMapping
    public SessionList list() {
        return new SessionList(sessions.list().stream().map(SessionView::of).toList());
    }

    @GetMapping("/{id}/messages")
    public MessageList messages(@PathVariable String id) {
        return new MessageList(sessions.messages(id).stream().map(MessageView::of).toList());
    }
}
