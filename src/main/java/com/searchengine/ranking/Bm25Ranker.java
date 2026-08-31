package com.searchengine.ranking;

import com.searchengine.index.InvertedIndex;
import com.searchengine.index.Posting;

public class Bm25Ranker implements Ranker {
    private static final double K1 = 1.5;
    private static final double B = 0.75;

    @Override
    public double score(String term, Posting posting, InvertedIndex index) {
        double idf = idf(term, index);
        double termFrequency = posting.frequency();
        double documentLength = index.documentLength(posting.documentId());
        double averageDocumentLength = index.averageDocumentLength();

        if (averageDocumentLength == 0.0) {
            return 0.0;
        }

        double lengthNormalization = 1.0 - B + B * (documentLength / averageDocumentLength);
        double numerator = termFrequency * (K1 + 1.0);
        double denominator = termFrequency + K1 * lengthNormalization;

        return idf * (numerator / denominator);
    }

    private double idf(String term, InvertedIndex index) {
        double totalDocuments = index.documentCount();
        double documentsContainingTerm = index.documentFrequency(term);

        return Math.log(1.0 + (totalDocuments - documentsContainingTerm + 0.5) / (documentsContainingTerm + 0.5));
    }
}
