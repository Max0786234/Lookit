package com.searchengine.vector;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sentence-transformers bi-encoder (e.g. all-MiniLM-L6-v2) on ONNX Runtime: WordPiece ids →
 * transformer → mean pooling over non-padding tokens → L2 normalisation, exactly as the
 * sentence-transformers pipeline does. Texts are batched and padded to the longest in the batch.
 *
 * <p>Expects a directory with {@code model.onnx} and {@code vocab.txt}.
 */
public final class OnnxEncoder implements Encoder, AutoCloseable {
    /** all-MiniLM-L6-v2 was trained with max_seq_length 256. */
    public static final int MAX_TOKENS = 256;
    private static final int BATCH = 32;

    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final OrtSession session;
    private final WordPieceTokenizer tokenizer;
    private final boolean needsTokenTypes;
    private final int dimension;

    public OnnxEncoder(Path modelDir) throws IOException {
        try {
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            options.setIntraOpNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
            this.session = env.createSession(modelDir.resolve("model.onnx").toString(), options);
            this.needsTokenTypes = session.getInputNames().contains("token_type_ids");
            this.tokenizer = WordPieceTokenizer.load(modelDir.resolve("vocab.txt"));
            this.dimension = embedBatch(List.of("probe"))[0].length;
        } catch (OrtException e) {
            throw new IOException("Cannot load ONNX model from " + modelDir, e);
        }
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public float[][] embed(List<String> texts) {
        float[][] out = new float[texts.size()][];
        try {
            for (int i = 0; i < texts.size(); i += BATCH) {
                float[][] batch = embedBatch(texts.subList(i, Math.min(texts.size(), i + BATCH)));
                System.arraycopy(batch, 0, out, i, batch.length);
            }
        } catch (OrtException e) {
            throw new IllegalStateException("Embedding failed", e);
        }
        return out;
    }

    private float[][] embedBatch(List<String> texts) throws OrtException {
        List<long[]> encoded = new ArrayList<>(texts.size());
        int maxLen = 0;
        for (String t : texts) {
            long[] ids = tokenizer.encode(t, MAX_TOKENS);
            encoded.add(ids);
            maxLen = Math.max(maxLen, ids.length);
        }
        int n = texts.size();
        long[] ids = new long[n * maxLen];
        long[] mask = new long[n * maxLen];
        for (int i = 0; i < n; i++) {
            long[] e = encoded.get(i);
            System.arraycopy(e, 0, ids, i * maxLen, e.length);
            for (int j = 0; j < e.length; j++) {
                mask[i * maxLen + j] = 1;
            }
        }
        long[] shape = {n, maxLen};
        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            inputs.put("input_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape));
            inputs.put("attention_mask", OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape));
            if (needsTokenTypes) {
                inputs.put("token_type_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(new long[n * maxLen]), shape));
            }
            try (OrtSession.Result result = session.run(inputs)) {
                float[][][] hidden = (float[][][]) result.get(0).getValue(); // [batch][seq][dim]
                float[][] pooled = new float[n][];
                for (int i = 0; i < n; i++) {
                    int dim = hidden[i][0].length;
                    float[] v = new float[dim];
                    int count = 0;
                    for (int j = 0; j < maxLen; j++) {
                        if (mask[i * maxLen + j] == 1) {
                            for (int d = 0; d < dim; d++) {
                                v[d] += hidden[i][j][d];
                            }
                            count++;
                        }
                    }
                    for (int d = 0; d < dim; d++) {
                        v[d] /= count;
                    }
                    pooled[i] = VectorMath.normalize(v);
                }
                return pooled;
            }
        } finally {
            inputs.values().forEach(OnnxTensor::close);
        }
    }

    @Override
    public void close() throws OrtException {
        session.close();
    }
}
