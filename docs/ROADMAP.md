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
- [ ] `IndexReader` interface; search depends on it, not on a concrete index
- [ ] Analyzer pipeline: Unicode tokenizer (with offsets), lower-case, stopwords, Porter stemmer
- [ ] Fielded documents (title / body) with per-field positions and lengths

## M2 — Evaluation (moved early on purpose)
- [ ] BEIR loader (SciFact: 5k docs, 300 test queries)
- [ ] Metrics: nDCG@10, MRR@10, Recall@100
- [ ] CLI benchmark runner + results table in `docs/evaluation.md`
- [ ] Lucene BM25 baseline (test scope only)

## M3 — Ranking + query language
- [ ] BM25F with tuned field weights
- [ ] Query parser: AND / OR / NOT, "phrases", field:term, prefix*
- [ ] Top-k heap
- [ ] Highlighted snippets

## M4 — On-disk index
- [ ] Postings codec (delta + varint)
- [ ] Immutable segments, RAM buffer → flush, tombstone deletes, merges
- [ ] Load index from disk on startup
- [ ] WAND dynamic pruning
- [ ] Latency / throughput / index-size benchmarks

## M5 — Crawler
- [ ] Async crawl jobs + status endpoint
- [ ] robots.txt + per-host politeness
- [ ] SimHash near-duplicate detection
- [ ] Link graph + PageRank as a ranking signal (evaluated)

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
