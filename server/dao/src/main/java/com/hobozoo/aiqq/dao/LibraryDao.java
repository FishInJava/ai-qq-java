package com.hobozoo.aiqq.dao;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.hobozoo.aiqq.entity.rag.DocumentRow;
import com.hobozoo.aiqq.entity.rag.EvalCaseRow;
import com.hobozoo.aiqq.entity.rag.EventRow;
import com.hobozoo.aiqq.entity.rag.HealthRow;
import com.hobozoo.aiqq.entity.rag.JobRow;
import com.hobozoo.aiqq.entity.rag.MessageRow;
import com.hobozoo.aiqq.entity.rag.QueryRow;
import com.hobozoo.aiqq.entity.rag.QueryStageRow;
import com.hobozoo.aiqq.entity.rag.RunRow;
import com.hobozoo.aiqq.entity.rag.SessionRow;
import com.hobozoo.aiqq.entity.rag.TurnRow;

/**
 * 文档、任务、管线运行记录。
 */
@Repository
public class LibraryDao {

    private final JdbcTemplate jdbc;

    public LibraryDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<DocumentRow> DOCUMENT = (ResultSet rs, int rowNum) -> new DocumentRow(
            rs.getLong("id"), rs.getString("path"), rs.getString("sha256"), rs.getString("filename"),
            rs.getString("title"), rs.getString("category"), rs.getInt("pages"), rs.getLong("bytes"),
            rs.getString("language"), rs.getBoolean("encrypted"), rs.getString("structure_source"),
            (Integer) rs.getObject("pipeline_version"), rs.getString("status"), rs.getString("error"),
            rs.getTimestamp("created_at").toInstant().toString());

    private static final RowMapper<QueryRow> QUERY = (ResultSet rs, int rowNum) -> new QueryRow(
            rs.getString("id"), rs.getString("session_id"), rs.getString("question"),
            rs.getString("answer"), rs.getString("model"), (Integer) rs.getObject("tokens_in"),
            (Integer) rs.getObject("tokens_out"), (Integer) rs.getObject("latency_ms"),
            (Integer) rs.getObject("first_token_ms"), rs.getString("status"), rs.getString("error"),
            rs.getTimestamp("created_at").toInstant().toString());

    private static final RowMapper<JobRow> JOB = (ResultSet rs, int rowNum) -> new JobRow(
            rs.getLong("id"), rs.getString("type"), rs.getString("source_path"),
            (Long) rs.getObject("document_id"), rs.getString("status"), rs.getString("stage"),
            rs.getInt("progress"), rs.getInt("total"), rs.getInt("done"), rs.getString("error"),
            rs.getTimestamp("created_at").toInstant().toString(),
            rs.getTimestamp("updated_at").toInstant().toString());

    public Optional<DocumentRow> findActiveBySha(String sha256) {
        return jdbc.query("select * from documents where sha256 = ? and deleted_at is null", DOCUMENT, sha256)
                .stream().findFirst();
    }

    public Optional<DocumentRow> findDocument(long id) {
        return jdbc.query("select * from documents where id = ? and deleted_at is null", DOCUMENT, id)
                .stream().findFirst();
    }

    public List<DocumentRow> listDocuments(String status, int limit) {
        if (status == null || status.isBlank()) {
            return jdbc.query("select * from documents where deleted_at is null order by id desc limit ?",
                    DOCUMENT, limit);
        }
        return jdbc.query("select * from documents where deleted_at is null and status = ? order by id desc limit ?",
                DOCUMENT, status, limit);
    }

