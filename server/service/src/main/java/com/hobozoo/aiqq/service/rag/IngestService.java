package com.hobozoo.aiqq.service.rag;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hobozoo.aiqq.common.util.JsonText;
import com.hobozoo.aiqq.dao.IngestDao;
import com.hobozoo.aiqq.dao.LibraryDao;
import com.hobozoo.aiqq.dao.SearchDao;
import com.hobozoo.aiqq.entity.rag.BlockDraft;
import com.hobozoo.aiqq.entity.rag.BlockRow;
import com.hobozoo.aiqq.entity.rag.ChildDraft;
import com.hobozoo.aiqq.entity.rag.ChunkInsert;
import com.hobozoo.aiqq.entity.rag.ChunkRow;
import com.hobozoo.aiqq.entity.rag.DocumentRow;
import com.hobozoo.aiqq.entity.rag.EventRow;
import com.hobozoo.aiqq.entity.rag.PageDraft;
import com.hobozoo.aiqq.entity.rag.ParentDraft;
import com.hobozoo.aiqq.entity.rag.PipelineConfig;
import com.hobozoo.aiqq.entity.rag.RunRow;

/**
 * 八个节点的执行与断点续跑。
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);
    private static final String PIPELINE = "rag";
    private static final int SAMPLE_CHARS = 2000;

    private final LibraryDao library;
    private final IngestDao ingest;
    private final PipelineConfigService configs;
    private final PdfExtractor extractor;
    private final BlockAssembler assembler;
    private final Chunker chunker;
    private final EmbeddingModel embedding;
    private final SearchDao search;
    private final LuceneIndexService lucene;

    public IngestService(LibraryDao library, IngestDao ingest, PipelineConfigService configs,
                         PdfExtractor extractor, BlockAssembler assembler, Chunker chunker,
                         EmbeddingModel embedding, SearchDao search, LuceneIndexService lucene) {
        this.library = library;
        this.ingest = ingest;
        this.configs = configs;
        this.extractor = extractor;
        this.assembler = assembler;
        this.chunker = chunker;
        this.embedding = embedding;
        this.search = search;
        this.lucene = lucene;
    }

    public void ingest(long documentId, Long jobId) {
        DocumentRow document = library.findDocument(documentId)
                .orElseThrow(() -> new IllegalStateException("文档不存在：" + documentId));

        int version = configs.currentVersion(PIPELINE);
        PipelineConfig config = configs.load(PIPELINE, version);
        List<String> nodes = config.nodes();
        long pipelineDefId = library.findPipelineDefId(PIPELINE, version)
                .orElseThrow(() -> new IllegalStateException("缺少管线定义：" + PIPELINE + " v" + version));

        int attempt = library.maxAttempt(documentId) + 1;
        Set<String> done = attempt > 1 ? completedNodes(documentId) : Set.of();
        String resumeFrom = nodes.stream().filter(node -> !done.contains(node)).findFirst().orElse(null);
        if (resumeFrom == null) {
            resumeFrom = nodes.get(0);
        }
        if (attempt > 1) {
            ingest.clearFrom(documentId, resumeFrom);
        }

        long runId = library.insertRun(documentId, pipelineDefId, attempt);
        library.updateDocumentStatus(documentId, "parsing", null);
        log.info("开始导入 document={} attempt={} resumeFrom={}", documentId, attempt, resumeFrom);

        PdfExtractor.Result extraction = null;
        BlockAssembler.Assembled assembled = null;
        int resumeIndex = nodes.indexOf(resumeFrom);

        for (int index = resumeIndex; index < nodes.size(); index++) {
            String node = nodes.get(index);
            int progress = (int) Math.round((index + 1) * 100.0 / nodes.size());
            if (jobId != null) {
                library.touchJobProgress(jobId, node, progress);
            }
            long startedAt = System.nanoTime();
            String detail;
            String message = null;
            try {
                if ("parse".equals(node)) {
                    extraction = extractor.extract(Path.of(document.path()));
                    if (extraction.locked()) {
                        library.insertEvent(runId, node, "failed", elapsed(startedAt),
                                JsonText.object(Map.of("locked", true)), "加密且口令非空");
                        library.finishRun(runId, "failed", JsonText.object(Map.of("node", node)));
                        library.updateDocumentStatus(documentId, "locked", "需要口令");
                        return;
                    }
                }
                detail = switch (node) {
                    case "parse" -> parse(documentId, version, extraction);
                    case "tables" -> {
                        assembled = assemble(assembled, extraction, config, document);
                        yield insertBlocks(documentId, assembled, true);
                    }
                    case "blocks" -> {
                        assembled = assemble(assembled, extraction, config, document);
                        yield insertBlocks(documentId, assembled, false);
                    }
                    case "chunk" -> chunk(documentId, config);
                    case "embed" -> embed(documentId);
                    case "index_vector" -> JsonText.object(
                            Map.of("embeddings", search.countEmbeddings(documentId, embedding.name())));
                    case "index_lexical" -> {
                        NodeResult result = lexical(documentId);
                        message = result.message();
                        yield result.detail();
                    }
                    case "finalize" -> {
                        library.updateDocumentStatus(documentId, "ready", null);
                        yield JsonText.object(Map.of("status", "ready"));
                    }
                    default -> {
                        message = "未实现";
                        yield JsonText.object(Map.of("skipped", true));
                    }
                };
            } catch (RuntimeException | IOException e) {
                library.insertEvent(runId, node, "failed", elapsed(startedAt),
                        JsonText.object(Map.of("error", String.valueOf(e.getMessage()))), e.getMessage());
                library.finishRun(runId, "failed", JsonText.object(Map.of("node", node)));
                library.updateDocumentStatus(documentId, "failed", node + ": " + e.getMessage());
                throw new IllegalStateException("管线节点失败：" + node, e);
            }
            library.insertEvent(runId, node, message == null ? "ok" : "skipped", elapsed(startedAt), detail,
                    message);
        }

        library.finishRun(runId, "succeeded", JsonText.object(Map.of("nodes", nodes.size())));
        log.info("导入完成 document={} attempt={}", documentId, attempt);
    }

    private String parse(long documentId, int version, PdfExtractor.Result extraction) {
        List<PageDraft> pages = new ArrayList<>();
        StringBuilder sample = new StringBuilder();
        for (PdfExtractor.Page page : extraction.pages()) {
            pages.add(new PageDraft(page.pageNo(), page.width(), page.height(), page.rotation(),
                    page.charCount(), page.charCount() == 0));
            if (sample.length() < SAMPLE_CHARS) {
                page.lines().forEach(line -> sample.append(line.text()));
            }
        }
        ingest.insertPages(documentId, pages);
        String language = language(sample.toString());
        library.updateDocumentParsed(documentId, extraction.pages().size(), language, false, "pending", version);
        return JsonText.object(Map.of("pages", pages.size(), "language", language,
                "emptyPages", pages.stream().filter(PageDraft::empty).count()));
    }

    private BlockAssembler.Assembled assemble(BlockAssembler.Assembled cached, PdfExtractor.Result extraction,
                                              PipelineConfig config, DocumentRow document) {
        if (cached != null) {
            return cached;
        }
        PdfExtractor.Result result = extraction;
        if (result == null) {
            try {
                result = extractor.extract(Path.of(document.path()));
            } catch (IOException e) {
                throw new IllegalStateException("重新解析失败", e);
            }
        }
        BlockAssembler.Assembled assembled = assembler.assemble(result.pages(), config);
        library.updateDocumentParsed(document.id(), result.pages().size(), document.language(), false,
                assembled.structureSource(), document.pipelineVersion());
        return assembled;
    }

    private String insertBlocks(long documentId, BlockAssembler.Assembled assembled, boolean tables) {
        int inserted = 0;
        for (BlockDraft block : assembled.blocks()) {
            boolean isTable = "table".equals(block.type());
            if (isTable != tables) {
                continue;
            }
            ingest.insertBlock(documentId, block);
            inserted++;
        }
        if (!tables) {
            library.updateDocumentTitle(documentId, assembled.title());
        }
        return JsonText.object(Map.of("blocks", inserted, "removed", assembled.removed()));
    }

    private String embed(long documentId) {
        List<ChunkRow> children = ingest.chunks(documentId, "child");
        search.deleteEmbeddings(documentId);
        for (ChunkRow child : children) {
            search.insertEmbedding(child.id(), embedding.name(), embedding.dim(), embedding.embed(child.text()));
        }
        return JsonText.object(Map.of("children", children.size(), "model", embedding.name()));
    }

    private record NodeResult(String detail, String message) {
    }

    private NodeResult lexical(long documentId) {
        List<ChunkRow> children = ingest.chunks(documentId, "child");
        try {
            int count = lucene.index(documentId, children);
            return new NodeResult(JsonText.object(Map.of("children", count)), null);
        } catch (IOException e) {
            for (ChunkRow child : children) {
                search.insertIndexRetry(child.id(), "lexical", String.valueOf(e.getMessage()));
            }
            return new NodeResult(JsonText.object(Map.of("children", children.size(), "retry", children.size())),
                    "Lucene 写入失败，已进重试：" + e.getMessage());
        }
    }

    private String chunk(long documentId, PipelineConfig config) {
        List<BlockRow> rows = ingest.blocks(documentId);
        List<BlockDraft> blocks = new ArrayList<>(rows.size());
        for (BlockRow row : rows) {
            blocks.add(new BlockDraft(row.pageNo(), row.type(), row.x0(), row.y0(), row.x1(), row.y1(),
                    row.text(), row.orderNo(), row.sectionPath(), row.fontSize(), row.fontName()));
        }
        Chunker.Result result = chunker.chunk(blocks, config.chunk());

        int ord = 0;
        List<Long> parentIds = new ArrayList<>(result.parents().size());
        for (ParentDraft parent : result.parents()) {
            long firstBlockId = parent.blockIndexes().isEmpty() ? -1
                    : rows.get(parent.blockIndexes().get(0)).id();
            String markdown = parent.table() ? toMarkdown(parent.text()) : null;
            long parentId = ingest.insertChunk(new ChunkInsert(documentId, firstBlockId, null, "parent",
                    parent.text(), markdown, parent.pageFrom(), parent.pageTo(), parent.sectionPath(),
                    parent.sha256(), ord++));
            parentIds.add(parentId);
            linkBlocks(parentId, rows, parent.blockIndexes());
        }
        for (ChildDraft child : result.children()) {
            ParentDraft parent = result.parents().get(child.parentIndex());
            long firstBlockId = parent.blockIndexes().isEmpty() ? -1
                    : rows.get(parent.blockIndexes().get(0)).id();
            long parentId = parentIds.get(child.parentIndex());
            String markdown = parent.table() ? toMarkdown(parent.text()) : null;
            long childId = ingest.insertChunk(new ChunkInsert(documentId, firstBlockId, parentId, "child",
                    child.text(), markdown, child.pageFrom(), child.pageTo(), parent.sectionPath(),
                    child.sha256(), ord++));
            linkBlocks(childId, rows, parent.blockIndexes());
        }
        return JsonText.object(Map.of("parents", result.parents().size(), "children", result.children().size()));
    }

    private void linkBlocks(long chunkId, List<BlockRow> rows, List<Integer> blockIndexes) {
        int ord = 0;
        for (Integer index : blockIndexes) {
            if (index >= 0 && index < rows.size()) {
                ingest.linkChunkBlock(chunkId, rows.get(index).id(), ord++);
            }
        }
    }

    static String toMarkdown(String tableText) {
        String[] rows = tableText.split("\n");
        if (rows.length == 0) {
            return tableText;
        }
        List<String> cells = splitCells(rows[0]);
        StringBuilder markdown = new StringBuilder();
        markdown.append("| ").append(String.join(" | ", cells)).append(" |\n");
        markdown.append("|").append("---|".repeat(cells.size())).append("\n");
        for (int i = 1; i < rows.length; i++) {
            markdown.append("| ").append(String.join(" | ", splitCells(rows[i]))).append(" |\n");
        }
        return markdown.toString().strip();
    }

    private static List<String> splitCells(String row) {
        List<String> cells = new ArrayList<>();
        for (String cell : row.split("\\s\\|\\s")) {
            if (!cell.isBlank()) {
                cells.add(cell.strip());
            }
        }
        return cells.isEmpty() ? List.of("") : cells;
    }

    private Set<String> completedNodes(long documentId) {
        List<RunRow> runs = library.runs(documentId);
        if (runs.isEmpty()) {
            return Set.of();
        }
        RunRow latest = runs.get(0);
        Set<String> nodes = new LinkedHashSet<>();
        for (EventRow event : library.events(latest.id())) {
            if ("ok".equals(event.status())) {
                nodes.add(event.node());
            }
        }
        return nodes;
    }

    private static int elapsed(long startedAt) {
        return (int) ((System.nanoTime() - startedAt) / 1_000_000);
    }

    private static String language(String sample) {
        long cjk = sample.chars().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
        long latin = sample.chars().filter(c -> Character.isLetter(c) && c < 0x4E00).count();
        if (cjk == 0 && latin == 0) {
            return "unknown";
        }
        if (cjk > latin * 3) {
            return "zh";
        }
        if (latin > cjk * 3) {
            return "en";
        }
        return "mixed";
    }
}
