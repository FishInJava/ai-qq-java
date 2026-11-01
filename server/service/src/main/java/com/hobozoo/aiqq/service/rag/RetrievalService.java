package com.hobozoo.aiqq.service.rag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.hobozoo.aiqq.dao.LibraryDao;
import com.hobozoo.aiqq.dao.SearchDao;
import com.hobozoo.aiqq.entity.rag.ChunkHit;
import com.hobozoo.aiqq.entity.rag.Passage;
import com.hobozoo.aiqq.common.util.JsonText;

import tools.jackson.databind.ObjectMapper;

/**
 * 两路召回、RRF 融合、父块回取与拒答判定。
 */
@Service
public class RetrievalService {

    public record Stage(String stage, long durationMs, String payloadJson) {
    }

    public record Retrieval(List<Passage> passages, List<Stage> stages, boolean refused, double maxCosine) {
    }

    public record RetrievalConfig(Bm25 bm25, Vector vector, Fusion fusion, Rerank rerank, Context context,
                                  Refusal refusal, String queryInstruction) {
        public record Bm25(int topK) {
        }

        public record Vector(int topK, double minCosine) {
        }

        public record Fusion(int rrfK, int topK) {
        }

        public record Rerank(int topK) {
        }

        public record Context(int tokenBudget) {
        }

        public record Refusal(double minFusionScore) {
        }
    }

    private final LuceneIndexService lucene;
    private final SearchDao search;
    private final EmbeddingModel model;
    private final LibraryDao library;
    private final ObjectMapper mapper;

    public RetrievalService(LuceneIndexService lucene, SearchDao search, EmbeddingModel model, LibraryDao library,
                            ObjectMapper mapper) {
        this.lucene = lucene;
        this.search = search;
        this.model = model;
        this.library = library;
        this.mapper = mapper;
    }

    public Retrieval retrieve(String question, Long documentId, Consumer<Stage> onStage) throws IOException {
        RetrievalConfig config = config();
        List<Stage> stages = new ArrayList<>();

        long startedAt = System.nanoTime();
        List<ChunkHit> bm25 = lucene.search(question, config.bm25().topK(), documentId);
        Stage bm25Stage = new Stage("bm25", elapsed(startedAt), hits(bm25));
        stages.add(bm25Stage);
        onStage.accept(bm25Stage);

        startedAt = System.nanoTime();
        float[] vector = model.embed(config.queryInstruction() + question);
        List<ChunkHit> semantic = search.vectorSearch(model.name(), vector, config.vector().topK(), documentId);
        Stage vectorStage = new Stage("vector", elapsed(startedAt), hits(semantic));
        stages.add(vectorStage);
        onStage.accept(vectorStage);

        startedAt = System.nanoTime();
        List<ChunkHit> fused = fuse(bm25, semantic, config.fusion().rrfK(), config.fusion().topK());
        Stage fusionStage = new Stage("fusion", elapsed(startedAt), hits(fused));
        stages.add(fusionStage);
        onStage.accept(fusionStage);

        startedAt = System.nanoTime();
        List<Passage> passages = fetch(fused, config.rerank().topK(), config.context().tokenBudget());
        Stage fetchStage = new Stage("fetch", elapsed(startedAt), JsonText.object(Map.of(
                "parents", passages.size(),
                "pages", passages.stream().map(Passage::pageFrom).distinct().count())));
        stages.add(fetchStage);
        onStage.accept(fetchStage);

        startedAt = System.nanoTime();
        Stage rerankStage = new Stage("rerank", elapsed(startedAt), JsonText.object(Map.of(
                "enabled", false, "topK", config.rerank().topK(), "passthrough", passages.size())));
        stages.add(rerankStage);
        onStage.accept(rerankStage);

        double maxCosine = semantic.stream().mapToDouble(ChunkHit::score).max().orElse(0);
        double topFusion = fused.isEmpty() ? 0 : fused.get(0).score();
        boolean stub = HashEmbeddingModel.NAME.equals(model.name());
        boolean refused = passages.isEmpty()
                || fused.isEmpty()
                || topFusion < config.refusal().minFusionScore()
                || (!stub && maxCosine < config.vector().minCosine());
        return new Retrieval(passages, stages, refused, maxCosine);
    }

    private List<Passage> fetch(List<ChunkHit> fused, int topK, int tokenBudget) {
        List<Long> parentIds = fused.stream().map(ChunkHit::parentId).distinct().limit(topK).toList();
        Map<Long, Passage> byId = new LinkedHashMap<>();
        for (Passage passage : search.passages(parentIds)) {
            byId.put(passage.chunkId(), passage);
        }
        List<Passage> ordered = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        int chars = 0;
        for (ChunkHit hit : fused) {
            Passage passage = byId.get(hit.parentId());
            if (passage == null || !seen.add(hit.parentId())) {
                continue;
            }
            if (chars + passage.text().length() > tokenBudget && !ordered.isEmpty()) {
                break;
            }
            chars += passage.text().length();
            ordered.add(new Passage(passage.chunkId(), passage.parentId(), passage.documentId(),
                    passage.filename(), passage.text(), passage.pageFrom(), passage.pageTo(),
                    passage.sectionPath(), hit.score()));
            if (ordered.size() >= topK) {
                break;
            }
        }
        return ordered;
    }

    static List<ChunkHit> fuse(List<ChunkHit> bm25, List<ChunkHit> semantic, int k, int topK) {
        Map<Long, Double> scores = new LinkedHashMap<>();
        addScores(scores, bm25, k);
        addScores(scores, semantic, k);
        return scores.entrySet().stream()
                .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                .limit(topK)
                .map(entry -> new ChunkHit(0, entry.getKey(), entry.getValue()))
                .toList();
    }

    private static void addScores(Map<Long, Double> scores, List<ChunkHit> hits, int k) {
        for (int rank = 0; rank < hits.size(); rank++) {
            long parentId = hits.get(rank).parentId();
            scores.merge(parentId, 1.0 / (k + rank + 1), Double::sum);
        }
    }

    private static String hits(List<ChunkHit> hits) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ChunkHit hit : hits.stream().limit(5).toList()) {
            items.add(Map.of("parent", hit.parentId(), "score", Math.round(hit.score() * 10000) / 10000.0));
        }
        return JsonText.object(Map.of("count", hits.size(), "top", items));
    }

    private RetrievalConfig config() {
        String json = library.appConfig("retrieval");
        if (json == null || json.isBlank()) {
            return new RetrievalConfig(new RetrievalConfig.Bm25(40), new RetrievalConfig.Vector(40, 0.40),
                    new RetrievalConfig.Fusion(60, 20), new RetrievalConfig.Rerank(8),
                    new RetrievalConfig.Context(8000), new RetrievalConfig.Refusal(0.012), "");
        }
        return mapper.readValue(json, RetrievalConfig.class);
    }

    private static long elapsed(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
