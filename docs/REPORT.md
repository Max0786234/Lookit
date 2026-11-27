# Lookit: a search engine from first principles

## 1. Goal

Lookit started as a small educational prototype: a HashMap inverted index, a regex tokenizer,
BM25 recomputing collection statistics per posting, and a crawler that blocked an HTTP request. The
aim of this work was to turn it into a real search engine whose every component is implemented in
the project, while keeping each claim about quality or speed *measured* against public benchmarks
and a reference implementation (Apache Lucene, used only in tests).

The work was organised in eight milestones (M0–M7), ordered so that measurement came before the
expensive internals:

| Milestone | Content |
|---|---|
| M0 Correctness | stale cache, O(N) statistics, thread safety, id collisions, API errors, paging |
| M1 Index seam + analysis | `IndexReader` interface, Unicode analyzer, Porter stemmer, fielded index, query AST |
| M2 Evaluation | BEIR loader, nDCG/MRR/Recall, CLI runner, Lucene baseline |
| M3 Ranking + queries | BM25F tuned on a train split, query language |
| M4 On-disk index | memory-mapped segments, merges, persistence, WAND |
| M5 Crawler | polite frontier, robots.txt, SimHash, PageRank |
| M6 Semantic search | MiniLM encoder, HNSW, hybrid RRF, spelling, autocomplete |
| M7 Polish | metrics, OpenAPI, Docker, UI |

## 2. Architecture

The engine (`analysis`, `index`, `search`, `vector`, `crawler`, `eval`) has no Spring dependency;
Spring Boot provides the REST API and persistence. The database is the source of truth for
documents; the index is derived from it and rebuilt automatically if the two disagree.

- **Analysis.** Unicode word segmentation with character offsets, Latin diacritic folding (other
  scripts untouched), CJK unigrams, a 33-word stopword list that leaves position gaps so phrases stay
  exact, and the Porter stemmer validated against the reference vocabulary.
- **Index.** Title and body are separate positional fields. In memory, postings are doc-ordered
  arrays. On disk, segments are immutable files: stored documents, per-field lengths, a sorted term
  dictionary with document/collection frequencies and WAND bounds, and delta + varint compressed
  postings with a skip entry every 128 documents. New documents go to a RAM buffer that is flushed to
  a segment; deletes are tombstones; segments are merged by a k-way postings merge (no re-analysis);
  commits swap a manifest atomically.
- **Search.** A query language (boolean operators, phrases, fields, prefixes) compiles to an AST and
  then to doc-at-a-time scorers (leapfrog conjunction, disjunction, exclusion, positional phrase).
  Results are collected in a bounded heap; pure disjunctions use WAND.
- **Ranking.** BM25F combines field frequencies before saturation. A PageRank prior and dense
  retrieval are fused on top.
- **Semantic.** all-MiniLM-L6-v2 runs in-process on ONNX Runtime with a Java port of BERT's
  WordPiece tokenizer; vectors go into a hand-written HNSW graph; keyword and semantic rankings are
  merged by Reciprocal Rank Fusion.
- **Crawler.** A frontier with one queue per host served by earliest allowed time, robots.txt per
  RFC 9309, SimHash near-duplicate detection, link storage and PageRank, run as asynchronous jobs.

## 3. Evaluation method

Quality is measured on BEIR SciFact (5,183 abstracts, 300 test queries) and FiQA (57,638 posts,
648 test queries) with nDCG@10 (trec_eval conventions), MRR@10 and Recall@100. Parameters are tuned
only on SciFact's *train* split (809 queries) and reported on test. Every system is a named entry in
`EvalRunner`, so any row of any table can be regenerated with one command.

## 4. Results

### 4.1 Quality

| System | SciFact nDCG@10 | SciFact R@100 | FiQA nDCG@10 |
|---|---|---|---|
| TF-IDF | 0.584 | 0.866 | — |
| BM25 without stemming | 0.638 | 0.896 | — |
| BM25, per-field sum | 0.668 | 0.911 | 0.249 |
| Lucene 9.12 BM25, single field | 0.683 | 0.928 | 0.254 |
| BM25F untuned | 0.690 | 0.928 | 0.249 |
| **BM25F tuned** (with query parser) | **0.691** | 0.925 | 0.248 |
| Dense (MiniLM, exact / HNSW) | 0.645 | 0.925 | **0.369** / 0.365 |
| **Hybrid RRF** | **0.721** | **0.954** | 0.361 |

Findings:

1. **Each classical component pays for itself.** Stemming adds 0.030; BM25's saturation and length
   normalisation add 0.084 over TF-IDF.
2. **The structural fix mattered more than the tuning.** Summing per-field BM25 saturates a term once
   per field. BM25F (one saturation) gains 0.022 untuned; tuning adds 0.003, and the SciFact-tuned
   parameters transfer slightly negatively to FiQA (−0.002). Tuning is corpus-specific.
