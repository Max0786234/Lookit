# M1 — Index seam and analysis

**Analysis.** `Analyzer` segments text on Unicode letters/digits (CJK as unigrams), lower-cases,
folds Latin diacritics, strips possessives, removes 33 English stopwords while keeping position gaps,
and applies the Porter stemmer (validated against the reference vocabulary). Each token carries
character offsets for highlighting.

**Index.** `IndexReader` is the only thing search code sees: postings cursors (`nextDoc`,
`advance`, `freq`, `positions`), per-field lengths, collection statistics and a sorted term
dictionary for prefix expansion. `MemoryIndex` implements it with title and body as separate fields.

**Search.** Queries are an AST (`Term`, `Phrase`, `Prefix`, `Bool`) compiled into doc-at-a-time
scorers; results go through a top-k min-heap. Phrases are matched per field, so they can no longer
span the title/body boundary.

**Engine.** `SearchEngine` is Spring-free and is used by the API, and later by the evaluation
harness and benchmarks.

Tests: 125 (Porter cases are parameterised).