    public long insertDocument(String path, String sha256, String filename, long bytes, String category) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    insert into documents (path, sha256, filename, bytes, category, status)
                    values (?, ?, ?, ?, ?, 'queued') returning id
                    """, new String[]{"id"});
            statement.setString(1, path);
            statement.setString(2, sha256);
            statement.setString(3, filename);
            statement.setLong(4, bytes);
            statement.setString(5, category);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    public void updateDocumentStatus(long id, String status, String error) {
        jdbc.update("update documents set status = ?, error = ?, updated_at = now() where id = ?",
                status, error, id);
    }

    public void updateDocumentParsed(long id, int pages, String language, boolean encrypted,
                                     String structureSource, Integer pipelineVersion) {
        jdbc.update("""
                update documents set pages = ?, language = coalesce(?, language), encrypted = ?,
                       structure_source = ?, pipeline_version = coalesce(?, pipeline_version), updated_at = now()
                where id = ?
                """, pages, language, encrypted, structureSource, pipelineVersion, id);
    }

    public void softDeleteDocument(long id) {
        jdbc.update("""
                update documents set deleted_at = now(), status = 'deleted', updated_at = now()
                where id = ? and deleted_at is null
                """, id);
    }

    public void updateDocumentTitle(long id, String title) {
        if (title == null || title.isBlank()) {
            return;
        }
        jdbc.update("update documents set title = ?, updated_at = now() where id = ?", title, id);
    }

    public long insertJob(String type, String sourcePath, Long documentId, int total) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    insert into jobs (type, source_path, document_id, status, stage, total)
                    values (?, ?, ?, 'queued', 'queued', ?) returning id
                    """, new String[]{"id"});
            statement.setString(1, type);
            statement.setString(2, sourcePath);
            statement.setObject(3, documentId);
            statement.setInt(4, total);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    public void updateJob(long id, String status, String stage, int progress, int done, String error) {
        jdbc.update("""
                update jobs set status = ?, stage = ?, progress = ?, done = ?, error = ?, updated_at = now()
                where id = ?
                """, status, stage, progress, done, error, id);
    }

    public Optional<JobRow> findJob(long id) {
        return jdbc.query("select * from jobs where id = ?", JOB, id).stream().findFirst();
    }

    public List<JobRow> listJobs(int limit) {
        return jdbc.query("select * from jobs order by id desc limit ?", JOB, limit);
    }

    public List<Long> unfinishedIngestJobs() {
        return jdbc.queryForList("""
                select id from jobs where type = 'ingest' and status in ('queued', 'running') order by id
                """, Long.class);
    }

    public long insertRun(long documentId, long pipelineDefId, int attempt) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    insert into pipeline_runs (document_id, pipeline_def_id, attempt, status)
                    values (?, ?, ?, 'running') returning id
                    """, new String[]{"id"});
            statement.setLong(1, documentId);
            statement.setLong(2, pipelineDefId);
            statement.setInt(3, attempt);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    public void finishRun(long runId, String status, String statsJson) {
        jdbc.update("""
                update pipeline_runs set status = ?, finished_at = now(), stats = cast(? as jsonb) where id = ?
                """, status, statsJson, runId);
    }

    public void insertEvent(long runId, String node, String status, Integer durationMs, String detailJson,
                            String message) {
        jdbc.update("""
                insert into pipeline_events (run_id, node, status, duration_ms, detail, message)
                values (?, ?, ?, ?, cast(? as jsonb), ?)
                """, runId, node, status, durationMs, detailJson, message);
    }

    public Optional<Long> findPipelineDefId(String name, int version) {
        return jdbc.queryForList("select id from pipeline_def where name = ? and version = ?", Long.class, name, version)
                .stream().findFirst();
    }

    public Integer currentPipelineVersion(String name) {
        return jdbc.queryForObject("select max(version) from pipeline_def where name = ?", Integer.class, name);
    }

    public String pipelineDefinition(String name, int version) {
        return jdbc.queryForObject("select definition::text from pipeline_def where name = ? and version = ?",
                String.class, name, version);
    }

    public int maxAttempt(long documentId) {
        Integer value = jdbc.queryForObject("select coalesce(max(attempt), 0) from pipeline_runs where document_id = ?",
                Integer.class, documentId);
        return value == null ? 0 : value;
    }

    public List<RunRow> runs(long documentId) {
        return jdbc.query("""
                select id, attempt, status, started_at, finished_at, stats::text as stats
                from pipeline_runs where document_id = ? order by attempt desc
                """, (ResultSet rs, int rowNum) -> new RunRow(
                rs.getLong("id"), rs.getInt("attempt"), rs.getString("status"),
                rs.getTimestamp("started_at").toInstant().toString(),
                rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant().toString(),
                rs.getString("stats")), documentId);
    }

    public List<EventRow> events(long runId) {
        return jdbc.query("""
                select node, status, duration_ms, detail::text as detail, message
                from pipeline_events where run_id = ? order by id
                """, (ResultSet rs, int rowNum) -> new EventRow(
                rs.getString("node"), rs.getString("status"), (Integer) rs.getObject("duration_ms"),
                rs.getString("detail"), rs.getString("message")), runId);
    }

    public void touchJobProgress(long jobId, String stage, int progress) {
        jdbc.update("update jobs set stage = ?, progress = ?, updated_at = now() where id = ?", stage, progress, jobId);
    }

    public void requeueRunningJobs() {
        jdbc.update("update jobs set status = 'queued', stage = 'queued' where status = 'running'");
    }

    public HealthRow health() {
        String postgres = jdbc.queryForObject("select current_setting('server_version')", String.class);
        String pgvector = jdbc.queryForObject(
                "select extversion from pg_extension where extname = 'vector'", String.class);
        Integer tables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public'", Integer.class);
        return new HealthRow(postgres, pgvector, tables == null ? 0 : tables);
    }

    public String appConfig(String key) {
        return jdbc.queryForList("select value::text from app_config where key = ?", String.class, key)
                .stream().findFirst().orElse(null);
    }

    public String createSession(String title) {
        return jdbc.queryForObject("insert into sessions (title) values (?) returning id", String.class, title);
    }

    public String insertQuery(String sessionId, String requestId, String question) {
        jdbc.update("update sessions set updated_at = now() where id = ?", sessionId);
        return jdbc.queryForObject("""
                insert into queries (session_id, request_id, question, status)
                values (?, ?, ?, 'running') returning id
                """, String.class, sessionId, requestId, question);
    }

    public Optional<QueryRow> findQueryByRequestId(String requestId) {
        return jdbc.query("""
                select id, session_id, question, answer, model, tokens_in, tokens_out, latency_ms,
                       first_token_ms, status, error, created_at
                from queries where request_id = ?
                """, QUERY, requestId).stream().findFirst();
    }

    public void insertFeedback(String queryId, String verdict, String note) {
        jdbc.update("insert into feedback (query_id, verdict, note) values (?, ?, ?)",
                queryId, verdict, note);
    }

    public void finishQuery(String id, String status, long latencyMs, String error) {
        jdbc.update("update queries set status = ?, latency_ms = ?, error = ? where id = ?",
                status, latencyMs, error, id);
    }

    public void insertQueryStage(String queryId, String stage, long durationMs, String payloadJson) {
        jdbc.update("""
                insert into query_stages (query_id, attempt, stage, duration_ms, payload)
                values (?, 1, ?, ?, cast(? as jsonb))
                on conflict (query_id, attempt, stage) do update
                   set duration_ms = excluded.duration_ms, payload = excluded.payload
                """, queryId, stage, durationMs, payloadJson);
    }

    public Optional<QueryRow> findQuery(String id) {
        return jdbc.query("""
                select id, session_id, question, answer, model, tokens_in, tokens_out, latency_ms,
                       first_token_ms, status, error, created_at
                from queries where id = ?
                """, QUERY, id).stream().findFirst();
    }

    public List<QueryStageRow> queryStages(String queryId) {
        return jdbc.query("""
                select stage, duration_ms, payload::text as payload, now()::text as recorded_at
                from query_stages where query_id = ? order by duration_ms desc, stage
                """, (ResultSet rs, int rowNum) -> new QueryStageRow(
                rs.getString("stage"), (Integer) rs.getObject("duration_ms"), rs.getString("payload"),
                rs.getString("recorded_at")), queryId);
    }

    public void finishQueryAnswer(String id, String answer, Integer tokensIn, Integer tokensOut, long latencyMs,
                                  Integer firstTokenMs, String status) {
        jdbc.update("""
                update queries set answer = ?, tokens_in = ?, tokens_out = ?, latency_ms = ?,
                       first_token_ms = ?, status = ?
                where id = ?
                """, answer, tokensIn, tokensOut, latencyMs, firstTokenMs, status, id);
    }

    public void insertPromptSnapshot(String queryId, int attempt, String systemPrompt, String userPrompt,
                                     String blocksJson) {
        jdbc.update("""
                insert into prompt_snapshots (query_id, attempt, system_prompt, user_prompt, blocks)
                values (?, ?, ?, ?, cast(? as jsonb))
                """, queryId, attempt, systemPrompt, userPrompt, blocksJson);
    }

    public List<SessionRow> sessions(int limit) {
        return jdbc.query("""
                select id, coalesce(title, '') as title, created_at, updated_at
                from sessions order by updated_at desc limit ?
                """, (ResultSet rs, int rowNum) -> new SessionRow(
                rs.getString("id"), rs.getString("title"),
                rs.getTimestamp("created_at").toInstant().toString(),
                rs.getTimestamp("updated_at").toInstant().toString()), limit);
    }

    public List<MessageRow> messages(String sessionId) {
        return jdbc.query("""
                select role, content, query_id, created_at from (
                    select 'user' as role, question as content, id as query_id, created_at, 0 as ord
                    from queries where session_id = ?
                    union all
                    select 'assistant' as role, answer as content, id as query_id, created_at, 1 as ord
                    from queries where session_id = ? and answer is not null
                ) merged order by created_at, ord
                """, (ResultSet rs, int rowNum) -> new MessageRow(
                rs.getString("role"), rs.getString("content"), rs.getString("query_id"),
                rs.getTimestamp("created_at").toInstant().toString()), sessionId, sessionId);
    }

    public List<EvalCaseRow> evalCases() {
        return jdbc.query("""
                select id, question, kind, expected_doc, expected_page_from, expected_page_to,
                       expected_keywords, expected_sources::text as expected_sources
                from eval_cases order by id
                """, (ResultSet rs, int rowNum) -> new EvalCaseRow(
                rs.getLong("id"), rs.getString("question"), rs.getString("kind"), rs.getString("expected_doc"),
                (Integer) rs.getObject("expected_page_from"), (Integer) rs.getObject("expected_page_to"),
                (String[]) rs.getArray("expected_keywords").getArray(), rs.getString("expected_sources")));
    }

    public long insertEvalCase(String question, String kind, String expectedDoc, Integer pageFrom, Integer pageTo,
                               String[] keywords, String expectedSourcesJson) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    insert into eval_cases (question, kind, expected_doc, expected_page_from, expected_page_to,
                                            expected_keywords, expected_sources)
                    values (?, ?, ?, ?, ?, ?, cast(? as jsonb)) returning id
                    """, new String[]{"id"});
            statement.setString(1, question);
            statement.setString(2, kind);
            statement.setString(3, expectedDoc);
            statement.setObject(4, pageFrom);
            statement.setObject(5, pageTo);
            statement.setArray(6, connection.createArrayOf("text", keywords));
            statement.setString(7, expectedSourcesJson);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    public List<TurnRow> recentTurns(String sessionId, int limit) {
        return jdbc.query("""
                select question, answer from queries
                where session_id = ? and answer is not null
                order by created_at desc limit ?
                """, (ResultSet rs, int rowNum) -> new TurnRow(
                rs.getString("question"), rs.getString("answer")), sessionId, limit);
    }
}
