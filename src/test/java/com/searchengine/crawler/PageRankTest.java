package com.searchengine.crawler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageRankTest {

    private static PageRank.Edge e(int from, int to) {
        return new PageRank.Edge(from, to);
    }

    @Test
    void symmetricCycleGivesUniformRank() {
        PageRank.Result r = PageRank.compute(new int[]{1, 2, 3}, List.of(e(1, 2), e(2, 3), e(3, 1)), 1e-10, 100);
        for (int id : new int[]{1, 2, 3}) {
            assertEquals(1.0 / 3, r.scores().get(id), 1e-9);
            assertEquals(1.0, r.normalized(id), 1e-9);
        }
    }

    @Test
    void matchesHandComputedFixedPoint() {
        // A→B, A→C, B→C, C→A (classic 3-page example); solve PR = (1-d)/3 + d·M·PR exactly
        PageRank.Result r = PageRank.compute(new int[]{0, 1, 2}, List.of(e(0, 1), e(0, 2), e(1, 2), e(2, 0)), 1e-12, 500);
        double d = PageRank.DAMPING;
        double base = (1 - d) / 3;
        // a = base + d·c ; b = base + d·a/2 ; c = base + d·(a/2 + b)
        double a = r.scores().get(0);
        double b = r.scores().get(1);
        double c = r.scores().get(2);
        assertEquals(base + d * c, a, 1e-9);
        assertEquals(base + d * a / 2, b, 1e-9);
        assertEquals(base + d * (a / 2 + b), c, 1e-9);
        assertEquals(1.0, a + b + c, 1e-9);
        assertTrue(c > a && a > b);
    }

    @Test
    void danglingMassIsRedistributedAndHubsWin() {
        // 1..5 all link to hub 0; 0 links nowhere (dangling); 6 is isolated
        PageRank.Result r = PageRank.compute(new int[]{0, 1, 2, 3, 4, 5, 6},
                List.of(e(1, 0), e(2, 0), e(3, 0), e(4, 0), e(5, 0), e(5, 0), e(0, 0), e(9, 0)), 1e-12, 500);
        double total = r.scores().values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, total, 1e-9, "still a probability distribution");
        assertTrue(r.normalized(0) > 3, "hub collects rank: " + r.normalized(0));
        assertEquals(r.scores().get(1), r.scores().get(6), 1e-12, "no in-links: same as isolated page");
    }
}
