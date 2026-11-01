package com.hobozoo.aiqq.service.rag;

import java.util.List;

import org.springframework.stereotype.Service;

import com.hobozoo.aiqq.dao.LibraryDao;
import com.hobozoo.aiqq.entity.rag.MessageRow;
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

    public List<MessageRow> messages(String sessionId) {
        return library.messages(sessionId);
    }
}
