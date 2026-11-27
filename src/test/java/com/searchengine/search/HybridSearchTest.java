package com.searchengine.search;

import com.searchengine.document.Document;
import com.searchengine.vector.Encoder;
import com.searchengine.vector.ExactVectorIndex;
import com.searchengine.vector.VectorMath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridSearchTest {

    /** Toy "semantic" encoder: synonyms share a dimension, so it matches meaning, not spelling. */
    static final class ConceptEncoder implements Encoder {
        private static final Map<String, Integer> CONCEPT = Map.of(
                "car", 0, "automobile", 0, "vehicle", 0,
                "cat", 1, "feline", 1, "kitten", 1,
                "price", 2, "cost", 2);

        @Override
        public int dimension() {
            return 4;
        }

        @Override
        public float[][] embed(List<String> texts) {
            float[][] out = new float[texts.size()][];
            for (int i = 0; i < texts.size(); i++) {
                float[] v = new float[4];
                v[3] = 0.01f;
                for (String w : texts.get(i).toLowerCase().split("\\W+")) {
                    Integer c = CONCEPT.get(w);
                    if (c != null) {
                        v[c] += 1;
                    }
                }
                out[i] = VectorMath.normalize(v);
            }
            return out;
        }
    }

    private static List<Integer> ids(SearchEngine e, String q, SearchEngine.Mode mode) {
        return e.topDocs(q, 10, mode).hits().stream().map(TopDocs.ScoredDoc::docId).toList();
    }

    @Test
    void semanticFindsSynonymsKeywordCannotAndHybridGetsBoth() {
        SearchEngine engine = SearchEngine.inMemory();
        engine.enableDense(new ConceptEncoder(), new ExactVectorIndex());
        engine.add(new Document(1, "Automobile prices", "u1", "the automobile market and its cost"));
        engine.add(new Document(2, "Car reviews", "u2", "a car review site"));
        engine.add(new Document(3, "Kittens", "u3", "feline care for kittens"));

        assertEquals(List.of(2), ids(engine, "car", SearchEngine.Mode.KEYWORD));
        List<Integer> semantic = ids(engine, "car", SearchEngine.Mode.SEMANTIC);
        assertTrue(semantic.indexOf(1) < semantic.indexOf(3) && semantic.indexOf(2) < semantic.indexOf(3),
                "automobile is semantically close to car: " + semantic);
        List<Integer> hybrid = ids(engine, "car", SearchEngine.Mode.HYBRID);
        assertEquals(2, hybrid.get(0), "matched by both retrievers");
        assertEquals(1, hybrid.get(1), "found only semantically, still ahead of the unrelated doc");
    }

    @Test
    void deletesReachTheVectorIndexAndKeywordOnlyEngineRejectsSemanticMode() {
        SearchEngine engine = SearchEngine.inMemory();
        assertThrows(IllegalArgumentException.class, () -> engine.topDocs("car", 5, SearchEngine.Mode.SEMANTIC));
        engine.enableDense(new ConceptEncoder(), new ExactVectorIndex());
        engine.add(new Document(1, "Car", "u1", "car"));
        engine.delete(1);
        assertTrue(ids(engine, "automobile", SearchEngine.Mode.SEMANTIC).isEmpty());
    }

    @Test
    void searchResultsCarryDidYouMean() {
        SearchEngine engine = SearchEngine.inMemory();
        engine.add(new Document(1, "Retrieval", "u1", "retrieval evaluation with relevance judgments"));
        assertEquals("retrieval evaluation", engine.search("retreival evaluation", 1, 10).didYouMean());
        assertEquals(null, engine.search("retrieval", 1, 10).didYouMean());
        assertEquals(List.of("relevance"), engine.complete("rel", 3));
    }
}
