package com.hobozoo.aiqq.service.rag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.hobozoo.aiqq.dao.LibraryDao;
import com.hobozoo.aiqq.entity.rag.CitationRow;
import com.hobozoo.aiqq.entity.rag.MessageRow;
import com.hobozoo.aiqq.entity.rag.NumberCheckRow;
import com.hobozoo.aiqq.entity.rag.SessionMessage;
import com.hobozoo.aiqq.entity.rag.SessionRow;

/**
 * 会话列表与历史消息。
 */
@Service
public class SessionService {

    private static final int LIMIT = 50;

    private final LibraryDao library;

    public SessionService(LibraryDao library) {
        this.library = library;
    }

    public List<SessionRow> list() {
        return library.sessions(LIMIT);
    }

    public List<SessionMessage> messages(String sessionId) {
        Map<String, List<CitationRow>> citations = library.citations(sessionId).stream()
                .collect(Collectors.groupingBy(CitationRow::queryId, LinkedHashMap::new, Collectors.toList()));
        Map<String, NumberCheckRow> checks = library.numberChecks(sessionId).stream()
                .collect(Collectors.toMap(NumberCheckRow::queryId, Function.identity(), (left, right) -> left));
        return assemble(library.messages(sessionId), citations, checks);
    }

    static List<SessionMessage> assemble(List<MessageRow> rows, Map<String, List<CitationRow>> citations,
                                         Map<String, NumberCheckRow> checks) {
        return rows.stream().map(row -> {
            boolean assistant = "assistant".equals(row.role());
            return new SessionMessage(row.role(), row.content(), row.queryId(), row.createdAt(),
                    row.latencyMs(), row.firstTokenMs(), row.refused(),
                    assistant ? citations.getOrDefault(row.queryId(), List.of()) : List.of(),
                    assistant ? checks.get(row.queryId()) : null);
        }).toList();
    }
}
