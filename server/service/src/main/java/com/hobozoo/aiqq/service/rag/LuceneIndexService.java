package com.hobozoo.aiqq.service.rag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.hobozoo.aiqq.entity.rag.ChunkHit;
import com.hobozoo.aiqq.entity.rag.ChunkRow;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * 子块的倒排索引，BM25 与 smartcn 分词。
 */
@Component
public class LuceneIndexService {

    private static final Logger log = LoggerFactory.getLogger(LuceneIndexService.class);
    private static final String VERSION_DIR = "lucene-1";
    private static final float K1 = 1.2f;
    private static final float B = 0.75f;

    private final RagProperties properties;
    private Directory directory;
    private SmartChineseAnalyzer analyzer;
    private IndexWriter writer;
    private SearcherManager searcherManager;

    public LuceneIndexService(RagProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void open() throws IOException {
        Path path = Path.of(properties.indexRoot(), VERSION_DIR);
        Files.createDirectories(path);
        directory = FSDirectory.open(path);
        analyzer = new SmartChineseAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setSimilarity(new BM25Similarity(K1, B));
        writer = new IndexWriter(directory, config);
        searcherManager = new SearcherManager(writer, new SearcherFactory());
        log.info("Lucene 索引目录 {}，当前 {} 个文档", path, writer.getDocStats().numDocs);
    }

    @PreDestroy
    void close() throws IOException {
        if (searcherManager != null) {
            searcherManager.close();
        }
        if (writer != null) {
            writer.close();
        }
        if (analyzer != null) {
            analyzer.close();
        }
        if (directory != null) {
            directory.close();
        }
    }

    public synchronized int index(long documentId, List<ChunkRow> chunks) throws IOException {
        writer.deleteDocuments(new Term("documentId", String.valueOf(documentId)));
        for (ChunkRow chunk : chunks) {
            add(documentId, chunk.id(), chunk.parentId(), chunk.text(), chunk.pageFrom(), chunk.pageTo(),
                    chunk.sectionPath());
        }
        writer.commit();
        searcherManager.maybeRefreshBlocking();
        return chunks.size();
    }

    public synchronized void delete(long documentId) throws IOException {
        writer.deleteDocuments(new Term("documentId", String.valueOf(documentId)));
        writer.commit();
        searcherManager.maybeRefreshBlocking();
    }

    public synchronized void indexOne(long documentId, long chunkId, long parentId, String text, int pageFrom,
                                      int pageTo, String sectionPath) throws IOException {
        writer.deleteDocuments(new Term("id", String.valueOf(chunkId)));
        add(documentId, chunkId, parentId, text, pageFrom, pageTo, sectionPath);
        writer.commit();
        searcherManager.maybeRefreshBlocking();
    }

    private void add(long documentId, long chunkId, long parentId, String text, int pageFrom, int pageTo,
                     String sectionPath) throws IOException {
        Document document = new Document();
        document.add(new StringField("id", String.valueOf(chunkId), Field.Store.YES));
        document.add(new StringField("documentId", String.valueOf(documentId), Field.Store.YES));
        document.add(new StoredField("parentId", String.valueOf(parentId)));
        document.add(new StoredField("pageFrom", pageFrom));
        document.add(new StoredField("pageTo", pageTo));
        document.add(new StoredField("section", sectionPath));
        document.add(new TextField("text", text, Field.Store.NO));
        writer.addDocument(document);
    }

    public List<ChunkHit> search(String question, int topK, Long documentId) throws IOException {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        try (TokenStream stream = analyzer.tokenStream("text", question)) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                builder.add(new TermQuery(new Term("text", term.toString())), BooleanClause.Occur.SHOULD);
            }
            stream.end();
        }
        BooleanQuery terms = builder.build();
        if (terms.clauses().isEmpty()) {
            return List.of();
        }
        BooleanQuery query = terms;
        if (documentId != null) {
            query = new BooleanQuery.Builder()
                    .add(terms, BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term("documentId", String.valueOf(documentId))),
                            BooleanClause.Occur.FILTER)
                    .build();
        }
        IndexSearcher searcher = searcherManager.acquire();
        try {
            TopDocsResult result = new TopDocsResult();
            for (ScoreDoc scoreDoc : searcher.search(query, topK).scoreDocs) {
                Document document = searcher.storedFields().document(scoreDoc.doc);
                long chunkId = Long.parseLong(document.get("id"));
                long parentId = Long.parseLong(document.get("parentId"));
                result.hits.add(new ChunkHit(chunkId, parentId, scoreDoc.score));
            }
            return result.hits;
        } finally {
            searcherManager.release(searcher);
        }
    }

    public int documentCount() {
        return writer.getDocStats().numDocs;
    }

    private static final class TopDocsResult {
        private final List<ChunkHit> hits = new ArrayList<>();
    }
}
