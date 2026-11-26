package com.searchengine.service;

import com.searchengine.document.Document;
import com.searchengine.document.DocumentEntity;
import com.searchengine.document.DocumentRepository;
import com.searchengine.index.Fields;
import com.searchengine.loader.DocumentLoaderService;
import com.searchengine.search.SearchEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Spring facade over {@link SearchEngine}: the database is the source of truth for documents,
 * the engine index is rebuilt from it at startup and kept in sync on every write.
 */
@Service
public class SearchEngineService {
    private static final Logger log = LoggerFactory.getLogger(SearchEngineService.class);
    static final String SAMPLE_DIRECTORY = "data/documents";

    private final SearchEngine engine = SearchEngine.inMemory();
    private final DocumentRepository documentRepository;

    public SearchEngineService(DocumentRepository documentRepository, DocumentLoaderService documentLoaderService) {
        this.documentRepository = documentRepository;
        if (documentRepository.count() == 0) {
            int loaded = documentLoaderService.loadDocumentsFromDirectory(SAMPLE_DIRECTORY);
            log.info("Empty corpus: loaded {} sample documents from {}", loaded, SAMPLE_DIRECTORY);
        }
        int indexed = reindexAllDocuments();
        log.info("Index ready: {} documents, {} body terms", indexed, vocabularySize());
    }

    public SearchEngine.Results search(String query, int page, int size) {
        return engine.search(query, page, size);
    }

    public Optional<Document> getDocument(int id) {
        return engine.index().document(id);
    }

    public List<Document> getAllDocuments() {
        int[] ids = engine.index().docIds();
        return java.util.Arrays.stream(ids).mapToObj(this::getDocument).flatMap(Optional::stream).toList();
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
        engine.add(document);
        return document;
    }

    public boolean deleteDocument(int id) {
        if (!documentRepository.existsById(id)) {
            return false;
        }
        documentRepository.deleteById(id);
        return engine.delete(id);
    }

    /**
     * Adds every stored document that is not yet in the index (e.g. after a crawl wrote to the DB).
     *
     * @return number of documents in the index afterwards
     */
    public int reindexAllDocuments() {
        int added = 0;
        for (DocumentEntity entity : documentRepository.findAll()) {
            if (entity.getId() != null && engine.index().document(entity.getId()).isEmpty()) {
                engine.add(toDocument(entity));
                added++;
            }
        }
        if (added > 0) {
            log.debug("Reindex added {} documents", added);
        }
        return engine.index().numDocs();
    }

    private static Document toDocument(DocumentEntity entity) {
        return new Document(entity.getId(), entity.getTitle(), entity.getUrl(), entity.getContent());
    }
}
