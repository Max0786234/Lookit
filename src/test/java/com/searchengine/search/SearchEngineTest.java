package com.searchengine.search;

import com.searchengine.document.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchEngineTest {

    private static List<Integer> ids(SearchEngine e, String q) {
        return e.topDocs(q, 10).hits().stream().map(TopDocs.ScoredDoc::docId).toList();
    }

    @Test
    void pageRankPriorPromotesAuthoritativePageAmongEqualTextMatches() {
        SearchEngine engine = SearchEngine.inMemory();
        engine.add(new Document(1, "Jaguar", "u1", "jaguar cat habitat"));
        engine.add(new Document(2, "Jaguar", "u2", "jaguar cat habitat"));
        assertEquals(List.of(1, 2), ids(engine, "jaguar"), "equal text scores: lower id first");

        engine.setStaticPrior(Map.of(1, 0.5, 2, 4.0), 0.3);

        assertEquals(List.of(2, 1), ids(engine, "jaguar"), "cache must not serve the pre-prior ranking");
        double gap = engine.topDocs("jaguar", 10).hits().get(0).score() - engine.topDocs("jaguar", 10).hits().get(1).score();
        assertEquals(0.3 * (Math.log(4.0) - Math.log(0.5)), gap, 1e-9);
    }

    @Test
    void priorDoesNotOverrideAStrongTextMatch() {
        SearchEngine engine = SearchEngine.inMemory();
        engine.add(new Document(1, "Jaguar habitat", "u1", "jaguar habitat jaguar range jaguar diet"));
        engine.add(new Document(2, "Cars", "u2", "the jaguar car brand and many unrelated words about engines and roads"));
        engine.setStaticPrior(Map.of(2, 3.0), 0.3);
        assertEquals(1, ids(engine, "jaguar habitat").get(0));
        assertTrue(ids(engine, "jaguar habitat").contains(2));
    }
}
