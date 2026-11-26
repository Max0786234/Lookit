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
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentedIndexTest {

    @TempDir
    Path dir;

    private final Analyzer analyzer = Analyzer.english();

    private static Document doc(int id, String body) {
        return new Document(id, "title " + id, "u" + id, body);
    }

    @Test
    void committedDocumentsSurviveReopen() throws Exception {
        try (SegmentedIndex index = SegmentedIndex.open(dir, analyzer, 3, 100, 4)) {
            for (int i = 1; i <= 7; i++) {
                index.add(doc(i, "kiwi number " + i));
            }
            assertEquals(2, index.segmentCount(), "auto-flush every 3 docs");
            assertEquals(7, index.numDocs(), "buffered docs are searchable before flush");
        } // close() commits
        try (SegmentedIndex reopened = SegmentedIndex.open(dir, analyzer)) {
            assertEquals(7, reopened.numDocs());
            assertEquals(7, reopened.docFreq(Fields.BODY, "kiwi"));
            assertEquals("kiwi number 5", reopened.document(5).orElseThrow().content());
        }
    }

    @Test
    void uncommittedChangesAreNotVisibleAfterReopen() throws Exception {
        SegmentedIndex index = SegmentedIndex.open(dir, analyzer, 100, 100, 4);
        index.add(doc(1, "committed"));
        index.commit();
        index.add(doc(2, "lost"));
        // no commit: simulate a crash by opening a second instance on the same directory
        SegmentedIndex reopened = SegmentedIndex.open(dir, analyzer);
        assertArrayEquals(new int[]{1}, reopened.docIds());
    }

    @Test
    void replaceAndDeleteAcrossSegmentsArePersisted() throws Exception {
        try (SegmentedIndex index = SegmentedIndex.open(dir, analyzer, 2, 100, 4)) {
            index.add(doc(1, "apple"));
            index.add(doc(2, "banana"));
            index.add(doc(3, "cherry")); // segment 1 = {1,2}, buffer = {3}
            index.add(doc(1, "apricot")); // replaces doc 1 held in segment 1
            assertTrue(index.delete(2));
            assertFalse(index.delete(99));

            assertEquals(2, index.numDocs());
            assertEquals("apricot", index.document(1).orElseThrow().content());
            assertEquals(0, countPostings(index, "appl"));
            assertEquals(1, countPostings(index, "apricot"));
        }
        try (SegmentedIndex reopened = SegmentedIndex.open(dir, analyzer)) {
            assertArrayEquals(new int[]{1, 3}, reopened.docIds());
            assertEquals(0, countPostings(reopened, "banana"));
        }
    }

    @Test
    void mergesKeepSegmentCountBoundedAndDropTombstones() throws Exception {
        try (SegmentedIndex index = SegmentedIndex.open(dir, analyzer, 5, 3, 3)) {
            for (int i = 0; i < 100; i++) {
                index.add(doc(i, "merge test " + (i % 2 == 0 ? "even" : "odd")));
                if (i % 7 == 0) {
                    index.delete(i);
                }
            }
            index.flush();
            assertTrue(index.segmentCount() <= 3, "segments: " + index.segmentCount());
            index.forceMerge();
            assertEquals(1, index.segmentCount());
            assertEquals(index.numDocs(), index.maxDoc(), "merge purged the tombstones");
            assertEquals(100 - 15, index.numDocs());
        }
    }

    @Test
    void rankingMatchesAnEquivalentMemoryIndex() throws Exception {
        MemoryIndex reference = new MemoryIndex(analyzer);
        Random r = new Random(11);
        String[] words = "search engine index rank query field merge segment posting token".split(" ");
        try (SegmentedIndex index = SegmentedIndex.open(dir, analyzer, 37, 4, 3)) {
            for (int i = 0; i < 400; i++) {
                StringBuilder sb = new StringBuilder();
                for (int w = 0; w < 4 + r.nextInt(30); w++) {
                    sb.append(words[r.nextInt(words.length)]).append(' ');
                }
                Document d = doc(r.nextInt(300), sb.toString());
                index.add(d);
                reference.add(d);
            }
            index.forceMerge(); // statistics equal only once tombstones are purged
            QueryParser parser = new QueryParser(analyzer, Set.copyOf(Fields.ALL));
            for (String q : List.of("search engine", "\"merge segment\"", "+rank -token", "post*")) {
                List<TopDocs.ScoredDoc> a = search(reference, parser, q);
                List<TopDocs.ScoredDoc> b = search(index, parser, q);
                assertEquals(a.size(), b.size(), q);
                for (int i = 0; i < a.size(); i++) {
                    assertEquals(a.get(i).docId(), b.get(i).docId(), q);
                    assertEquals(a.get(i).score(), b.get(i).score(), 1e-9, q);
                }
            }
        }
    }

    private static List<TopDocs.ScoredDoc> search(IndexReader reader, QueryParser parser, String q) {
        return new Bm25fSearcher(reader, SearchEngine.BM25F_K1, SearchEngine.defaultBm25fParams())
                .search(parser.parse(q), 25).hits();
    }

    private static int countPostings(IndexReader index, String term) {
        PostingsEnum p = index.postings(Fields.BODY, term);
        int n = 0;
        while (p.nextDoc() != PostingsEnum.NO_MORE_DOCS) {
            n++;
        }
        return n;
    }
}
