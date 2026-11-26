# M0 — Correctness

Goal: make the existing prototype correct and safe before adding features, so later measurements
are not distorted by bugs.

| Problem in the prototype | Effect | Fix |
|---|---|---|
| `@Cacheable` search results never evicted | Searches after a crawl/upload returned stale results | LRU keyed on `(index generation, query)` — ADR-004 |
| `averageDocumentLength()` summed all lengths, called per scored posting | Query cost O(postings × docs) | Running total, O(1) |
| Plain `HashMap`s mutated by crawl while searches iterate | `ConcurrentModificationException` / torn reads | `ReentrantReadWriteLock`, snapshot getters |
| `documentCount() + 1` id fallback | Id collisions after deletes | DB identity only |
| `IllegalArgumentException` for missing docs | 500 instead of 404 | `ProblemDetail` handler + Bean Validation |
| Full 5000-char bodies, unbounded result lists | Large responses, no paging | `page`/`size`, 30-word query-focused snippet with highlight offsets |
| Trust-all TLS, Chrome UA spoofing, `endsWith` domain check | MITM-able, dishonest, `evilexample.com` accepted | Default TLS, `LookitBot` UA, dot-boundary check |
| Redis configured but unused; `System.out` logging | Dead dependency, noisy logs | Removed; SLF4J |

Tests added: concurrent read/write on the index, remove/re-add statistics, cache generation
invalidation, service-level "search after add/delete", API 400/404 cases, snippet window selection.

Test count: 13 → 32.
