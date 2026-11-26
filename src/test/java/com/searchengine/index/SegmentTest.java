package com.searchengine.index;

import com.searchengine.analysis.Analyzer;
import com.searchengine.document.Document;
import com.searchengine.search.Bm25fSearcher;
import com.searchengine.search.QueryParser;
import com.searchengine.search.SearchEngine;
import com.searchengine.search.TopDocs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentTest {

    @TempDir
    Path dir;

    private static final String[] WORDS = ("alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu "
            + "search engine index posting rank query token field segment merge").split(" ");

    static MemoryIndex randomIndex(int docs, long seed) {
        Random r = new Random(seed);
        MemoryIndex index = new MemoryIndex(Analyzer.english());
        for (int i = 0; i < docs; i++) {
            StringBuilder body = new StringBuilder();
            int len = 5 + r.nextInt(60);
            for (int w = 0; w < len; w++) {
                body.append(WORDS[r.nextInt(WORDS.length)]).append(' ');
            }
            int id = i * 3 + r.nextInt(3); // gaps in ids
            index.add(new Document(id, WORDS[r.nextInt(WORDS.length)] + " doc " + id, "u" + id, body.toString()));
        }
        return index;
    }

    @Test
    void roundTripPreservesDocumentsStatisticsAndPostings() throws Exception {
        MemoryIndex mem = randomIndex(700, 1); // > 5 postings blocks for common terms
        Path file = dir.resolve("seg.seg");
        SegmentWriter.write(mem, file);
        SegmentReader seg = SegmentReader.open(file);

        assertEquals(mem.numDocs(), seg.numDocs());
        assertArrayEquals(mem.docIds(), seg.docIds());
        for (int id : mem.docIds()) {
            assertEquals(mem.document(id), seg.document(id));
        }
        for (String field : mem.fields()) {
            assertEquals(mem.totalFieldLength(field), seg.totalFieldLength(field));
            assertEquals(mem.termsWithPrefix(field, "", 1000), seg.termsWithPrefix(field, "", 1000));
            for (String term : mem.termsWithPrefix(field, "", 1000)) {
                assertEquals(mem.docFreq(field, term), seg.docFreq(field, term), term);
                assertEquals(mem.totalTermFreq(field, term), seg.totalTermFreq(field, term));
                assertPostingsEqual(mem.postings(field, term), seg.postings(field, term));
            }
            for (int id : mem.docIds()) {
                assertEquals(mem.fieldLength(field, id), seg.fieldLength(field, id));
            }
        }
    }

    @Test
    void advanceSkipsBlocksCorrectly() throws Exception {
        MemoryIndex mem = new MemoryIndex(Analyzer.english());
        for (int i = 0; i < 1000; i++) {
            mem.add(new Document(i * 2, "t", "u", i % 3 == 0 ? "common rare" : "common"));
        }
        Path file = dir.resolve("skip.seg");
        SegmentWriter.write(mem, file);
        SegmentReader seg = SegmentReader.open(file);

        Random r = new Random(7);
        for (int trial = 0; trial < 200; trial++) {
            PostingsEnum a = mem.postings(Fields.BODY, "common");
            PostingsEnum b = seg.postings(Fields.BODY, "common");
            int target = 0;
            while (true) {
                target += 1 + r.nextInt(400);
                int x = a.advance(target);
                int y = b.advance(target);
                assertEquals(x, y, "advance(" + target + ")");
                if (x == PostingsEnum.NO_MORE_DOCS) {
                    break;
                }
                assertArrayEquals(a.positions(), b.positions());
            }
        }
    }

    @Test
    void deletesAreFilteredAndPersisted() throws Exception {
        MemoryIndex mem = randomIndex(50, 2);
        Path file = dir.resolve("del.seg");
        SegmentWriter.write(mem, file);
        SegmentReader seg = SegmentReader.open(file);
        int victim = mem.docIds()[10];

        assertTrue(seg.markDeleted(victim));
        assertFalse(seg.markDeleted(victim));
        assertFalse(seg.contains(victim));
        assertEquals(mem.numDocs() - 1, seg.numDocs());
        assertEquals(mem.numDocs(), seg.maxDoc(), "statistics still count the tombstone");
        for (String term : seg.termsWithPrefix(Fields.BODY, "", 100)) {
            PostingsEnum p = seg.postings(Fields.BODY, term);
            for (int d = p.nextDoc(); d != PostingsEnum.NO_MORE_DOCS; d = p.nextDoc()) {
                assertTrue(d != victim);
            }
        }
        seg.writeDeletes();
        assertFalse(SegmentReader.open(file).contains(victim));
    }

    @Test
    void searchResultsAreIdenticalOnSegmentAndMultiReader() throws Exception {
        MemoryIndex mem = randomIndex(400, 3);
        Path file = dir.resolve("search.seg");
        SegmentWriter.write(mem, file);
        SegmentReader seg = SegmentReader.open(file);

        // split the same docs across a segment and a RAM buffer
        MemoryIndex firstHalf = new MemoryIndex(Analyzer.english());
        MemoryIndex secondHalf = new MemoryIndex(Analyzer.english());
        int[] ids = mem.docIds();
        for (int i = 0; i < ids.length; i++) {
            (i % 2 == 0 ? firstHalf : secondHalf).add(mem.document(ids[i]).orElseThrow());
        }
        Path half = dir.resolve("half.seg");
        SegmentWriter.write(firstHalf, half);
        MultiReader multi = new MultiReader(List.of(SegmentReader.open(half), secondHalf), 0);

        QueryParser parser = new QueryParser(Analyzer.english(), Set.copyOf(Fields.ALL));
        for (String q : List.of("search engine", "\"posting rank\"", "+alpha -beta", "title:gamma", "seg*", "kappa OR mu")) {
            List<TopDocs.ScoredDoc> expected = top(mem, parser, q);
            assertEquals(expected, top(seg, parser, q), q);
            List<TopDocs.ScoredDoc> fromMulti = top(multi, parser, q);
            assertEquals(expected.size(), fromMulti.size(), q);
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(expected.get(i).docId(), fromMulti.get(i).docId(), q);
                assertEquals(expected.get(i).score(), fromMulti.get(i).score(), 1e-9, q);
            }
        }
    }

    private static List<TopDocs.ScoredDoc> top(IndexReader reader, QueryParser parser, String q) {
        Bm25fSearcher s = new Bm25fSearcher(reader, SearchEngine.BM25F_K1, SearchEngine.defaultBm25fParams());
        return new ArrayList<>(s.search(parser.parse(q), 20).hits());
    }

    private static void assertPostingsEqual(PostingsEnum a, PostingsEnum b) {
        while (true) {
            int x = a.nextDoc();
            assertEquals(x, b.nextDoc());
            if (x == PostingsEnum.NO_MORE_DOCS) {
                return;
            }
            assertEquals(a.freq(), b.freq());
            assertArrayEquals(a.positions(), b.positions());
        }
    }
}
