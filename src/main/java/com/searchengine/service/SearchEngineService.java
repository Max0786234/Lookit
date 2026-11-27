package com.searchengine.service;

import com.searchengine.analysis.Analyzer;
import com.searchengine.document.Document;
import com.searchengine.document.DocumentEntity;
import com.searchengine.document.DocumentRepository;
import com.searchengine.index.Fields;
import com.searchengine.index.MemoryIndex;
import com.searchengine.index.MutableIndex;
import com.searchengine.index.SegmentedIndex;
import com.searchengine.loader.DocumentLoaderService;
import com.searchengine.search.Bm25fSearcher;
import com.searchengine.search.SearchEngine;
import com.searchengine.vector.HnswIndex;
import com.searchengine.vector.OnnxEncoder;
import com.searchengine.vector.VectorStore;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Spring facade over {@link SearchEngine}. The database is the source of truth for documents; the
 * engine index is either in-memory (rebuilt from the DB at startup) or a persistent
 * {@link SegmentedIndex} in {@code lookit.index.dir}, reused when it matches the DB and rebuilt
 * when it does not (e.g. after a crash before the last commit).
 */
@Service
public class SearchEngineService {
    private static final Logger log = LoggerFactory.getLogger(SearchEngineService.class);
    static final String SAMPLE_DIRECTORY = "data/documents";

    private final DocumentRepository documentRepository;
    private final SearchEngine engine;
    private final AtomicBoolean dirty = new AtomicBoolean();

    private final Path vectorFile;
    private final Set<Integer> vectorized = ConcurrentHashMap.newKeySet();

    /** Keyword-only service (no embedding model). */
    public SearchEngineService(DocumentRepository documentRepository, DocumentLoaderService documentLoaderService,
                               String indexDir) {
        this(documentRepository, documentLoaderService, indexDir, "");
    }

    @Autowired
    public SearchEngineService(DocumentRepository documentRepository, DocumentLoaderService documentLoaderService,
                               @Value("${lookit.index.dir:}") String indexDir,
                               @Value("${lookit.vector.model-dir:}") String modelDir) {
        this.documentRepository = documentRepository;
        this.vectorFile = indexDir == null || indexDir.isBlank() ? null : Path.of(indexDir).resolve("vectors.vec");
        if (documentRepository.count() == 0) {
            int loaded = documentLoaderService.loadDocumentsFromDirectory(SAMPLE_DIRECTORY);
            log.info("Empty corpus: loaded {} sample documents from {}", loaded, SAMPLE_DIRECTORY);
        }
        MutableIndex index = openIndex(indexDir);
        this.engine = new SearchEngine(index, new Bm25fSearcher(index, SearchEngine.BM25F_K1,
                SearchEngine.defaultBm25fParams()));
        enableDense(modelDir);
        if (index.numDocs() != documentRepository.count()) {
            int added = reindexAllDocuments();
            log.info("Indexed {} documents from the database", added);
            commit();
        }
        backfillVectors();
        log.info("Index ready: {} documents, {} body terms", documentCount(), vocabularySize());
    }

