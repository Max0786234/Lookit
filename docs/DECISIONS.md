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
