package com.searchengine.service;

import com.searchengine.document.Document;
import com.searchengine.document.DocumentEntity;
import com.searchengine.document.DocumentRepository;
import com.searchengine.index.InvertedIndex;
import com.searchengine.loader.DocumentLoaderService;
import com.searchengine.processor.TextProcessor;
import com.searchengine.query.QueryCache;
import com.searchengine.query.SearchResult;
import com.searchengine.query.SearchService;
import com.searchengine.ranking.Bm25Ranker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Spring facade over the engine: the database is the source of truth for documents, the
 * in-memory index is rebuilt from it at startup and kept in sync on every write.
 */
@Service
public class SearchEngineService {
    private static final Logger log = LoggerFactory.getLogger(SearchEngineService.class);
    static final String SAMPLE_DIRECTORY = "data/documents";

    private final InvertedIndex index;
    private final SearchService searchService;
    private final DocumentRepository documentRepository;
    private final QueryCache<List<SearchResult>> queryCache = new QueryCache<>(512);

    public SearchEngineService(DocumentRepository documentRepository, DocumentLoaderService documentLoaderService) {
        this.documentRepository = documentRepository;
        TextProcessor textProcessor = new TextProcessor();
        this.index = new InvertedIndex(textProcessor);
        this.searchService = new SearchService(index, textProcessor, new Bm25Ranker());

        if (documentRepository.count() == 0) {
            int loaded = documentLoaderService.loadDocumentsFromDirectory(SAMPLE_DIRECTORY);
            log.info("Empty corpus: loaded {} sample documents from {}", loaded, SAMPLE_DIRECTORY);
        }
        int indexed = reindexAllDocuments();
        log.info("Index ready: {} documents, {} terms", indexed, index.vocabularySize());
    }

    public List<SearchResult> search(String query) {
        String key = query.trim().toLowerCase(Locale.ROOT);
        return queryCache.get(index.generation(), key, () -> List.copyOf(searchService.search(query)));
    }

    public Optional<Document> getDocument(int id) {
        return index.getDocument(id);
    }

    public List<Document> getAllDocuments() {
        return index.getAllDocuments();
    }

    public int documentCount() {
        return index.documentCount();
    }

    public int vocabularySize() {
        return index.vocabularySize();
    }

    public long totalTermOccurrences() {
        return index.totalLength();
    }

    public Document addDocument(String title, String url, String content) {
        DocumentEntity saved = documentRepository.save(new DocumentEntity(title, url, content));
        Objects.requireNonNull(saved.getId(), "database did not assign an id");
        Document document = toDocument(saved);
        index.addDocument(document);
        return document;
    }

    public boolean deleteDocument(int id) {
        if (!documentRepository.existsById(id)) {
            return false;
        }
        documentRepository.deleteById(id);
        return index.removeDocument(id);
    }

    /**
     * Adds every stored document that is not yet in the index (e.g. after a crawl wrote to the DB).
     *
     * @return number of documents in the index afterwards
     */
    public int reindexAllDocuments() {
        int added = 0;
        for (DocumentEntity entity : documentRepository.findAll()) {
            if (entity.getId() != null && index.getDocument(entity.getId()).isEmpty()) {
                index.addDocument(toDocument(entity));
                added++;
            }
        }
        if (added > 0) {
            log.debug("Reindex added {} documents", added);
        }
        return index.documentCount();
    }

    private static Document toDocument(DocumentEntity entity) {
        return new Document(entity.getId(), entity.getTitle(), entity.getUrl(), entity.getContent());
    }
}
