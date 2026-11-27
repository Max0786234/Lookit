# Roadmap

Status legend: [x] done · [~] in progress · [ ] todo

Ordering principle: correctness first, then the seams that let storage change without breaking the
API, then **measurement**, and only then the expensive internals — so every ranking/perf change is
backed by a number.

## M0 — Correctness
- [x] Java 21 target, CI (GitHub Actions)
- [x] Crawler: TLS verification, gzip, safe domain match, honest User-Agent, URL canonicalisation
- [x] Search cache invalidated on every index mutation
- [x] O(1) average document length (running total)
- [x] Thread-safe index (concurrent crawl + search)
- [x] Database-assigned ids only (no `count()+1` fallback)
- [x] API error handling (400/404 JSON bodies) + request validation
- [x] Pagination + snippets instead of full content in results
- [x] SLF4J everywhere, drop unused Redis

## M1 — Index seam + analyzer
- [x] `IndexReader` interface; search depends on it, not on a concrete index
- [x] Analyzer pipeline: Unicode tokenizer (with offsets), lower-case, stopwords, Porter stemmer
- [x] Fielded documents (title / body) with per-field positions and lengths

## M2 — Evaluation (moved early on purpose)
- [x] BEIR loader (SciFact: 5k docs, 300 test queries)
- [x] Metrics: nDCG@10, MRR@10, Recall@100
- [x] CLI benchmark runner + results table in `docs/evaluation.md`
- [x] Lucene BM25 baseline (test scope only)

## M3 — Ranking + query language
- [x] BM25F with tuned field weights
- [x] Query parser: AND / OR / NOT, "phrases", field:term, prefix*
- [x] Top-k heap
- [x] Highlighted snippets

## M4 — On-disk index
- [x] Postings codec (delta + varint)
- [x] Immutable segments, RAM buffer → flush, tombstone deletes, merges
- [x] Load index from disk on startup
- [x] WAND dynamic pruning
- [x] Latency / throughput / index-size benchmarks

## M5 — Crawler
- [x] Async crawl jobs + status endpoint
- [x] robots.txt + per-host politeness
- [x] SimHash near-duplicate detection
- [x] Link graph + PageRank as a ranking signal (tested; no public judged link graph — see ADR-012)

## M6 — Semantic search + query assistance
- [ ] Dense embeddings (pluggable encoder)
- [ ] HNSW vector index
- [ ] Hybrid retrieval with Reciprocal Rank Fusion, evaluated against BM25
- [ ] Spelling correction ("did you mean")
- [ ] Autocomplete (prefix suggestions)

## M7 — Polish
- [ ] Docker compose full stack, OpenAPI, metrics endpoint
- [ ] Frontend: pagination, highlighting, suggestions, crawl status
- [ ] Final report
