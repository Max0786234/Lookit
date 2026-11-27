package com.searchengine.search;

import com.searchengine.analysis.Analyzer;
import com.searchengine.document.Document;
import com.searchengine.index.Fields;
import com.searchengine.index.MemoryIndex;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WandTest {

    @Test
    void wandReturnsExactlyTheExhaustiveTopK() {
        Random r = new Random(42);
        // Zipf-ish vocabulary: a few very common words and a long tail
        String[] vocab = new String[300];
        for (int i = 0; i < vocab.length; i++) {
            vocab[i] = "w" + i;
        }
        MemoryIndex index = new MemoryIndex(Analyzer.simple());
        for (int d = 0; d < 3000; d++) {
            StringBuilder body = new StringBuilder();
            for (int w = 0; w < 10 + r.nextInt(80); w++) {
                body.append(vocab[(int) Math.min(vocab.length - 1, Math.abs(r.nextGaussian()) * 40)]).append(' ');
            }
            index.add(new Document(d, vocab[r.nextInt(vocab.length)], "u", body.toString()));
        }
        QueryParser parser = new QueryParser(Analyzer.simple(), Set.copyOf(Fields.ALL));
        List<Searcher> searchers = List.of(
                new Bm25fSearcher(index, 0.9, SearchEngine.defaultBm25fParams()),
                new Searcher(index, new Bm25Similarity(), Map.of(Fields.TITLE, 2.0, Fields.BODY, 1.0)));

        int pruned = 0;
        for (Searcher s : searchers) {
            for (int q = 0; q < 200; q++) {
                StringBuilder query = new StringBuilder();
                for (int t = 0; t < 1 + r.nextInt(6); t++) {
                    query.append(vocab[r.nextInt(120)]).append(' ');
                }
                Query parsed = parser.parse(query.toString());
                for (int k : new int[]{1, 10, 100}) {
                    TopDocs exact = s.searchExhaustive(parsed, k);
                    TopDocs wand = s.search(parsed, k);
                    assertEquals(exact.hits().size(), wand.hits().size(), query.toString());
                    for (int i = 0; i < exact.hits().size(); i++) {
                        assertEquals(exact.hits().get(i).docId(), wand.hits().get(i).docId(), query + " k=" + k);
                        assertEquals(exact.hits().get(i).score(), wand.hits().get(i).score(), 1e-9);
                    }
                    assertTrue(wand.totalHits() <= exact.totalHits());
                    if (wand.totalHits() < exact.totalHits()) {
                        pruned++;
                    }
                }
            }
        }
        assertTrue(pruned > 100, "WAND should actually skip documents; pruned queries: " + pruned);
    }
}
