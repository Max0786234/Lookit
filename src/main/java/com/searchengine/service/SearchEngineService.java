package com.searchengine.service;

import com.searchengine.document.Document;
import com.searchengine.document.DocumentEntity;
import com.searchengine.document.DocumentRepository;
import com.searchengine.index.InvertedIndex;
import com.searchengine.loader.DocumentLoaderService;
import com.searchengine.processor.TextProcessor;
import com.searchengine.query.SearchResult;
import com.searchengine.query.SearchService;
import com.searchengine.ranking.Bm25Ranker;
import com.searchengine.query.QueryCache;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SearchEngineService {
    private final SearchService searchService;
    private final InvertedIndex index;
    private final DocumentRepository documentRepository;
    private final DocumentLoaderService documentLoaderService;
    private final QueryCache<List<SearchResult>> queryCache = new QueryCache<>(512);

    public SearchEngineService(DocumentRepository documentRepository, DocumentLoaderService documentLoaderService) {
        this.documentRepository = documentRepository;
        this.documentLoaderService = documentLoaderService;
        TextProcessor textProcessor = new TextProcessor();
        this.index = new InvertedIndex(textProcessor);

        seedSampleDocuments();
        this.searchService = new SearchService(index, textProcessor, new Bm25Ranker());
    }

    private void seedSampleDocuments() {
        List<DocumentEntity> savedDocuments = documentRepository.findAll();
        if (!savedDocuments.isEmpty()) {
            savedDocuments.forEach(entity -> {
                Integer id = entity.getId();
                if (id == null) {
                    id = index.documentCount() + 1;
                    entity.setId(id);
                }
                index.addDocument(new Document(
                        id,
                        entity.getTitle(),
                        entity.getUrl(),
                        entity.getContent()
                ));
            });
            return;
        }

        // Try loading from data/documents directory
        int loaded = documentLoaderService.loadDocumentsFromDirectory("data/documents");
        if (loaded > 0) {
            System.out.println("Loaded " + loaded + " documents from data/documents/");
            // Reload from database after loading
            List<DocumentEntity> newDocuments = documentRepository.findAll();
            newDocuments.forEach(entity -> index.addDocument(new Document(
                    entity.getId(),
                    entity.getTitle(),
                    entity.getUrl(),
                    entity.getContent()
            )));
            return;
        }

        // Fallback to hardcoded samples if no files found
        System.out.println("No documents found. Seeding with default samples.");
        addDocument("Java Search Engine", "local://java-search-engine",
                "Java is a powerful language. This project builds a search engine using Java.");
        addDocument("Spring Boot Backend", "local://spring-boot-backend",
                "Spring Boot helps Java developers build backend APIs quickly.");
        addDocument("Information Retrieval Basics", "local://information-retrieval",
                "Search engines use an inverted index and ranking algorithms to retrieve documents.");
    }


    public List<SearchResult> search(String query) {
        String key = query.trim().toLowerCase(java.util.Locale.ROOT);
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

    public int totalTermOccurrences() {
        return index.getAllDocuments().stream()
                .mapToInt(document -> index.documentLength(document.id()))
                .sum();
    }

    public Document addDocument(String title, String url, String content) {
        DocumentEntity entity = new DocumentEntity(title, url, content);
        DocumentEntity saved = documentRepository.save(entity);
        if (saved == null) {
            saved = entity;
        }

        if (saved.getId() == null) {
            saved.setId(index.documentCount() + 1);
        }

        Document document = new Document(saved.getId(), title, url, content);
        index.addDocument(document);
        return document;
    }

    /**
     * Rebuild the in-memory index from all documents in the database.
     * Call this after adding documents via crawler or batch operations.
     */
    public void reindexAllDocuments() {
        // Load all documents from database and add to the existing index
        // This is idempotent - re-adding the same documents just updates their entries
        List<DocumentEntity> allDocs = documentRepository.findAll();
        int beforeCount = index.documentCount();

        for (DocumentEntity entity : allDocs) {
            Integer id = entity.getId();
            if (id == null) {
                id = index.documentCount() + 1;
                entity.setId(id);
            }
            if (index.getDocument(id).isEmpty()) {
                // Only add if not already in index
                Document document = new Document(id, entity.getTitle(), entity.getUrl(), entity.getContent());
                index.addDocument(document);
            }
        }
        
        int afterCount = index.documentCount();
        if (afterCount > beforeCount) {
            System.out.println("Reindexed: added " + (afterCount - beforeCount) + " new documents, total now: " + afterCount);
        }
    }
}
