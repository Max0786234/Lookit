package com.searchengine.query;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryCacheTest {

    @Test
    void sameGenerationHitsCacheNewGenerationRecomputes() {
        QueryCache<String> cache = new QueryCache<>(10);
        AtomicInteger computed = new AtomicInteger();

        cache.get(1, "java", () -> "r" + computed.incrementAndGet());
        cache.get(1, "java", () -> "r" + computed.incrementAndGet());
        assertEquals(1, computed.get());

        String fresh = cache.get(2, "java", () -> "r" + computed.incrementAndGet());
        assertEquals("r2", fresh);
    }

    @Test
    void evictsLeastRecentlyUsed() {
        QueryCache<String> cache = new QueryCache<>(2);
        cache.get(1, "a", () -> "a");
        cache.get(1, "b", () -> "b");
        cache.get(1, "c", () -> "c");
        assertEquals(2, cache.size());
    }
}
