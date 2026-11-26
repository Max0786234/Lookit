package com.searchengine.search;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Small LRU cache for query results, keyed on (index generation, query).
 *
 * <p>Because the generation is part of the key, any index mutation implicitly invalidates every
 * cached entry — there is no eviction call to forget. Stale generations simply age out of the LRU.
 */
public final class QueryCache<V> {
    private record Key(long generation, String query) {
    }

    private final Map<Key, V> entries;

    public QueryCache(int capacity) {
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, V> eldest) {
                return size() > capacity;
            }
        };
    }

    public V get(long generation, String query, Supplier<V> compute) {
        Key key = new Key(generation, query);
        synchronized (entries) {
            V cached = entries.get(key);
            if (cached != null) {
                return cached;
            }
        }
        V value = compute.get();
        synchronized (entries) {
            entries.put(key, value);
        }
        return value;
    }

    public int size() {
        synchronized (entries) {
            return entries.size();
        }
    }
}
