package com.searchengine.vector;

import com.searchengine.search.TopDocs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Hierarchical Navigable Small World graph (Malkov &amp; Yashunin, TPAMI 2018) for approximate
 * nearest-neighbour search over unit vectors (distance = 1 − cosine).
 *
 * <p>Each node gets a random top layer {@code ⌊−ln(U)·mL⌋}; a query descends greedily through the
 * sparse upper layers to a good entry point, then runs a best-first search with a dynamic candidate
 * list of size {@code ef} on layer 0. Neighbours are chosen with the paper's diversity heuristic
 * (Algorithm 4), which keeps the graph navigable across clusters. Deletes are tombstones: a deleted
 * node still routes searches but is never returned. Single writer, concurrent readers.
 */
public final class HnswIndex implements VectorIndex {
    private final int m;
    private final int m0;
    private final int efConstruction;
    private final double levelMultiplier;
    private final Random random;

    private final List<float[]> vectors = new ArrayList<>();
    private final List<int[][]> links = new ArrayList<>();   // links[node][layer] = neighbour ids
    private final List<int[]> linkCounts = new ArrayList<>(); // live length of each layer's array
    private final List<Integer> docIds = new ArrayList<>();
    private final Map<Integer, Integer> nodeOfDoc = new HashMap<>();
    private final BitSet deleted = new BitSet();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private int entryPoint = -1;
    private int maxLevel = -1;
    private int efSearch;

    /** Defaults from the paper / common practice: M = 16, efConstruction = 100, efSearch = 64. */
    public HnswIndex() {
        this(16, 100, 64, 42);
    }

    public HnswIndex(int m, int efConstruction, int efSearch, long seed) {
        this.m = m;
        this.m0 = 2 * m;
        this.efConstruction = efConstruction;
        this.efSearch = efSearch;
        this.levelMultiplier = 1 / Math.log(m);
        this.random = new Random(seed);
    }

    public void setEfSearch(int efSearch) {
        this.efSearch = efSearch;
    }

    private record Candidate(int node, float distance) {
    }

    private static final Comparator<Candidate> NEAREST_FIRST = Comparator.comparingDouble(Candidate::distance);
    private static final Comparator<Candidate> FARTHEST_FIRST = NEAREST_FIRST.reversed();

    private float distance(float[] q, int node) {
        return 1 - VectorMath.dot(q, vectors.get(node));
    }

