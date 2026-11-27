package com.searchengine.vector;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Append-only file of (doc id, vector) records so embeddings survive restarts — encoding is far
 * more expensive than rebuilding the HNSW graph from stored vectors.
 * Format: {@code dim:int} then repeated {@code id:int float[dim]}; a later record for the same id wins.
 */
public final class VectorStore {
    private VectorStore() {
    }

    public static Map<Integer, float[]> read(Path file, int dim) throws IOException {
        Map<Integer, float[]> out = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return out;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file), 1 << 16))) {
            int fileDim = in.readInt();
            if (fileDim != dim) {
                throw new IOException("vector file " + file + " has dimension " + fileDim + ", expected " + dim);
            }
            while (true) {
                int id;
                try {
                    id = in.readInt();
                } catch (EOFException e) {
                    break;
                }
                float[] v = new float[dim];
                try {
                    for (int d = 0; d < dim; d++) {
                        v[d] = in.readFloat();
                    }
                } catch (EOFException e) {
                    break; // torn final record from a crash: ignore it
                }
                out.put(id, v);
            }
        }
        return out;
    }

    public static synchronized void append(Path file, int dim, Map<Integer, float[]> vectors) throws IOException {
        boolean fresh = !Files.exists(file);
        if (!fresh && Files.size(file) == 0) {
            fresh = true;
        }
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND), 1 << 16))) {
            if (fresh) {
                out.writeInt(dim);
            }
            for (Map.Entry<Integer, float[]> e : vectors.entrySet()) {
                out.writeInt(e.getKey());
                for (float x : e.getValue()) {
                    out.writeFloat(x);
                }
            }
        }
    }
}
