package com.searchengine.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankFusionTest {

    private static List<TopDocs.ScoredDoc> ranking(int... ids) {
        return java.util.Arrays.stream(ids).mapToObj(id -> new TopDocs.ScoredDoc(id, 100 - id)).toList();
    }

    @Test
    void documentsRankedWellByBothListsWin() {
        List<TopDocs.ScoredDoc> fused = RankFusion.rrf(List.of(ranking(1, 2, 3), ranking(3, 1, 4)), 60, 10);
        // 1: 1/61 + 1/62 ; 3: 1/63 + 1/61 ; 2: 1/62 ; 4: 1/63
        assertEquals(List.of(1, 3, 2, 4), fused.stream().map(TopDocs.ScoredDoc::docId).toList());
        assertEquals(1.0 / 61 + 1.0 / 62, fused.get(0).score(), 1e-12);
    }

    @Test
    void limitAndEmptyInputs() {
        assertEquals(2, RankFusion.rrf(List.of(ranking(1, 2, 3)), 60, 2).size());
        assertEquals(0, RankFusion.rrf(List.of(List.of(), List.of()), 60, 5).size());
    }
}
