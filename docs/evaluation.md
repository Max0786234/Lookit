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
