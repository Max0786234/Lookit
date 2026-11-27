package com.searchengine.service;

import com.searchengine.document.Document;
import com.searchengine.loader.DocumentLoaderService;
import com.searchengine.search.SearchEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {"lookit.index.dir=", "lookit.vector.model-dir="})
@Import({SearchEngineService.class, DocumentLoaderService.class})
class SearchEngineServiceTest {

    @Autowired
    private SearchEngineService service;

    @Test
    void searchSeesDocumentsAddedAfterAPreviousIdenticalQuery() {
        String query = "zygomorphic";
        assertTrue(service.search(query, 1, 10).hits().isEmpty());

        Document added = service.addDocument("Flowers", "local://flowers", "zygomorphic flowers are bilateral");

        assertEquals(1, service.search(query, 1, 10).hits().size(), "cached empty result must not be served after an add");
        assertEquals(added.id(), service.search(query, 1, 10).hits().get(0).document().id());
    }

    @Test
    void deletedDocumentsDisappearFromResults() {
        Document added = service.addDocument("Tmp", "local://tmp", "quokka marsupial");
        assertEquals(1, service.search("quokka", 1, 10).hits().size());

        assertTrue(service.deleteDocument(added.id()));

        assertTrue(service.search("quokka", 1, 10).hits().isEmpty());
    }

    @Test
    void pagesSliceResultsAndCarrySnippets() {
        for (int i = 0; i < 15; i++) {
            service.addDocument("Wombat " + i, "local://w" + i, "the wombat number " + i + " digs burrows");
        }
        SearchEngine.Results first = service.search("wombat", 1, 10);
        SearchEngine.Results second = service.search("wombat", 2, 10);
        SearchEngine.Results beyond = service.search("wombat", 5, 10);

        assertEquals(15, first.total());
        assertEquals(10, first.hits().size());
        assertEquals(5, second.hits().size());
        assertTrue(beyond.hits().isEmpty());
        assertTrue(first.hits().get(0).snippet().text().contains("wombat"));
        assertEquals(1, first.hits().get(0).snippet().highlights().size());
    }

    @Test
    void idsAreAssignedByTheDatabaseAndUnique() {
        Document a = service.addDocument("A", "local://a", "alpha");
        Document b = service.addDocument("B", "local://b", "beta");
        assertTrue(a.id() != b.id());
    }
}