    private MutableIndex openIndex(String indexDir) {
        if (indexDir == null || indexDir.isBlank()) {
            return new MemoryIndex(Analyzer.english());
        }
        try {
            Path dir = Path.of(indexDir);
            SegmentedIndex index = SegmentedIndex.open(dir, Analyzer.english());
            if (index.numDocs() == documentRepository.count()) {
                return index;
            }
            log.warn("Index at {} has {} docs but the database has {}; rebuilding",
                    dir, index.numDocs(), documentRepository.count());
            wipe(dir);
            return SegmentedIndex.open(dir, Analyzer.english());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void wipe(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.sorted(Comparator.reverseOrder()).toList()) {
                if (!p.equals(dir)) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }

    public SearchEngine engine() {
        return engine;
    }

    public SearchEngine.Results search(String query, int page, int size) {
        return engine.search(query, page, size);
    }

    public SearchEngine.Results search(String query, int page, int size, SearchEngine.Mode mode) {
        return engine.search(query, page, size, mode);
    }

    public List<String> suggest(String prefix, int limit) {
        return engine.complete(prefix, limit);
    }

    // ---- dense retrieval

    private void enableDense(String modelDir) {
        if (modelDir == null || modelDir.isBlank() || !Files.exists(Path.of(modelDir, "model.onnx"))) {
            log.info("Semantic search disabled (no model at '{}'; see scripts/fetch-model.sh)", modelDir);
            return;
        }
        try {
            OnnxEncoder encoder = new OnnxEncoder(Path.of(modelDir));
            HnswIndex vectors = new HnswIndex();
            engine.enableDense(encoder, vectors);
            if (vectorFile != null) {
                VectorStore.read(vectorFile, encoder.dimension()).forEach((id, v) -> {
                    if (engine.index().contains(id)) {
                        vectors.add(id, v);
                        vectorized.add(id);
                    }
                });
            }
            log.info("Semantic search enabled: {}-d embeddings, {} stored vectors", encoder.dimension(), vectorized.size());
        } catch (IOException e) {
            log.warn("Could not load embedding model from {}: {}", modelDir, e.toString());
        }
    }

    /** Embeds every indexed document that has no stored vector yet. */
    private void backfillVectors() {
        if (!engine.denseEnabled()) {
            return;
        }
        int added = 0;
        for (int id : engine.index().docIds()) {
            if (!vectorized.contains(id)) {
                Optional<Document> doc = engine.index().document(id);
                if (doc.isPresent()) {
                    indexDocument(doc.get());
                    added++;
                }
            }
        }
        if (added > 0) {
            log.info("Embedded {} documents", added);
        }
    }

    /** Adds to the engine, embedding and persisting the vector when semantic search is on. */
    private void indexDocument(Document document) {
        if (!engine.denseEnabled()) {
            engine.add(document, null);
            return;
        }
        float[] vector = engine.embed(document);
        engine.add(document, vector);
        vectorized.add(document.id());
        if (vectorFile != null) {
            try {
                VectorStore.append(vectorFile, vector.length, Map.of(document.id(), vector));
            } catch (IOException e) {
                log.warn("Could not persist vector for doc {}: {}", document.id(), e.toString());
            }
        }
    }

    public Optional<Document> getDocument(int id) {
        return engine.index().document(id);
    }

    public List<Document> getAllDocuments() {
        return Arrays.stream(engine.index().docIds()).mapToObj(this::getDocument).flatMap(Optional::stream).toList();
    }

    public int documentCount() {
        return engine.index().numDocs();
    }

    public int vocabularySize() {
        return engine.index().vocabularySize(Fields.BODY);
    }

    public long totalTermOccurrences() {
        return Fields.ALL.stream().mapToLong(f -> engine.index().totalFieldLength(f)).sum();
    }

    public Document addDocument(String title, String url, String content) {
        DocumentEntity saved = documentRepository.save(new DocumentEntity(title, url, content));
        Objects.requireNonNull(saved.getId(), "database did not assign an id");
        Document document = toDocument(saved);
        indexDocument(document);
        dirty.set(true);
        return document;
    }

    public boolean deleteDocument(int id) {
        if (!documentRepository.existsById(id)) {
            return false;
        }
        documentRepository.deleteById(id);
        dirty.set(true);
        return engine.delete(id);
    }

    /**
     * Adds every stored document that is not yet in the index (e.g. after a crawl wrote to the DB).
     *
     * @return number of documents added
     */
    public int reindexAllDocuments() {
        int added = 0;
        for (DocumentEntity entity : documentRepository.findAll()) {
            if (entity.getId() != null && !engine.index().contains(entity.getId())) {
                indexDocument(toDocument(entity));
                added++;
            }
        }
        if (added > 0) {
            dirty.set(true);
        }
        return added;
    }

    /** Persists the index if it is on disk and has changed. */
    @Scheduled(fixedDelayString = "${lookit.index.commit-interval-ms:10000}")
    public void commitIfDirty() {
        if (dirty.getAndSet(false)) {
            commit();
        }
    }

    @PreDestroy
    public void commit() {
        if (engine.index() instanceof SegmentedIndex segmented) {
            segmented.commit();
        }
    }

    private static Document toDocument(DocumentEntity entity) {
        return new Document(entity.getId(), entity.getTitle(), entity.getUrl(), entity.getContent());
    }
}
