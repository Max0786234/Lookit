package com.searchengine.vector;

import java.util.List;

/** Maps texts to L2-normalised dense vectors, so cosine similarity is a dot product. */
public interface Encoder {

    int dimension();

    float[][] embed(List<String> texts);

    default float[] embed(String text) {
        return embed(List.of(text))[0];
    }
}
