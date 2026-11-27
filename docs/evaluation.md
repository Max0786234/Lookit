# Evaluation

All ranking changes are measured on [BEIR](https://github.com/beir-cellar/beir) **SciFact**
(5,183 scientific abstracts, 300 test queries with expert relevance judgments).

## Reproduce

```sh
scripts/fetch-beir.sh scifact       # or scripts\fetch-beir.ps1 scifact on Windows
mvn -q compile exec:java -Dexec.args="data/beir/scifact"
# with the Lucene reference systems (test classpath):
mvn -q test-compile exec:java -Dexec.classpathScope=test \
    -Dexec.mainClass=com.searchengine.eval.LuceneBaseline -Dexec.args="data/beir/scifact"
```

`EvalRunner <dataset> [out.md] [system,...]` runs the named systems registered in
`EvalRunner.SYSTEMS`.

## Metrics

- **nDCG@10**: graded gain, log2(rank+1) discount (trec_eval convention, as BEIR reports).
- **MRR@10**: reciprocal rank of the first relevant document.
- **Recall@100**: fraction of judged-relevant documents found in the top 100, which matters
  as the candidate pool for re-ranking and hybrid fusion.
- Latency: wall-clock per query on one thread after a 20-query warm-up, in-memory index.

## Results

### M2 baseline (2026-11-26)

| System | nDCG@10 | MRR@10 | Recall@100 | mean ms | p95 ms |
|---|---|---|---|---|---|
| tfidf | 0.5840 | 0.5520 | 0.8659 | 0.93 | 1.81 |
| bm25-nostem | 0.6379 | 0.6036 | 0.8959 | 0.62 | 1.29 |
| bm25 | 0.6675 | 0.6294 | 0.9109 | 0.67 | 1.44 |
| lucene-bm25-flat | 0.6826 | 0.6464 | 0.9282 | 3.56 | 6.73 |
| lucene-bm25-multifield | 0.6697 | 0.6347 | 0.9042 | 2.82 | 4.90 |

Systems: `bm25` = our engine, English analyzer (33 stopwords + Porter), BM25 k1=1.2 b=0.75 scored
per field (title, body) and summed. `lucene-*` = Lucene 9.12 `EnglishAnalyzer` + `BM25Similarity`
with the same parameters; *flat* indexes title+body as one field (Pyserini's BEIR setting, whose
published SciFact nDCG@10 is 0.679), *multifield* mirrors our two-field layout.

Observations:
- Our two-field BM25 is within 0.3% of Lucene's equivalent configuration (0.6675 vs 0.6697); the
  remaining gap is analyzer differences (Lucene's EnglishAnalyzer also applies possessive
  filtering and a slightly different Porter variant).
- Stemming is worth +0.030 nDCG@10; BM25's TF saturation and pivoted length normalisation are worth
  +0.084 over log-TF·IDF.
- **Flat beats multi-field** (0.6826 vs 0.6697): summing two separately saturated BM25 scores
  over-rewards a term that appears in both title and body. Fixing that (BM25F, M3) is the next
  measurable target.
- Lucene latency here includes classic query-parser and stored-field lookup per hit, so it is not a
  like-for-like speed comparison; M4 adds a proper benchmark.

### M3 — BM25F (2026-11-26)

Parameters tuned by grid search on the **train** split (809 queries, `eval/Bm25fTuner`):
k1 ∈ {0.6…1.5}, title weight ∈ {0.5…3}, b_title, b_body ∈ {0.3…0.9}. Best: k1 = 0.9,
title weight 2.0, b_title = 0.6, b_body = 0.9 (train nDCG@10 0.7065). Test results:

| System | nDCG@10 | MRR@10 | Recall@100 | mean ms | p95 ms |
|---|---|---|---|---|---|
| bm25 (per-field sum) | 0.6675 | 0.6294 | 0.9109 | 1.09 | 2.47 |
| bm25f-untuned (k1 1.2, w 1, b 0.75) | 0.6895 | 0.6517 | 0.9276 | 0.86 | 1.93 |
| **bm25f (tuned)** | **0.6922** | **0.6571** | 0.9280 | 0.77 | 1.65 |
| lucene-bm25-flat | 0.6826 | 0.6464 | 0.9282 | 3.87 | 7.36 |

- Combining field frequencies before saturation is worth +0.022 on its own; tuning adds +0.003.
  The structural fix matters far more than the parameters, which is reassuring about overfitting.
- BM25F is now the engine default (`SearchEngine.inMemory()`).

### Second dataset: FiQA (57,638 financial Q&A posts, 648 test queries)

| System | nDCG@10 | MRR@10 | Recall@100 |
|---|---|---|---|
| bm25 / bm25f-untuned | 0.2493 | 0.3087 | 0.5522 |
| bm25f (SciFact-tuned) | 0.2475 | 0.3019 | 0.5463 |
| lucene-bm25 (flat = multifield) | 0.2536 | 0.3089 | 0.5489 |

- FiQA documents have no titles, so BM25F reduces to BM25 and flat = multi-field.
- Our BM25 is within 1.7% of Lucene's nDCG@10 and has higher Recall@100 (published BEIR BM25: 0.236).
- The SciFact-tuned parameters (notably b_body = 0.9) transfer slightly negatively (−0.002):
  tuning is corpus-specific, which is why the untuned BM25F is kept as a registered system.

### M6 — Dense and hybrid retrieval (2026-11-27)

Encoder: sentence-transformers all-MiniLM-L6-v2 (384-d) on ONNX Runtime, Java WordPiece tokenizer,
mean pooling. Hybrid: Reciprocal Rank Fusion (k = 60) of the BM25F and HNSW top 100.
Latency includes encoding the query on CPU.

| System | SciFact nDCG@10 | SciFact R@100 | FiQA nDCG@10 | FiQA R@100 | FiQA mean ms |
|---|---|---|---|---|---|
| bm25f | 0.6914 | 0.9247 | 0.2475 | 0.5463 | 3.3 |
| dense-exact | 0.6451 | 0.9250 | **0.3687** | **0.7061** | 36.5 |
| dense-hnsw | 0.6451 | 0.9250 | 0.3652 | 0.7041 | 5.1 |
| hybrid-rrf | **0.7211** | **0.9543** | 0.3610 | 0.7040 | 9.9 |

- Both dense scores match the published all-MiniLM-L6-v2 BEIR numbers (SciFact 0.645, FiQA 0.369),
  which validates the tokenizer and pooling end to end.
- **The best system depends on the corpus.** On SciFact (technical claims, exact terminology) BM25F
  beats dense and fusion beats both (+0.030). On FiQA (informal financial questions, vocabulary
  mismatch) dense beats BM25F by +0.121 and equal-weight RRF lands slightly *below* dense alone,
  because the much weaker lexical ranking dilutes it. A weighted fusion or a learned router would
  address this; RRF was kept parameter-free on purpose (ADR-015).

#### HNSW recall vs exact search (real embeddings, k = 10, M = 16, efConstruction = 100)

| efSearch | SciFact recall@10 | SciFact ms | FiQA recall@10 | FiQA ms |
|---|---|---|---|---|
| exact | 1.000 | 1.889 | 1.000 | 23.383 |
| 10 | 0.931 | 0.108 | 0.891 | 0.181 |
| 20 | 0.966 | 0.161 | 0.948 | 0.295 |
| 40 | 0.986 | 0.250 | 0.979 | 0.479 |
| **64 (default)** | **0.994** | 0.353 | **0.990** | 0.598 |
| 100 | 0.998 | 0.486 | 0.995 | 0.841 |
| 200 | 1.000 | 0.766 | 0.998 | 1.477 |

At the default ef = 64, HNSW finds 99 % of the true top 10 while searching 39× faster than brute
force on FiQA (build: 185 s for 57k vectors, single-threaded). Timings exclude query encoding.
