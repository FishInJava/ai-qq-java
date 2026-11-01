package com.hobozoo.aiqq.service.rag;

import java.util.List;

import org.springframework.stereotype.Service;

import com.hobozoo.aiqq.dao.LibraryDao;
import com.hobozoo.aiqq.entity.rag.QueryRow;
import com.hobozoo.aiqq.entity.rag.QueryStageRow;

/**
 * 链路详情。
 */
@Service
public class QueryService {

    private final LibraryDao library;

    public QueryService(LibraryDao library) {
        this.library = library;
    }

    public record TraceView(QueryRow query, List<QueryStageRow> stages) {
    }

    public TraceView trace(String queryId) {
        QueryRow query = library.findQuery(queryId)
                .orElseThrow(() -> new IllegalArgumentException("查询不存在：" + queryId));
        return new TraceView(query, library.queryStages(queryId));
    }
}
