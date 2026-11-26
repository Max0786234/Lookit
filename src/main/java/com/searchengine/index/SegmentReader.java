package com.searchengine.index;

import com.searchengine.document.Document;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only view of one segment file (format in {@link SegmentWriter}). The file is memory-mapped;
 * metadata (doc ids, field lengths, term dictionary) is decoded into arrays at open, postings and
 * stored documents are decoded lazily from the mapping.
 *
 * <p>Deletes are tombstones kept in a sidecar {@code .del} file; deleted documents still count in
 * {@link #maxDoc()} statistics until a merge rewrites the segment.
 */
public final class SegmentReader implements IndexReader {
    private final Path file;
    private final ByteBuffer data;
    private final int[] ids;
    private final long[] docOffsets;
    private final List<String> fields;
    private final Map<String, FieldInfo> fieldInfo = new LinkedHashMap<>();
    private final Set<Integer> deleted = ConcurrentHashMap.newKeySet();

    private SegmentReader(Path file, ByteBuffer data, int[] ids, long[] docOffsets) {
        this.file = file;
        this.data = data;
        this.ids = ids;
        this.docOffsets = docOffsets;
        this.fields = new ArrayList<>();
    }

    public static SegmentReader open(Path file) throws IOException {
        MappedByteBuffer map;
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            map = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
        }
        ByteIO.In in = new ByteIO.In(map, 0);
        if (in.readInt() != SegmentWriter.MAGIC || in.readInt() != SegmentWriter.VERSION) {
            throw new IOException("not a segment file: " + file);
        }
        in.seek(map.capacity() - 12L);
        long metadataOffset = in.readLong();
        if (in.readInt() != SegmentWriter.MAGIC) {
            throw new IOException("truncated segment (bad footer): " + file);
        }
        in.seek(metadataOffset);
        int n = in.readVInt();
        int[] ids = new int[n];
        int prev = -1;
        for (int i = 0; i < n; i++) {
            prev += in.readVInt();
            ids[i] = prev;
        }
        long[] offsets = new long[n];
        long off = 0;
        for (int i = 0; i < n; i++) {
            off += in.readVLong();
            offsets[i] = off;
        }
        SegmentReader reader = new SegmentReader(file, map, ids, offsets);
        int numFields = in.readVInt();
        for (int f = 0; f < numFields; f++) {
            String name = in.readString();
            long total = in.readVLong();
            int[] lengths = new int[n];
            for (int i = 0; i < n; i++) {
                lengths[i] = in.readVInt();
            }
            int numTerms = in.readVInt();
            String[] terms = new String[numTerms];
            int[] df = new int[numTerms];
            long[] ttf = new long[numTerms];
            int[] maxTf = new int[numTerms];
            int[] minLen = new int[numTerms];
            long[] postingsOffset = new long[numTerms];
            long p = 0;
            for (int t = 0; t < numTerms; t++) {
                terms[t] = in.readString();
                df[t] = in.readVInt();
                ttf[t] = in.readVLong();
                maxTf[t] = in.readVInt();
                minLen[t] = in.readVInt();
                p += in.readVLong();
                postingsOffset[t] = p;
            }
            reader.fields.add(name);
            reader.fieldInfo.put(name, new FieldInfo(total, lengths, terms, df, ttf, maxTf, minLen, postingsOffset));
        }
        Path del = deletesFile(file);
        if (Files.exists(del)) {
            for (String line : Files.readAllLines(del)) {
                if (!line.isBlank()) {
                    reader.deleted.add(Integer.parseInt(line.trim()));
                }
            }
        }
        return reader;
    }

    static Path deletesFile(Path segment) {
        return segment.resolveSibling(segment.getFileName() + ".del");
    }

    public Path file() {
        return file;
    }

    /** Marks {@code docId} deleted if it is live here. In-memory only until {@link #writeDeletes()}. */
    public boolean markDeleted(int docId) {
        return Arrays.binarySearch(ids, docId) >= 0 && deleted.add(docId);
    }

    public void writeDeletes() throws IOException {
        Path del = deletesFile(file);
        if (deleted.isEmpty()) {
            Files.deleteIfExists(del);
            return;
        }
        Path tmp = del.resolveSibling(del.getFileName() + ".tmp");
        StringBuilder sb = new StringBuilder();
        deleted.stream().sorted().forEach(id -> sb.append(id).append('\n'));
        Files.writeString(tmp, sb);
        Files.move(tmp, del, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public int deletedCount() {
        return deleted.size();
    }

    @Override
    public List<String> fields() {
        return fields;
    }

    @Override
    public int numDocs() {
        return ids.length - deleted.size();
    }

    @Override
    public int maxDoc() {
        return ids.length;
    }

    @Override
    public boolean contains(int docId) {
        return !deleted.contains(docId) && Arrays.binarySearch(ids, docId) >= 0;
    }

    @Override
    public Optional<Document> document(int docId) {
        int idx = Arrays.binarySearch(ids, docId);
        if (idx < 0 || deleted.contains(docId)) {
            return Optional.empty();
        }
        ByteIO.In in = new ByteIO.In(data, docOffsets[idx]);
        return Optional.of(new Document(docId, in.readString(), in.readString(), in.readString()));
    }

    @Override
    public int[] docIds() {
        if (deleted.isEmpty()) {
            return ids.clone();
        }
        return Arrays.stream(ids).filter(id -> !deleted.contains(id)).toArray();
    }

    @Override
    public int docFreq(String field, String term) {
        FieldInfo fi = fieldInfo.get(field);
        int t = fi == null ? -1 : fi.find(term);
        return t < 0 ? 0 : fi.df[t];
    }

    @Override
    public long totalTermFreq(String field, String term) {
        FieldInfo fi = fieldInfo.get(field);
        int t = fi == null ? -1 : fi.find(term);
        return t < 0 ? 0 : fi.ttf[t];
    }

    @Override
    public int maxTermFreq(String field, String term) {
        FieldInfo fi = fieldInfo.get(field);
        int t = fi == null ? -1 : fi.find(term);
        return t < 0 ? 0 : fi.maxTf[t];
    }

    @Override
    public int minFieldLength(String field, String term) {
        FieldInfo fi = fieldInfo.get(field);
        int t = fi == null ? -1 : fi.find(term);
        return t < 0 ? 0 : fi.minLen[t];
    }

    @Override
    public PostingsEnum postings(String field, String term) {
        FieldInfo fi = fieldInfo.get(field);
        int t = fi == null ? -1 : fi.find(term);
        if (t < 0) {
            return PostingsEnum.EMPTY;
        }
        return new SegmentPostingsEnum(new ByteIO.In(data, fi.postingsOffset[t]), deleted);
    }

    @Override
    public int fieldLength(String field, int docId) {
        FieldInfo fi = fieldInfo.get(field);
        int idx = Arrays.binarySearch(ids, docId);
        return fi == null || idx < 0 ? 0 : fi.lengths[idx];
    }

    @Override
    public long totalFieldLength(String field) {
        FieldInfo fi = fieldInfo.get(field);
        return fi == null ? 0 : fi.total;
    }

    @Override
    public List<String> termsWithPrefix(String field, String prefix, int limit) {
        FieldInfo fi = fieldInfo.get(field);
        List<String> out = new ArrayList<>();
        if (fi == null) {
            return out;
        }
        int i = Arrays.binarySearch(fi.terms, prefix);
        for (i = i < 0 ? -i - 1 : i; i < fi.terms.length && out.size() < limit && fi.terms[i].startsWith(prefix); i++) {
            out.add(fi.terms[i]);
        }
        return out;
    }

    @Override
    public int vocabularySize(String field) {
        FieldInfo fi = fieldInfo.get(field);
        return fi == null ? 0 : fi.terms.length;
    }

    @Override
    public long generation() {
        return deleted.size();
    }

    private record FieldInfo(long total, int[] lengths, String[] terms, int[] df, long[] ttf, int[] maxTf,
                             int[] minLen, long[] postingsOffset) {
        int find(String term) {
            return Arrays.binarySearch(terms, term);
        }
    }

    /** Decodes one term's postings; skips whole 128-doc blocks on {@link #advance}. */
    static final class SegmentPostingsEnum implements PostingsEnum {
        private final ByteIO.In in;
        private final Set<Integer> deleted;
        private final int df;
        private final int[] blockLastDoc;
        private final long[] blockStart;
        private int block;
        private int remainingInBlock;
        private int doc = -1;
        private int freq;
        private boolean positionsRead;
        private int[] positions;

        SegmentPostingsEnum(ByteIO.In in, Set<Integer> deleted) {
            this.in = in;
            this.deleted = deleted;
            this.df = in.readVInt();
            int blocks = in.readVInt();
            blockLastDoc = new int[blocks];
            long[] lengths = new long[blocks];
            int last = -1;
            for (int b = 0; b < blocks; b++) {
                last += in.readVInt();
                blockLastDoc[b] = last;
                lengths[b] = in.readVInt();
            }
            blockStart = new long[blocks];
            long pos = in.position();
            for (int b = 0; b < blocks; b++) {
                blockStart[b] = pos;
                pos += lengths[b];
            }
            block = -1;
            remainingInBlock = 0;
        }

        @Override
        public int docId() {
            return doc;
        }

        @Override
        public int nextDoc() {
            do {
                if (!readNext()) {
                    return doc = NO_MORE_DOCS;
                }
            } while (deleted.contains(doc));
            return doc;
        }

        private boolean readNext() {
            if (doc != -1 && !positionsRead) {
                for (int i = 0; i < freq; i++) {
                    in.readVInt();
                }
            }
            if (remainingInBlock == 0) {
                if (block + 1 >= blockLastDoc.length) {
                    return false;
                }
                enterBlock(block + 1);
            }
            doc += in.readVInt();
            freq = in.readVInt();
            positionsRead = false;
            remainingInBlock--;
            return true;
        }

        private void enterBlock(int b) {
            block = b;
            in.seek(blockStart[b]);
            doc = b == 0 ? -1 : blockLastDoc[b - 1];
            remainingInBlock = Math.min(SegmentWriter.BLOCK_SIZE, df - b * SegmentWriter.BLOCK_SIZE);
            positionsRead = true; // nothing pending at a block boundary
        }

        @Override
        public int advance(int target) {
            if (doc == NO_MORE_DOCS) {
                return doc;
            }
            int current = Math.max(block, 0);
            if (blockLastDoc.length > 0 && target > blockLastDoc[current]) {
                int b = Arrays.binarySearch(blockLastDoc, current, blockLastDoc.length, target);
                b = b < 0 ? -b - 1 : b;
                if (b >= blockLastDoc.length) {
                    return doc = NO_MORE_DOCS;
                }
                if (b != block) {
                    enterBlock(b);
                }
            }
            while (true) {
                int d = nextDoc();
                if (d >= target) {
                    return d;
                }
            }
        }

        @Override
        public int freq() {
            return freq;
        }

        @Override
        public int[] positions() {
            if (!positionsRead) {
                positions = new int[freq];
                int prev = 0;
                for (int i = 0; i < freq; i++) {
                    prev += in.readVInt();
                    positions[i] = prev;
                }
                positionsRead = true;
            }
            return positions;
        }

        @Override
        public long cost() {
            return df;
        }
    }
}
