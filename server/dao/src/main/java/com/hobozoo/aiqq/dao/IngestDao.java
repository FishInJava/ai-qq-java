package com.hobozoo.aiqq.dao;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.hobozoo.aiqq.entity.rag.BlockDraft;
import com.hobozoo.aiqq.entity.rag.BlockRow;
import com.hobozoo.aiqq.entity.rag.ChunkRow;
import com.hobozoo.aiqq.entity.rag.PageDraft;
import com.hobozoo.aiqq.entity.rag.PageRow;

/**
 * 解析结果：页、块、切块。
 */
@Repository
public class IngestDao {

    private final JdbcTemplate jdbc;

    public IngestDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void clearFrom(long documentId, String node) {
        switch (node) {
            case "parse" -> {
                jdbc.update("delete from chunks where document_id = ?", documentId);
                jdbc.update("delete from blocks where document_id = ?", documentId);
                jdbc.update("delete from pages where document_id = ?", documentId);
            }
            case "tables" -> {
                jdbc.update("delete from chunks where document_id = ?", documentId);
                jdbc.update("delete from blocks where document_id = ? and block_type = 'table'", documentId);
            }
            case "blocks" -> {
                jdbc.update("delete from chunks where document_id = ?", documentId);
                jdbc.update("delete from blocks where document_id = ? and block_type <> 'table'", documentId);
            }
            case "chunk" -> jdbc.update("delete from chunks where document_id = ?", documentId);
            default -> {
                // 后续节点本期没有落库产物
            }
        }
    }

    public void insertPages(long documentId, List<PageDraft> pages) {
        jdbc.batchUpdate("""
                insert into pages (document_id, page_no, width, height, rotation, char_count, is_empty)
                values (?, ?, ?, ?, ?, ?, ?)
                """, pages, 100, (PreparedStatement ps, PageDraft page) -> {
            ps.setLong(1, documentId);
            ps.setInt(2, page.pageNo());
            ps.setDouble(3, page.width());
            ps.setDouble(4, page.height());
            ps.setInt(5, page.rotation());
            ps.setInt(6, page.charCount());
            ps.setBoolean(7, page.empty());
        });
    }

    public long insertBlock(long documentId, BlockDraft block) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into blocks (document_id, page_no, block_type, bbox_x0, bbox_y0, bbox_x1, bbox_y1,
                                        text, order_no, section_path, font_size, font_name)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id
                    """, new String[]{"id"});
            ps.setLong(1, documentId);
            ps.setInt(2, block.pageNo());
            ps.setString(3, block.type());
            ps.setDouble(4, block.x0());
            ps.setDouble(5, block.y0());
            ps.setDouble(6, block.x1());
            ps.setDouble(7, block.y1());
            ps.setString(8, block.text());
            ps.setInt(9, block.orderNo());
            ps.setArray(10, textArray(ps, block.sectionPath()));
            if (block.fontSize() == null) {
                ps.setNull(11, Types.DOUBLE);
            } else {
                ps.setDouble(11, block.fontSize());
            }
            ps.setString(12, block.fontName());
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    public long insertChunk(long documentId, long blockId, Long parentId, String level, String text, String textMd,
                            int pageFrom, int pageTo, String[] sectionPath, String sha256, int ord) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into chunks (document_id, block_id, parent_id, level, text, text_md, char_count,
                                        page_from, page_to, section_path, sha256, ord)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id
                    """, new String[]{"id"});
            ps.setLong(1, documentId);
            if (blockId <= 0) {
                ps.setNull(2, Types.BIGINT);
            } else {
                ps.setLong(2, blockId);
            }
            if (parentId == null) {
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(3, parentId);
            }
            ps.setString(4, level);
            ps.setString(5, text);
            ps.setString(6, textMd);
            ps.setInt(7, text.length());
            ps.setInt(8, pageFrom);
            ps.setInt(9, pageTo);
            ps.setArray(10, textArray(ps, sectionPath));
            ps.setString(11, sha256);
            ps.setInt(12, ord);
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    public void linkChunkBlock(long chunkId, long blockId, int ord) {
        jdbc.update("insert into chunk_blocks (chunk_id, block_id, ord) values (?, ?, ?) on conflict do nothing",
                chunkId, blockId, ord);
    }

    public List<ChunkRow> chunks(long documentId, String level) {
        RowMapper<ChunkRow> mapper = (rs, rowNum) -> new ChunkRow(
                rs.getLong("id"), rs.getString("level"), rs.getLong("parent_id"), rs.getString("text"),
                rs.getInt("page_from"), rs.getInt("page_to"), rs.getString("section_path"),
                rs.getString("sha256"));
        return jdbc.query("""
                select id, level, coalesce(parent_id, 0) as parent_id, text, page_from, page_to,
                       array_to_string(section_path, ' / ') as section_path, sha256
                from chunks where document_id = ? and level = ? order by ord, id
                """, mapper, documentId, level);
    }

    public List<BlockRow> blocks(long documentId) {
        return jdbc.query("""
                select id, page_no, block_type, text, order_no, section_path, font_size, font_name,
                       bbox_x0, bbox_y0, bbox_x1, bbox_y1
                from blocks where document_id = ? order by page_no, order_no, id
                """, (rs, rowNum) -> new BlockRow(
                rs.getLong("id"), rs.getInt("page_no"), rs.getString("block_type"), rs.getString("text"),
                rs.getInt("order_no"), (String[]) rs.getArray("section_path").getArray(),
                (Double) rs.getObject("font_size"), rs.getString("font_name"),
                rs.getDouble("bbox_x0"), rs.getDouble("bbox_y0"), rs.getDouble("bbox_x1"), rs.getDouble("bbox_y1")),
                documentId);
    }

    public List<PageRow> pages(long documentId) {
        return jdbc.query("""
                select page_no, width, height, rotation, char_count, is_empty
                from pages where document_id = ? order by page_no
                """, (rs, rowNum) -> new PageRow(
                rs.getInt("page_no"), rs.getDouble("width"), rs.getDouble("height"), rs.getInt("rotation"),
                rs.getInt("char_count"), rs.getBoolean("is_empty")), documentId);
    }

    private static java.sql.Array textArray(PreparedStatement ps, String[] values) throws SQLException {
        return ps.getConnection().createArrayOf("text", values);
    }
}
