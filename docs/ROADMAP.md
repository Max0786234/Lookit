# Roadmap

Status legend: [x] done · [~] in progress · [ ] todo

## Phase 0 — Foundation
- [ ] Java 21 target, CI (GitHub Actions)
- [ ] Crawler fixes: TLS verification, gzip, domain check, honest User-Agent, URL dedup
- [ ] API error handling + validation
- [ ] Cache invalidation, BM25 avg-length cost, SLF4J logging

## Phase 1 — Real index
- [ ] Analyzer: Unicode tokenizer, stopwords, Porter stemmer, offsets
- [ ] Fielded documents (title/body/url) + BM25F
- [ ] Postings codec (delta + varint), on-disk immutable segments (mmap)
- [ ] IndexWriter: RAM buffer → flush, tiered merges, tombstone deletes, snapshot readers
- [ ] Top-k heap, WAND / Block-Max WAND

## Phase 2 — Query features
- [ ] Query parser: AND/OR/NOT, phrases, field:term, prefix*
- [ ] Snippets + highlighting, pagination
- [ ] Spelling correction, autocomplete

## Phase 3 — Crawler
- [ ] Async crawl jobs + status endpoint
- [ ] Frontier with per-host politeness, robots.txt, sitemaps
- [ ] SimHash near-duplicate detection
- [ ] Link graph + PageRank as ranking signal

## Phase 4 — Evaluation
- [ ] BEIR loader + nDCG@10 / MRR / Recall@100
- [ ] Lucene BM25 baseline comparison
- [ ] Latency / throughput / index size benchmarks

## Phase 5 — Semantic search
- [ ] Embeddings (ONNX MiniLM)
- [ ] HNSW vector index
- [ ] Hybrid retrieval (RRF) + eval

## Phase 6 — Polish
- [ ] Docker compose full stack, OpenAPI, metrics
- [ ] Frontend redesign
- [ ] Final report (PDF)
