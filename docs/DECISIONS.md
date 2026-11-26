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
