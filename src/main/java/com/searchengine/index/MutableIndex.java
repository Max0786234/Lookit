package com.searchengine.index;

import com.searchengine.analysis.Analyzer;
import com.searchengine.document.Document;

/** An index that accepts writes. Adding an existing id replaces that document. */
public interface MutableIndex extends IndexReader {

    void add(Document document);

    boolean delete(int docId);

    Analyzer analyzer();
}
