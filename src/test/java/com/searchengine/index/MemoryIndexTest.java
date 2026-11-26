package com.searchengine.index;

import com.searchengine.analysis.Analyzer;
import com.searchengine.document.Document;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryIndexTest {

    private final MemoryIndex index = new MemoryIndex(Analyzer.english());

    @Test
    void fieldsAreIndexedSeparatelyWithPositionsAndLengths() {
        index.add(new Document(1, "Java Search Engine", "u", "Java search engine Java"));

        assertEquals(1, index.docFreq(Fields.TITLE, "java"));
        assertEquals(3, index.fieldLength(Fields.TITLE, 1));
        assertEquals(4, index.fieldLength(Fields.BODY, 1));
        PostingsEnum p = index.postings(Fields.BODY, "java");
        assertEquals(1, p.nextDoc());
        assertArrayEquals(new int[]{0, 3}, p.positions());
        assertEquals(2, index.totalTermFreq(Fields.BODY, "java"));
    }

    @Test
    void postingsAreDocOrderedEvenWhenAddedOutOfOrder() {
        for (int id : new int[]{5, 1, 9, 3}) {
            index.add(new Document(id, "t", "u", "shared"));
        }
        PostingsEnum p = index.postings(Fields.BODY, "share");
        List<Integer> seen = new ArrayList<>();
        for (int d = p.nextDoc(); d != PostingsEnum.NO_MORE_DOCS; d = p.nextDoc()) {
            seen.add(d);
        }
        assertEquals(List.of(1, 3, 5, 9), seen);
    }

    @Test
    void advanceFindsFirstDocAtOrAfterTarget() {
        for (int id = 0; id < 100; id += 3) {
            index.add(new Document(id, "t", "u", "x"));
        }
        PostingsEnum p = index.postings(Fields.BODY, "x");
        assertEquals(30, p.advance(29));
        assertEquals(30, p.docId());
        assertEquals(99, p.advance(98));
        assertEquals(PostingsEnum.NO_MORE_DOCS, p.advance(100));
    }

    @Test
    void deleteAndReplaceKeepStatisticsExact() {
        index.add(new Document(1, "a", "u", "alpha beta"));
        index.add(new Document(2, "b", "u", "beta"));
        long gen = index.generation();

        index.add(new Document(1, "a", "u", "gamma"));
        assertEquals(0, index.docFreq(Fields.BODY, "alpha"));
        assertEquals(1, index.docFreq(Fields.BODY, "beta"));
        assertEquals(2, index.totalFieldLength(Fields.BODY));

        assertTrue(index.delete(2));
        assertEquals(1, index.vocabularySize(Fields.BODY)); // only "gamma" left
        assertTrue(index.generation() > gen);
        assertArrayEquals(new int[]{1}, index.docIds());
    }

    @Test
    void prefixTermsComeFromTheSortedDictionary() {
        index.add(new Document(1, "t", "u", "search searcher seattle sea"));
        assertEquals(List.of("sea", "search", "searcher", "seattl"), index.termsWithPrefix(Fields.BODY, "sea", 10));
        assertEquals(List.of("sea"), index.termsWithPrefix(Fields.BODY, "sea", 1));
    }

    @Test
    void concurrentWritesAndReads() throws Exception {
        Thread writer = new Thread(() -> {
            for (int i = 0; i < 2000; i++) {
                index.add(new Document(i, "doc", "u", "shared term " + i));
            }
        });
        writer.start();
        while (writer.isAlive()) {
            PostingsEnum p = index.postings(Fields.BODY, "share");
            while (p.nextDoc() != PostingsEnum.NO_MORE_DOCS) {
                p.freq();
            }
            index.avgFieldLength(Fields.BODY);
        }
        writer.join();
        assertEquals(2000, index.docFreq(Fields.BODY, "share"));
    }
}
