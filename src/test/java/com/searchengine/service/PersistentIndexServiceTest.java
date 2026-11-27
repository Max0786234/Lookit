package com.searchengine.service;

import com.searchengine.document.DocumentEntity;
import com.searchengine.document.DocumentRepository;
import com.searchengine.index.SegmentedIndex;
import com.searchengine.loader.DocumentLoaderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class PersistentIndexServiceTest {

    @Autowired
    private DocumentRepository repository;

    @TempDir
    Path indexDir;

    @Test
    void indexIsPersistedReusedAndRebuiltWhenOutOfSync() {
        DocumentLoaderService loader = new DocumentLoaderService(repository);
        SearchEngineService first = new SearchEngineService(repository, loader, indexDir.toString());
        assertInstanceOf(SegmentedIndex.class, first.engine().index());
        first.addDocument("Quagga", "local://quagga", "quagga extinct zebra");
        first.commit();
        long docs = repository.count();
        assertEquals(docs, first.documentCount());
        assertTrue(Files.exists(indexDir.resolve("segments")));

        SearchEngineService reopened = new SearchEngineService(repository, loader, indexDir.toString());
        assertEquals(docs, reopened.documentCount());
        assertEquals(1, reopened.search("quagga", 1, 10).total());

        // a document written to the DB but never committed to the index forces a rebuild
        repository.save(new DocumentEntity("Narwhal", "local://narwhal", "narwhal tusk"));
        SearchEngineService rebuilt = new SearchEngineService(repository, loader, indexDir.toString());
        assertEquals(docs + 1, rebuilt.documentCount());
        assertEquals(1, rebuilt.search("narwhal", 1, 10).total());
    }
}