3. **The dense pipeline is correct.** The Java tokenizer + ONNX model reproduce the published
   all-MiniLM-L6-v2 SciFact score (0.645), which validates tokenisation and pooling end to end.
4. **Hybrid beats both on SciFact, but not on FiQA.** On SciFact the dense model alone is below
   BM25F, yet fusing the two improves nDCG@10 by 0.030 and Recall@100 by 0.030: the retrievers make
   different mistakes. On FiQA, whose informal questions rarely share words with the answers, dense
   retrieval beats BM25F by 0.121 and equal-weight RRF (0.361) lands just below dense alone (0.369):
   a much weaker ranking dilutes a strong one. The best configuration is corpus-dependent, which
   argues for a weighted or learned fusion in future work.
5. **Evaluation caught real bugs.** Diffing the query parser against bag-of-words on all test queries
   found that stray punctuation like `(+)-` was parsed as a NOT operator, and that strict phrases for
   hyphenated words cost 1.8 points of recall.

### 4.2 Performance (FiQA, single thread, k = 10)

| | mean ms | p95 ms | QPS | docs scored / query |
|---|---|---|---|---|
| in-memory, exhaustive | 3.69 | 8.05 | 271 | 28,444 |
| in-memory, WAND | 1.96 | 5.00 | 510 | 1,294 |
| mmap segment, exhaustive | 7.44 | 15.74 | 134 | 28,444 |
| mmap segment, WAND | 2.65 | 6.30 | 378 | 1,294 |

WAND scores 22× fewer documents with an identical top 10 (checked by a randomised test against
exhaustive evaluation). Four threads reach 859 QPS on the memory-mapped index. Indexing runs at
~10k docs/s in memory and ~7.5k docs/s with segment flushes; the on-disk index is 138 % of the raw
text because it also stores every document for snippets.

### 4.3 Approximate nearest neighbours

HNSW (M = 16, efConstruction = 100) against exact search on the real embeddings, k = 10:

| efSearch | SciFact recall@10 | FiQA recall@10 | FiQA ms / query |
|---|---|---|---|
| exact | 1.000 | 1.000 | 23.38 |
| 20 | 0.966 | 0.948 | 0.30 |
| **64 (default)** | **0.994** | **0.990** | 0.60 |
| 200 | 1.000 | 0.998 | 1.48 |

At the default setting HNSW returns 99 % of the true top 10 at 39× the speed of brute force on
57k vectors; the nDCG difference between exact and HNSW retrieval is 0.0035 on FiQA and zero on
SciFact.

## 5. Engineering

- **Tests.** About 200 tests, including randomised equivalence tests (segment vs memory index,
  WAND vs exhaustive, HNSW vs exact), a fake web with a fake clock for crawler politeness, and
  analytic checks (PageRank fixed point, BM25F formula, IR metrics by hand).
- **Correctness under concurrency.** Read/write locking with snapshot readers lets a crawl write
  while searches run; caches key on an index generation counter, so they cannot serve stale results.
- **Crash safety.** Segment files and manifests are written to temporary files and atomically
  renamed; an index that disagrees with the database is rebuilt at startup.
- **Operability.** Prometheus metrics (latency per mode, zero-result rate, index size), OpenAPI,
  RFC 9457 error bodies, Docker compose.
- **Decisions.** Sixteen ADRs in `docs/DECISIONS.md` record the context and trade-off behind each
  non-obvious choice.

## 6. Limitations and future work

- **PageRank weight is not learned.** No public judged collection with a link graph fits the project,
  so the prior's weight (0.3 on a log scale) is set by reasoning and verified by behaviour tests,
  not by a metric (ADR-012).
- **Single writer, single node.** Merges run inline on the writer thread; there is no sharding or
  replication.
- **Statistics include tombstones until merge**, as in Lucene; heavy-delete workloads shift scores
  slightly until a merge.
- **CPU encoding is slow** (~20 abstracts/s). A quantised model, GPU, or batching across requests
  would help; the HNSW graph is in memory and rebuilt from stored vectors at startup.
- **Docker files were not built** in the development environment.
- Natural next steps: Block-Max WAND, learned sparse retrieval, a cross-encoder re-ranker over the
  hybrid top 50, and query-log-based spelling and autocomplete.

## 7. Reproducing

```sh
mvn -B test
scripts/fetch-beir.sh scifact && scripts/fetch-beir.sh fiqa && scripts/fetch-model.sh
mvn -q compile exec:java -Dexec.args="data/beir/scifact"            # quality table
mvn -q test-compile exec:java -Dexec.classpathScope=test \
    -Dexec.mainClass=com.searchengine.eval.LuceneBaseline -Dexec.args="data/beir/scifact"
mvn -q compile exec:java -Dexec.mainClass=com.searchengine.eval.Benchmark -Dexec.args="data/beir/fiqa"
mvn -q compile exec:java -Dexec.mainClass=com.searchengine.eval.VectorBenchmark -Dexec.args="data/beir/scifact"
```
