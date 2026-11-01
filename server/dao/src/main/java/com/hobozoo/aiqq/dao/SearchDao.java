package com.hobozoo.aiqq.dao;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.hobozoo.aiqq.entity.rag.ChunkHit;
import com.hobozoo.aiqq.entity.rag.Passage;

/**
 * 向量检索与父块回取。
 */
@Repository
public class SearchDao {

    private final JdbcTemplate jdbc;

    public SearchDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void deleteEmbeddings(long documentId) {
        jdbc.update("""
                delete from embeddings e using chunks c
                where e.chunk_id = c.id and c.document_id = ?
                """, documentId);
    }

    public void insertEmbedding(long chunkId, String model, int dim, float[] vector) {
        jdbc.update("""
                insert into embeddings (chunk_id, model, dim, vector)
                values (?, ?, ?, cast(? as vector))
                on conflict (chunk_id, model) do update set vector = excluded.vector, dim = excluded.dim
                """, chunkId, model, dim, literal(vector));
    }

    public long countEmbeddings(long documentId, String model) {
        Long count = jdbc.queryForObject("""
                select count(*) from embeddings e join chunks c on c.id = e.chunk_id
                where c.document_id = ? and e.model = ?
                """, Long.class, documentId, model);
        return count == null ? 0 : count;
    }

    public List<ChunkHit> vectorSearch(String model, float[] query, int limit, Long documentId) {
        RowMapper<ChunkHit> mapper = (rs, rowNum) -> new ChunkHit(
                rs.getLong("chunk_id"), rs.getLong("parent_id"), rs.getDouble("score"));
        if (documentId == null) {
            return jdbc.query("""
                    select c.id as chunk_id, c.parent_id, 1 - (e.vector <=> cast(? as vector)) as score
                    from embeddings e
                    join chunks c on c.id = e.chunk_id
                    join documents d on d.id = c.document_id
                    where e.model = ? and d.deleted_at is null and c.parent_id is not null
                    order by e.vector <=> cast(? as vector)
                    limit ?
                    """, mapper, literal(query), model, literal(query), limit);
        }
        return jdbc.query("""
                select c.id as chunk_id, c.parent_id, 1 - (e.vector <=> cast(? as vector)) as score
                from embeddings e
                join chunks c on c.id = e.chunk_id
                join documents d on d.id = c.document_id
                where e.model = ? and d.deleted_at is null and c.parent_id is not null and c.document_id = ?
                order by e.vector <=> cast(? as vector)
                limit ?
                """, mapper, literal(query), model, documentId, literal(query), limit);
    }

    public record ChunkBox(int pageNo, double x0, double y0, double x1, double y1) {
    }

    public record ChunkBoxes(long documentId, List<ChunkBox> boxes) {
    }

    public record IndexChunk(long chunkId, long documentId, long parentId, String text, int pageFrom, int pageTo,
                             String sectionPath) {
    }

    public record RetryRow(long id, long chunkId, int attempts) {
    }

    public ChunkBoxes chunkBoxes(long chunkId) {
        Long documentId = jdbc.queryForList("select document_id from chunks where id = ?", Long.class, chunkId)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("切块不存在：" + chunkId));
        List<ChunkBox> boxes = jdbc.query("""
                select b.page_no, b.bbox_x0, b.bbox_y0, b.bbox_x1, b.bbox_y1
                from chunk_blocks cb
                join blocks b on b.id = cb.block_id
                where cb.chunk_id = ?
                order by b.page_no, b.order_no, b.id
                """, (rs, rowNum) -> new ChunkBox(rs.getInt("page_no"), rs.getDouble("bbox_x0"),
                rs.getDouble("bbox_y0"), rs.getDouble("bbox_x1"), rs.getDouble("bbox_y1")), chunkId);
        if (boxes.isEmpty()) {
            boxes = jdbc.query("""
                    select b.page_no, b.bbox_x0, b.bbox_y0, b.bbox_x1, b.bbox_y1
                    from chunks c join blocks b on b.id = c.block_id
                    where c.id = ?
                    """, (rs, rowNum) -> new ChunkBox(rs.getInt("page_no"), rs.getDouble("bbox_x0"),
                    rs.getDouble("bbox_y0"), rs.getDouble("bbox_x1"), rs.getDouble("bbox_y1")), chunkId);
        }
        return new ChunkBoxes(documentId, boxes);
    }

    public IndexChunk findIndexChunk(long chunkId) {
        return jdbc.query("""
                select c.id, c.document_id, coalesce(c.parent_id, c.id) as parent_id, c.text,
                       c.page_from, c.page_to, array_to_string(c.section_path, ' / ') as section_path
                from chunks c where c.id = ?
                """, (rs, rowNum) -> new IndexChunk(rs.getLong("id"), rs.getLong("document_id"),
                rs.getLong("parent_id"), rs.getString("text"), rs.getInt("page_from"), rs.getInt("page_to"),
                rs.getString("section_path")), chunkId).stream().findFirst().orElse(null);
    }

    public void insertIndexRetry(long chunkId, String operation, String error) {
        jdbc.update("""
                insert into index_retry (chunk_id, operation, attempts, last_error)
                values (?, ?, 1, ?)
                """, chunkId, operation, error);
    }

    public List<RetryRow> dueRetries(int limit) {
        return jdbc.query("""
                select id, chunk_id, attempts from index_retry
                where next_at <= now() order by next_at limit ?
                """, (rs, rowNum) -> new RetryRow(rs.getLong("id"), rs.getLong("chunk_id"), rs.getInt("attempts")),
                limit);
    }

    public void deleteRetry(long id) {
        jdbc.update("delete from index_retry where id = ?", id);
    }

    public void bumpRetry(long id, String error) {
        jdbc.update("""
                update index_retry
                set attempts = attempts + 1, last_error = ?,
                    next_at = now() + (least(attempts, 6) * interval '1 minute')
                where id = ?
                """, error, id);
    }

    public List<Passage> passages(List<Long> parentIds) {
        if (parentIds.isEmpty()) {
            return List.of();
        }
        String array = "{" + String.join(",", parentIds.stream().map(String::valueOf).toList()) + "}";
        return jdbc.query("""
                select c.id, c.parent_id, c.document_id, d.filename, c.text, c.page_from, c.page_to, c.section_path
                from chunks c
                join documents d on d.id = c.document_id
                where c.id = any (cast(? as bigint[])) and d.deleted_at is null
                """, (rs, rowNum) -> new Passage(
                rs.getLong("id"), rs.getLong("parent_id"), rs.getLong("document_id"), rs.getString("filename"),
                rs.getString("text"), rs.getInt("page_from"), rs.getInt("page_to"),
                (String[]) rs.getArray("section_path").getArray(), 0), array);
    }

    static String literal(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 12);
        builder.append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(vector[index]);
        }
        return builder.append(']').toString();
    }
}
