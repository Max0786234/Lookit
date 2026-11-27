package com.searchengine.vector;

import com.searchengine.search.TopDocs;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HnswIndexTest {

    /** Gaussian clusters on the unit sphere: closer to real embeddings than uniform noise. */
    private static float[][] clustered(int n, int dim, int clusters, long seed) {
        Random r = new Random(seed);
        float[][] centres = new float[clusters][dim];
        for (float[] c : centres) {
            for (int d = 0; d < dim; d++) {
                c[d] = (float) r.nextGaussian();
            }
        }
        float[][] out = new float[n][dim];
        for (int i = 0; i < n; i++) {
            float[] c = centres[r.nextInt(clusters)];
            for (int d = 0; d < dim; d++) {
                out[i][d] = c[d] + (float) (0.35 * r.nextGaussian());
            }
            VectorMath.normalize(out[i]);
        }
        return out;
    }

    private static double recallAt10(VectorIndex approx, VectorIndex exact, float[][] queries) {
        double hits = 0;
        for (float[] q : queries) {
            Set<Integer> truth = new HashSet<>();
            exact.search(q, 10).forEach(d -> truth.add(d.docId()));
            for (TopDocs.ScoredDoc d : approx.search(q, 10)) {
                if (truth.contains(d.docId())) {
                    hits++;
                }
            }
        }
        return hits / (10.0 * queries.length);
    }

    @Test
    void recallAt10IsHighAgainstExactSearch() {
        // queries come from the same clusters as the data (held out), like real query embeddings
        float[][] all = clustered(5200, 48, 40, 1);
        float[][] data = java.util.Arrays.copyOfRange(all, 0, 5000);
        float[][] queries = java.util.Arrays.copyOfRange(all, 5000, 5200);
        HnswIndex hnsw = new HnswIndex();
        ExactVectorIndex exact = new ExactVectorIndex();
        for (int i = 0; i < data.length; i++) {
            hnsw.add(i, data[i]);
            exact.add(i, data[i]);
        }
        double recall = recallAt10(hnsw, exact, queries);
        assertTrue(recall >= 0.95, "recall@10 = " + recall);

        hnsw.setEfSearch(10);
        double lowEf = recallAt10(hnsw, exact, queries);
        assertTrue(lowEf <= recall, "smaller ef trades recall for speed");
    }

    @Test
    void resultsAreSortedCosinesAndSelfIsNearest() {
        float[][] data = clustered(1000, 16, 10, 3);
        HnswIndex hnsw = new HnswIndex();
        for (int i = 0; i < data.length; i++) {
            hnsw.add(i, data[i]);
        }
        List<TopDocs.ScoredDoc> top = hnsw.search(data[123], 5);
        assertEquals(123, top.get(0).docId());
        assertEquals(1.0, top.get(0).score(), 1e-5);
        for (int i = 1; i < top.size(); i++) {
            assertTrue(top.get(i - 1).score() >= top.get(i).score());
        }
    }

    @Test
    void deletesAndReplacementsAreNeverReturned() {
        float[][] data = clustered(800, 16, 8, 4);
        HnswIndex hnsw = new HnswIndex();
        for (int i = 0; i < data.length; i++) {
            hnsw.add(i, data[i]);
        }
        assertTrue(hnsw.delete(5));
        assertFalse(hnsw.delete(5));
        assertEquals(799, hnsw.size());
        assertTrue(hnsw.search(data[5], 10).stream().noneMatch(d -> d.docId() == 5));

        hnsw.add(7, data[300]); // doc 7 now has doc 300's vector
        List<TopDocs.ScoredDoc> near300 = hnsw.search(data[300], 2);
        assertTrue(near300.stream().anyMatch(d -> d.docId() == 7));
        assertEquals(1, hnsw.search(data[7], 50).stream().filter(d -> d.docId() == 7).count(), "no stale copy");
    }
}
