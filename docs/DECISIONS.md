# Design decisions (ADR log)

Short records of *why* things are the way they are. Format: context → decision → consequence.

## ADR-001 — Build the engine ourselves; use Lucene only as a baseline
- Context: the project's value is demonstrating search internals. Wrapping Elasticsearch/Lucene would hide them.
- Decision: index, codec, ranking, query evaluation and vector search are hand-written. Lucene is a
  test-scope dependency used only to produce a reference BM25 score in the evaluation harness.
- Consequence: more code to own, but every claim ("within X% of Lucene nDCG") is measurable.

## ADR-002 — Target Java 21 (LTS)
- Context: pom targeted Java 25; most machines/CI images ship 21 LTS.
- Decision: compile for 21. Records, sealed types, pattern matching and virtual threads are all available.

## ADR-003 — Engine core is Spring-free
- Context: the evaluation harness must index 50k+ docs quickly without a web container or JPA.
- Decision: `analysis`, `index`, `search`, `vector`, `eval` packages have no Spring imports. Spring
  services wrap them.
- Consequence: benchmarks run as plain `main` programs; unit tests are fast.

## ADR-004 — Generation-keyed query cache instead of Spring/Redis cache
- Context: `@Cacheable` results were never evicted, so searches after a crawl or document upload
  returned stale results. Redis was configured but unused, and `SearchResult` is not serializable.
- Decision: an in-engine LRU keyed on `(index.generation(), normalisedQuery)`. Every index mutation
  bumps the generation, so invalidation cannot be forgotten. Redis dependency removed.
- Consequence: cache is per-process (fine for a single-node engine); no external service needed to run.

## ADR-005 — Doc-at-a-time scoring over doc-ordered postings
- Context: the prototype accumulated scores term-at-a-time in a HashMap and sorted every match.
- Decision: postings are ordered by doc id and exposed as cursors with `advance()`; queries compile
  to scorer trees (conjunction, disjunction, exclusion, phrase) iterated doc-at-a-time into a
  bounded top-k heap.
- Consequence: boolean operators and phrases compose naturally, memory is O(k) not O(matches),
  and dynamic pruning (WAND) can be added without changing callers.

## ADR-006 — External document ids are the engine's doc ids
- Context: database ids are already unique, increasing integers.
- Decision: postings are keyed by the database id rather than a separate dense internal id.
- Consequence: no id-mapping table; a re-added document simply replaces the old one. Segments (M4)
  merge postings by id order instead of by base offset.

## ADR-007 — Short stopword list, Porter stemmer, position gaps
- Context: stemming improves recall on English; aggressive stopword lists break phrases.
- Decision: Lucene's 33-word English list; removed tokens still consume a position so
  "bank of america" does not match "bank america". Latin diacritics folded, other scripts untouched.
- Consequence: index size grows slightly vs a long stop list; IDF handles frequent terms.

## ADR-008 — BM25F as the default ranking function, tuned on train only
- Context: per-field BM25 summed over title and body scored 0.6675 nDCG@10 on SciFact, below
  Lucene's single-field 0.6826: a term in both fields was saturated twice.
- Decision: BM25F (field-weighted, per-field length-normalised tf, one saturation), with
  df = max over fields. Parameters grid-searched on SciFact *train*; test is reported only.
- Consequence: 0.6922 test nDCG@10. Field-restricted queries (`title:x`) still use plain BM25.

## ADR-009 — Segment format: immutable files, tombstones, postings-level merges
- Context: the index had to survive restarts and scale past RAM-rebuild-from-DB on every start.
- Decision: Lucene-style immutable segments. Postings are delta+varint coded with a skip entry
  per 128-doc block; the term dictionary and per-field lengths load into arrays at open, postings
  and stored docs are read lazily from a memory-mapped file. Deletes are tombstones in a `.del`
  sidecar; merges are k-way postings merges over a `MultiReader` (no re-analysis) that drop
  tombstoned docs. A manifest file is swapped atomically on commit.
- Consequence: crash-safe commits and bounded segment counts. Statistics count tombstoned docs until
  merged (as in Lucene). The DB stays the source of truth: an index that disagrees with it is
  rebuilt at startup. Single-writer only.

## ADR-010 — WAND with stored per-term bounds
- Context: BM25's theoretical ceiling idf·(k1+1) is too loose for effective pruning.
- Decision: each term stores max tf and min field length per field; the score bound is the
  similarity evaluated at (max tf, min length), valid because BM25/BM25F increase with tf and
  decrease with length. Bounds only widen on delete, so they stay valid without rewriting.
- Consequence: 22× fewer documents scored on FiQA with exactly the exhaustive top-k; reported hit
  totals become lower bounds (exposed as `totalIsLowerBound`).

## ADR-011 — Crawler: per-host frontier, RFC 9309, SimHash
- Context: the prototype crawled one site synchronously inside the HTTP request, with a fixed sleep.
- Decision: a frontier with one queue per host, served by earliest allowed time
  (max(min delay, Crawl-delay) capped at 30 s); robots.txt per RFC 9309 (unreachable 5xx = disallow
  all); robots meta and rel=nofollow honoured; 64-bit SimHash over 3-shingles with a 4-band index
  (exact recall up to Hamming distance 3) to skip near-duplicates. Jobs run on virtual threads and
  are polled via `/api/crawl/{id}`. The crawler takes a `Fetcher` so it is tested against a fake web
  with a fake clock.
- Consequence: polite by construction and deterministic tests; single-threaded per job (politeness,
  not CPU, bounds crawl speed). SimHash is unreliable for very short pages.

## ADR-012 — PageRank as an additive log-prior on the top 100, weight 0.3
- Context: there is no public relevance-judged collection with a link graph that fits this project
  (BEIR corpora have no links), so the PageRank weight cannot be tuned like BM25F.
- Decision: score' = text + w·ln(PR·N) on the top 100 text hits (PR·N = 1 for an average page);
  w = 0.3 means a page with 10× average PageRank gains ≈ 0.69, well below a typical single-term
  BM25F contribution (2–6), so authority breaks near-ties but does not override text relevance.
- Consequence: behaviour verified by tests (hub outranks an equal-text leaf; a strong text match is
  not displaced) rather than by a metric; `lookit.ranking.pagerank-weight` makes it adjustable.
