package com.searchengine.index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Posting {
    private final int documentId;
    private final List<Integer> positions = new ArrayList<>();

    public Posting(int documentId) {
        this.documentId = documentId;
    }

    public int documentId() {
        return documentId;
    }

    public int frequency() {
        return positions.size();
    }

    public List<Integer> positions() {
        return Collections.unmodifiableList(positions);
    }

    public void addPosition(int position) {
        positions.add(position);
    }
}
