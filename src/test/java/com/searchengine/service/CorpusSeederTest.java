package com.searchengine.service;

import com.searchengine.document.DocumentRepository;
import com.searchengine.loader.DocumentLoaderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class CorpusSeederTest {

    @Autowired
    private DocumentRepository repository;

    @Test
    void seedsOnceAndMakesTheCorpusSearchable() {
        String dir = "src/test/resources/beir/tiny";
        CorpusSeeder.seedIfNeeded(repository, dir);
        assertTrue(repository.existsByUrlStartingWith("beir://tiny/"));
        long afterFirst = repository.count();
        CorpusSeeder.seedIfNeeded(repository, dir);
        assertEquals(afterFirst, repository.count(), "second start must not duplicate the corpus");

        SearchEngineService service = new SearchEngineService(repository, new DocumentLoaderService(repository), "", "", dir);
        assertEquals("Inverted index", service.search("postings", 1, 10).hits().get(0).document().title());
    }

    @Test
    void missingDirectoryIsIgnored() {
        assertTrue(CorpusSeeder.seedIfNeeded(repository, "does/not/exist").isEmpty());
        assertTrue(CorpusSeeder.seedIfNeeded(repository, "").isEmpty());
    }
}
