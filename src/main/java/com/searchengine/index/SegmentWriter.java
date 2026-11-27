package com.searchengine.index;

import com.searchengine.document.Document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the live contents of any {@link IndexReader} as one immutable segment file. Used both to
 * flush the RAM buffer and to merge segments (the source is then a {@link MultiReader}), so merging
 * is a k-way postings merge with no re-analysis.
 *
 * <pre>
 * file     := header docStore postings metadata footer
 * header   := MAGIC:int VERSION:int
 * docStore := (title:str url:str content:str)*                    one entry per doc, ascending id
 * postings := per field, per term:
 *               df:vint numBlocks:vint (lastDocDelta:vint byteLen:vint)*numBlocks  skip table
 *               blocks of ≤128 docs: (docDelta:vint freq:vint posDelta:vint*freq)*
 * metadata := numDocs:vint (idDelta:vint)* (docOffsetDelta:vlong)*
 *             numFields:vint per field: name:str totalLength:vlong (length:vint)*numDocs
 *               numTerms:vint (term:str df:vint ttf:vlong maxTf:vint minLen:vint offsetDelta:vlong)*
 * footer   := metadataOffset:long MAGIC:int
 * </pre>
 */
public final class SegmentWriter {
    static final int MAGIC = 0x4C4B5347; // "LKSG"
    static final int VERSION = 1;
    static final int BLOCK_SIZE = 128;

    private SegmentWriter() {
    }

    /** Writes atomically: to a temp file first, then renamed into place. */
    public static void write(IndexReader source, Path file) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (ByteIO.Out out = new ByteIO.Out(Files.newOutputStream(tmp))) {
            writeTo(source, out);
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void writeTo(IndexReader source, ByteIO.Out out) throws IOException {
        out.writeInt(MAGIC);
        out.writeInt(VERSION);

        int[] ids = source.docIds();
        long[] docOffsets = new long[ids.length];
        for (int i = 0; i < ids.length; i++) {
            Document doc = source.document(ids[i]).orElseThrow();
            docOffsets[i] = out.position();
            out.writeString(nullToEmpty(doc.title()));
            out.writeString(nullToEmpty(doc.url()));
            out.writeString(nullToEmpty(doc.content()));
        }

        List<FieldMeta> fieldMetas = new ArrayList<>();
        for (String field : source.fields()) {
            FieldMeta fm = new FieldMeta(field);
            for (String term : source.termsWithPrefix(field, "", Integer.MAX_VALUE)) {
                TermMeta tm = writePostings(source, field, term, out);
                if (tm != null) {
                    fm.terms.add(tm);
                }
            }
            fieldMetas.add(fm);
        }

        long metadataOffset = out.position();
        out.writeVInt(ids.length);
        int prevId = -1;
        for (int id : ids) {
            out.writeVInt(id - prevId);
            prevId = id;
        }
        long prevOffset = 0;
        for (long off : docOffsets) {
            out.writeVLong(off - prevOffset);
            prevOffset = off;
        }
        out.writeVInt(fieldMetas.size());
        for (FieldMeta fm : fieldMetas) {
            out.writeString(fm.name);
            long total = 0;
            int[] lengths = new int[ids.length];
            for (int i = 0; i < ids.length; i++) {
                lengths[i] = source.fieldLength(fm.name, ids[i]);
                total += lengths[i];
            }
            out.writeVLong(total);
            for (int len : lengths) {
                out.writeVInt(len);
            }
            out.writeVInt(fm.terms.size());
            long prevPostings = 0;
            for (TermMeta tm : fm.terms) {
                out.writeString(tm.term);
                out.writeVInt(tm.df);
                out.writeVLong(tm.ttf);
                out.writeVInt(tm.maxTf);
                out.writeVInt(tm.minLen);
                out.writeVLong(tm.offset - prevPostings);
                prevPostings = tm.offset;
            }
        }
        out.writeLong(metadataOffset);
        out.writeInt(MAGIC);
    }

    /** @return term metadata, or null if no live document has the term */
    private static TermMeta writePostings(IndexReader source, String field, String term, ByteIO.Out out)
            throws IOException {
        PostingsEnum p = source.postings(field, term);
        List<byte[]> blocks = new ArrayList<>();
        List<Integer> blockLastDocs = new ArrayList<>();
        ByteArrayOutputStream blockBytes = new ByteArrayOutputStream();
        ByteIO.Out block = new ByteIO.Out(blockBytes);
        int df = 0;
        long ttf = 0;
        int maxTf = 0;
        int minLen = Integer.MAX_VALUE;
        int prevDoc = -1;
        int inBlock = 0;
        for (int doc = p.nextDoc(); doc != PostingsEnum.NO_MORE_DOCS; doc = p.nextDoc()) {
            int[] positions = p.positions();
            block.writeVInt(doc - prevDoc);
            block.writeVInt(positions.length);
            int prevPos = 0;
            for (int pos : positions) {
                block.writeVInt(pos - prevPos);
                prevPos = pos;
            }
            df++;
            ttf += positions.length;
            maxTf = Math.max(maxTf, positions.length);
            minLen = Math.min(minLen, source.fieldLength(field, doc));
            prevDoc = doc;
            if (++inBlock == BLOCK_SIZE) {
                block.close();
                blocks.add(blockBytes.toByteArray());
                blockLastDocs.add(doc);
                blockBytes = new ByteArrayOutputStream();
                block = new ByteIO.Out(blockBytes);
                inBlock = 0;
            }
        }
        block.close();
        if (inBlock > 0) {
            blocks.add(blockBytes.toByteArray());
            blockLastDocs.add(prevDoc);
        }
        if (df == 0) {
            return null;
        }
        long offset = out.position();
        out.writeVInt(df);
        out.writeVInt(blocks.size());
        int prevLast = -1;
        for (int b = 0; b < blocks.size(); b++) {
            out.writeVInt(blockLastDocs.get(b) - prevLast);
            out.writeVInt(blocks.get(b).length);
            prevLast = blockLastDocs.get(b);
        }
        for (byte[] bytes : blocks) {
            out.writeBytes(bytes);
        }
        return new TermMeta(term, df, ttf, maxTf, minLen, offset);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private record TermMeta(String term, int df, long ttf, int maxTf, int minLen, long offset) {
    }

    private static final class FieldMeta {
        final String name;
        final List<TermMeta> terms = new ArrayList<>();

        FieldMeta(String name) {
            this.name = name;
        }
    }
}
