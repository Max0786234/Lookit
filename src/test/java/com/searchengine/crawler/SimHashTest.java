package com.searchengine.crawler;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimHashTest {

    private static String article(Random r, int words) {
        String[] vocab = "search engine index crawler ranking query document term posting segment merge score"
                .split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words; i++) {
            sb.append(vocab[r.nextInt(vocab.length)]).append(i % 17 == 0 ? ". " : " ");
        }
        return sb.toString();
    }

    @Test
    void nearDuplicatesAreCloseUnrelatedTextsAreFar() {
        Random r = new Random(3);
        String base = article(r, 600);
        String tweaked = "Updated 27 Nov 2026 | Home | Login. " + base + " Share this page.";
        String other = article(r, 600);

        long a = SimHash.fingerprint(base);
        assertTrue(SimHash.distance(a, SimHash.fingerprint(tweaked)) <= 3,
                "distance " + SimHash.distance(a, SimHash.fingerprint(tweaked)));
        assertTrue(SimHash.distance(a, SimHash.fingerprint(other)) > 10);
        assertEquals(0, SimHash.distance(a, SimHash.fingerprint(base.toUpperCase())), "analyzer normalises case");
    }

    @Test
    void bandedIndexFindsEveryFingerprintWithinThreeBits() {
        Random r = new Random(9);
        SimHash.Index<Integer> index = new SimHash.Index<>(3);
        long[] fps = new long[2000];
        for (int i = 0; i < fps.length; i++) {
            fps[i] = r.nextLong();
            index.add(fps[i], i);
        }
        for (int i = 0; i < fps.length; i++) {
            long probe = fps[i];
            for (int flip = 0; flip < 3; flip++) {
                probe ^= 1L << r.nextInt(64);
            }
            assertTrue(index.findNear(probe).isPresent(), "missed a near duplicate");
        }
        assertTrue(index.findNear(fps[0] ^ 0xFFFFL ^ (0xFFFFL << 16) ^ (0xFFFFL << 32) ^ (0xFFFFL << 48)).isEmpty());
    }
}
