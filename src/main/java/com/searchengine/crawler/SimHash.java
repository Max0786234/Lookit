package com.searchengine.crawler;

import com.searchengine.analysis.Analyzer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Charikar SimHash over word 3-shingles: similar texts get fingerprints a few bits apart, so
 * near-duplicates (same article with a different header, tracking parameters, timestamps) are
 * found by Hamming distance instead of exact hashing. Manku et al. (WWW 2007) use 64-bit
 * fingerprints with distance ≤ 3 for web-scale near-duplicate detection.
 */
public final class SimHash {
    private static final int SHINGLE = 3;
    private static final Analyzer ANALYZER = Analyzer.simple();

    private SimHash() {
    }

    public static long fingerprint(String text) {
        List<String> words = ANALYZER.terms(text);
        int[] votes = new int[64];
        int shingles = Math.max(1, words.size() - SHINGLE + 1);
        for (int i = 0; i < shingles; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < Math.min(words.size(), i + SHINGLE); j++) {
                sb.append(words.get(j)).append(' ');
            }
            long h = hash64(sb.toString());
            for (int bit = 0; bit < 64; bit++) {
                votes[bit] += ((h >>> bit) & 1) == 1 ? 1 : -1;
            }
        }
        long fp = 0;
        for (int bit = 0; bit < 64; bit++) {
            if (votes[bit] > 0) {
                fp |= 1L << bit;
            }
        }
        return fp;
    }

    public static int distance(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    /** FNV-1a then a SplitMix64 finaliser, so similar shingles get unrelated hashes. */
    static long hash64(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    /**
     * Finds fingerprints within {@code maxDistance} (≤ 3) bits. The 64 bits are split into 4 bands
     * of 16; by pigeonhole, two fingerprints differing in ≤ 3 bits agree exactly on at least one
     * band, so looking up each band finds every candidate without a full scan.
     */
    public static final class Index<T> {
        private static final int BANDS = 4;
        private final int maxDistance;
        private final List<Map<Integer, List<Entry<T>>>> bands = new ArrayList<>();

        private record Entry<T>(long fingerprint, T value) {
        }

        public Index(int maxDistance) {
            if (maxDistance > BANDS - 1) {
                throw new IllegalArgumentException("4 bands guarantee recall only up to distance 3");
            }
            this.maxDistance = maxDistance;
            for (int b = 0; b < BANDS; b++) {
                bands.add(new HashMap<>());
            }
        }

        public synchronized Optional<T> findNear(long fp) {
            for (int b = 0; b < BANDS; b++) {
                for (Entry<T> e : bands.get(b).getOrDefault(band(fp, b), List.of())) {
                    if (distance(e.fingerprint, fp) <= maxDistance) {
                        return Optional.of(e.value);
                    }
                }
            }
            return Optional.empty();
        }

        public synchronized void add(long fp, T value) {
            Entry<T> e = new Entry<>(fp, value);
            for (int b = 0; b < BANDS; b++) {
                bands.get(b).computeIfAbsent(band(fp, b), k -> new ArrayList<>()).add(e);
            }
        }

        private static int band(long fp, int b) {
            return (int) ((fp >>> (16 * b)) & 0xFFFF);
        }
    }
}
