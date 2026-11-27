package com.searchengine.index;

import com.searchengine.analysis.Analyzer;
import com.searchengine.document.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Persistent index: a RAM buffer ({@link MemoryIndex}) in front of immutable on-disk segments.
 *
 * <ul>
 *   <li>Writes go to the buffer; a replaced or deleted document is tombstoned in whichever segment
 *       holds it.</li>
 *   <li>{@link #flush()} writes the buffer as a new segment; {@link #commit()} also persists
 *       tombstones and the manifest ({@code segments}), which is replaced atomically, so a crash
 *       leaves either the old or the new committed state.</li>
 *   <li>When there are more than {@code maxSegments} segments the smallest {@code mergeFactor} are
 *       merged by a postings-level k-way merge that drops tombstoned documents.</li>
 * </ul>
 *
 * <p>Readers see an immutable snapshot (list of segments + the buffer), swapped on flush/merge.
 * Writes are serialised.
 */
public final class SegmentedIndex implements MutableIndex, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SegmentedIndex.class);
    static final String MANIFEST = "segments";

    private final Path dir;
    private final Analyzer analyzer;
    private final int maxBufferedDocs;
    private final int maxSegments;
    private final int mergeFactor;

    private final List<SegmentReader> segments = new ArrayList<>();
    private MemoryIndex buffer;
    private int nextSegment;
    private volatile MultiReader snapshot;
    private volatile long generation;

    private SegmentedIndex(Path dir, Analyzer analyzer, int maxBufferedDocs, int maxSegments, int mergeFactor) {
        this.dir = dir;
        this.analyzer = analyzer;
        this.maxBufferedDocs = maxBufferedDocs;
        this.maxSegments = maxSegments;
        this.mergeFactor = mergeFactor;
        this.buffer = new MemoryIndex(analyzer);
    }

    public static SegmentedIndex open(Path dir, Analyzer analyzer) throws IOException {
        return open(dir, analyzer, 10_000, 8, 4);
    }

    /** Opens the last committed state in {@code dir} (creating it if needed). */
    public static SegmentedIndex open(Path dir, Analyzer analyzer, int maxBufferedDocs, int maxSegments,
                                      int mergeFactor) throws IOException {
        Files.createDirectories(dir);
        SegmentedIndex index = new SegmentedIndex(dir, analyzer, maxBufferedDocs, maxSegments, mergeFactor);
        Path manifest = dir.resolve(MANIFEST);
        if (Files.exists(manifest)) {
            List<String> lines = Files.readAllLines(manifest);
            index.nextSegment = Integer.parseInt(lines.get(0).trim());
            for (String name : lines.subList(1, lines.size())) {
                if (!name.isBlank()) {
                    index.segments.add(SegmentReader.open(dir.resolve(name.trim())));
                }
            }
        }
        index.deleteUnreferencedFiles();
        index.refresh();
        log.info("Opened index {}: {} segments, {} docs", dir, index.segments.size(), index.numDocs());
        return index;
    }

    // ---- writes

    @Override
    public synchronized void add(Document document) {
        for (SegmentReader s : segments) {
            s.markDeleted(document.id());
        }
        buffer.add(document);
        generation++;
        if (buffer.numDocs() >= maxBufferedDocs) {
            flush();
        }
    }

    @Override
    public synchronized boolean delete(int docId) {
        boolean removed = buffer.delete(docId);
        for (SegmentReader s : segments) {
            removed |= s.markDeleted(docId);
        }
        if (removed) {
            generation++;
        }
        return removed;
    }

    /** Writes the RAM buffer as a new segment (does not persist tombstones; see {@link #commit()}). */
    public synchronized void flush() {
        if (buffer.numDocs() == 0) {
            return;
        }
        try {
            Path file = dir.resolve(segmentName(nextSegment++));
            SegmentWriter.write(buffer, file);
            segments.add(SegmentReader.open(file));
            buffer = new MemoryIndex(analyzer);
            maybeMerge();
            refresh();
            log.debug("Flushed segment {} ({} segments)", file.getFileName(), segments.size());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Flush, persist tombstones, then atomically publish the manifest. */
    public synchronized void commit() {
        flush();
        try {
            for (SegmentReader s : segments) {
                s.writeDeletes();
            }
            StringBuilder sb = new StringBuilder().append(nextSegment).append('\n');
            for (SegmentReader s : segments) {
                sb.append(s.file().getFileName()).append('\n');
            }
            Path tmp = dir.resolve(MANIFEST + ".tmp");
            Files.writeString(tmp, sb);
            Files.move(tmp, dir.resolve(MANIFEST), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            deleteUnreferencedFiles();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Merges segments down to one (also purges every tombstone). */
    public synchronized void forceMerge() {
        flush();
        if (segments.size() > 1 || segments.stream().anyMatch(s -> s.deletedCount() > 0)) {
            mergeSegments(new ArrayList<>(segments));
            refresh();
        }
    }

    private void maybeMerge() throws IOException {
        while (segments.size() > maxSegments) {
            List<SegmentReader> smallest = segments.stream()
                    .sorted(Comparator.comparingInt(SegmentReader::numDocs))
                    .limit(mergeFactor)
                    .toList();
            mergeSegments(smallest);
        }
    }

    private void mergeSegments(List<SegmentReader> toMerge) {
        try {
            Path file = dir.resolve(segmentName(nextSegment++));
            SegmentWriter.write(new MultiReader(toMerge, 0), file);
            int at = segments.indexOf(toMerge.get(0));
            segments.removeAll(toMerge);
            segments.add(Math.min(at, segments.size()), SegmentReader.open(file));
            log.debug("Merged {} segments into {}", toMerge.size(), file.getFileName());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Removes segment files not referenced by the manifest. On Windows a still-mapped file cannot be
     * deleted; it is simply retried on the next commit or open.
     */
    private void deleteUnreferencedFiles() throws IOException {
        Set<String> live = segments.stream().map(s -> s.file().getFileName().toString()).collect(Collectors.toSet());
        Path manifest = dir.resolve(MANIFEST);
        if (Files.exists(manifest)) {
            Files.readAllLines(manifest).stream().skip(1).map(String::trim).forEach(live::add);
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.toList()) {
                String name = p.getFileName().toString();
                String base = name.endsWith(".del") ? name.substring(0, name.length() - 4) : name;
                if (base.startsWith("seg_") && !live.contains(base)) {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        log.debug("Could not delete {} yet: {}", p, e.toString());
                    }
                }
            }
        }
    }

    private static String segmentName(int n) {
        return String.format("seg_%06d.seg", n);
    }

    private void refresh() {
        List<IndexReader> readers = new ArrayList<>(segments);
        readers.add(buffer);
        snapshot = new MultiReader(readers, generation);
    }

    public synchronized int segmentCount() {
        return segments.size();
    }

    @Override
    public void close() {
        commit();
    }

    // ---- reads: delegate to the current snapshot (the buffer and tombstone sets are themselves thread-safe)

    @Override
    public Analyzer analyzer() {
        return analyzer;
    }

    private MultiReader r() {
        return snapshot;
    }

    @Override
    public List<String> fields() {
        return buffer.fields();
    }

    @Override
    public int numDocs() {
        return r().numDocs();
    }

    @Override
    public int maxDoc() {
        return r().maxDoc();
    }

    @Override
    public boolean contains(int docId) {
        return r().contains(docId);
    }

    @Override
    public Optional<Document> document(int docId) {
        return r().document(docId);
    }

    @Override
    public int[] docIds() {
        return r().docIds();
    }

    @Override
    public int docFreq(String field, String term) {
        return r().docFreq(field, term);
    }

    @Override
    public PostingsEnum postings(String field, String term) {
        return r().postings(field, term);
    }

    @Override
    public int fieldLength(String field, int docId) {
        return r().fieldLength(field, docId);
    }

    @Override
    public long totalFieldLength(String field) {
        return r().totalFieldLength(field);
    }

    @Override
    public long totalTermFreq(String field, String term) {
        return r().totalTermFreq(field, term);
    }

    @Override
    public int maxTermFreq(String field, String term) {
        return r().maxTermFreq(field, term);
    }

    @Override
    public int minFieldLength(String field, String term) {
        return r().minFieldLength(field, term);
    }

    @Override
    public List<String> termsWithPrefix(String field, String prefix, int limit) {
        return r().termsWithPrefix(field, prefix, limit);
    }

    @Override
    public int vocabularySize(String field) {
        return r().vocabularySize(field);
    }

    @Override
    public long generation() {
        return generation;
    }
}
