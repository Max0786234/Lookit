# Performance

Measured with `eval/Benchmark` on BEIR FiQA (57,638 docs, 648 queries), k = 10, BM25F, JDK 21,
16-core laptop, Windows 11. Reproduce:

```sh
scripts/fetch-beir.sh fiqa
mvn -q compile exec:java -Dexec.mainClass=com.searchengine.eval.Benchmark -Dexec.args="data/beir/fiqa"
```

## Indexing

| | value |
|---|---|
| raw text (title+body, UTF-8) | 44.3 MB |
| in-memory build | 5.66 s (10,180 docs/s) |
| segmented build incl. 6 flushes + commit | 7.65 s (7,536 docs/s) |
| force-merge to 1 segment (postings-level k-way merge) | 2.41 s |
| on-disk index (stored docs + positional postings) | 61.2 MB (138% of raw) |
| vocabulary (body) | 56,937 terms |

The index is larger than the raw text because it holds a full copy of every document (for
snippets) *plus* positional postings; postings alone are delta+varint compressed.

## Query latency (single thread)

| index | evaluation | mean ms | p50 | p95 | p99 | QPS | docs scored / query |
|---|---|---|---|---|---|---|---|
| memory | exhaustive | 3.685 | 3.203 | 8.048 | 11.689 | 271 | 28,444 |
| memory | WAND | 1.958 | 1.564 | 4.995 | 7.523 | 510 | 1,294 |
| mmap segment | exhaustive | 7.440 | 6.598 | 15.741 | 19.880 | 134 | 28,444 |
| mmap segment | WAND | 2.645 | 2.267 | 6.296 | 8.697 | 378 | 1,294 |

Throughput, mmap segment + WAND, 4 threads: **859 QPS**.

Observations:
- **WAND scores 22× fewer documents** (1,294 vs 28,444 per query) with identical top-10 (verified by
  `WandTest`), giving 1.9× (memory) and 2.8× (segment) lower mean latency. The speed-up is smaller
  than the pruning ratio because cursor movement and decoding still touch skipped postings.
- Decoding varint postings from the mapped file costs ~2× vs the in-memory arrays in exhaustive
  mode, but block skipping makes the gap small once WAND uses `advance()`.
- Queries are natural-language questions averaging ~10 terms, which is the hard case for WAND:
  many common terms keep the bound high.