    @Override
    public void add(int docId, float[] vector) {
        lock.writeLock().lock();
        try {
            Integer existing = nodeOfDoc.get(docId);
            if (existing != null) {
                deleted.set(existing); // replaced: tombstone the old node, insert a fresh one
            }
            int level = (int) Math.floor(-Math.log(1 - random.nextDouble()) * levelMultiplier);
            int node = vectors.size();
            vectors.add(vector);
            docIds.add(docId);
            nodeOfDoc.put(docId, node);
            int[][] layers = new int[level + 1][];
            for (int l = 0; l <= level; l++) {
                layers[l] = new int[(l == 0 ? m0 : m) + 1];
            }
            links.add(layers);
            linkCounts.add(new int[level + 1]);

            if (entryPoint < 0) {
                entryPoint = node;
                maxLevel = level;
                return;
            }
            int ep = entryPoint;
            for (int l = maxLevel; l > level; l--) {
                ep = greedyClosest(vector, ep, l);
            }
            List<Candidate> entry = List.of(new Candidate(ep, distance(vector, ep)));
            for (int l = Math.min(level, maxLevel); l >= 0; l--) {
                List<Candidate> found = searchLayer(vector, entry, efConstruction, l);
                int maxLinks = l == 0 ? m0 : m;
                List<Candidate> chosen = selectNeighbours(vector, found, maxLinks);
                for (Candidate c : chosen) {
                    connect(node, c.node(), l);
                    connect(c.node(), node, l);
                    if (linkCounts.get(c.node())[l] > maxLinks) {
                        shrink(c.node(), l, maxLinks);
                    }
                }
                entry = found;
            }
            if (level > maxLevel) {
                maxLevel = level;
                entryPoint = node;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void connect(int from, int to, int layer) {
        int[] counts = linkCounts.get(from);
        int[][] layers = links.get(from);
        if (counts[layer] == layers[layer].length) {
            layers[layer] = Arrays.copyOf(layers[layer], layers[layer].length * 2);
        }
        layers[layer][counts[layer]++] = to;
    }

    private void shrink(int node, int layer, int maxLinks) {
        float[] v = vectors.get(node);
        int count = linkCounts.get(node)[layer];
        int[] current = links.get(node)[layer];
        List<Candidate> candidates = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            candidates.add(new Candidate(current[i], distance(v, current[i])));
        }
        List<Candidate> kept = selectNeighbours(v, candidates, maxLinks);
        for (int i = 0; i < kept.size(); i++) {
            current[i] = kept.get(i).node();
        }
        linkCounts.get(node)[layer] = kept.size();
    }

    /**
     * Algorithm 4: take candidates nearest-first and keep one only if it is closer to the query than
     * to every neighbour already kept (so links point in diverse directions); top up with the
     * discarded ones to {@code max} ("keepPrunedConnections").
     */
    private List<Candidate> selectNeighbours(float[] q, List<Candidate> candidates, int max) {
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(NEAREST_FIRST);
        List<Candidate> kept = new ArrayList<>(max);
        List<Candidate> discarded = new ArrayList<>();
        for (Candidate c : sorted) {
            if (kept.size() >= max) {
                break;
            }
            boolean diverse = true;
            float[] cv = vectors.get(c.node());
            for (Candidate k : kept) {
                if (1 - VectorMath.dot(cv, vectors.get(k.node())) < c.distance()) {
                    diverse = false;
                    break;
                }
            }
            (diverse ? kept : discarded).add(c);
        }
        for (int i = 0; i < discarded.size() && kept.size() < max; i++) {
            kept.add(discarded.get(i));
        }
        return kept;
    }

    private int greedyClosest(float[] q, int start, int layer) {
        int best = start;
        float bestDist = distance(q, best);
        boolean improved = true;
        while (improved) {
            improved = false;
            int[] neighbours = links.get(best)[layer];
            int count = linkCounts.get(best)[layer];
            for (int i = 0; i < count; i++) {
                float d = distance(q, neighbours[i]);
                if (d < bestDist) {
                    bestDist = d;
                    best = neighbours[i];
                    improved = true;
                }
            }
        }
        return best;
    }

    /** Algorithm 2: best-first search keeping the {@code ef} closest nodes found. */
    private List<Candidate> searchLayer(float[] q, List<Candidate> entry, int ef, int layer) {
        BitSet visited = new BitSet(vectors.size());
        PriorityQueue<Candidate> candidates = new PriorityQueue<>(NEAREST_FIRST);
        PriorityQueue<Candidate> results = new PriorityQueue<>(FARTHEST_FIRST);
        for (Candidate c : entry) {
            visited.set(c.node());
            candidates.add(c);
            results.add(c);
        }
        while (results.size() > ef) {
            results.poll();
        }
        while (!candidates.isEmpty()) {
            Candidate c = candidates.poll();
            if (c.distance() > results.peek().distance() && results.size() >= ef) {
                break;
            }
            int[][] layers = links.get(c.node());
            if (layer >= layers.length) {
                continue;
            }
            int count = linkCounts.get(c.node())[layer];
            for (int i = 0; i < count; i++) {
                int n = layers[layer][i];
                if (visited.get(n)) {
                    continue;
                }
                visited.set(n);
                float d = distance(q, n);
                if (results.size() < ef || d < results.peek().distance()) {
                    Candidate nc = new Candidate(n, d);
                    candidates.add(nc);
                    results.add(nc);
                    if (results.size() > ef) {
                        results.poll();
                    }
                }
            }
        }
        return new ArrayList<>(results);
    }

    @Override
    public List<TopDocs.ScoredDoc> search(float[] query, int k) {
        lock.readLock().lock();
        try {
            if (entryPoint < 0) {
                return List.of();
            }
            int ep = entryPoint;
            for (int l = maxLevel; l > 0; l--) {
                ep = greedyClosest(query, ep, l);
            }
            // widen ef by the tombstone fraction so deleted nodes do not crowd out live results
            int ef = Math.max(efSearch, k) + deleted.cardinality() * Math.max(efSearch, k) / Math.max(1, vectors.size());
            List<Candidate> found = searchLayer(query, List.of(new Candidate(ep, distance(query, ep))), ef, 0);
            found.sort(NEAREST_FIRST);
            List<TopDocs.ScoredDoc> out = new ArrayList<>(k);
            for (Candidate c : found) {
                if (!deleted.get(c.node())) {
                    out.add(new TopDocs.ScoredDoc(docIds.get(c.node()), 1 - c.distance()));
                    if (out.size() == k) {
                        break;
                    }
                }
            }
            return out;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean delete(int docId) {
        lock.writeLock().lock();
        try {
            Integer node = nodeOfDoc.remove(docId);
            if (node == null) {
                return false;
            }
            deleted.set(node);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return nodeOfDoc.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
