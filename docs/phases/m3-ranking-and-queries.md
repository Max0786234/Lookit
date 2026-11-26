# M3 — Ranking and query language

**BM25F.** Summing per-field BM25 saturates a term once per field, so a term that appears in both
title and body is over-rewarded. `Bm25fSearcher` length-normalises and weights each field's term
frequency, adds them, and saturates once. Parameters were grid-searched on SciFact *train*
(`Bm25fTuner`, 225 configurations) and only then measured on test:

| | nDCG@10 (test) |
|---|---|
| per-field BM25 sum | 0.6675 |
| BM25F, default parameters | 0.6895 |
| BM25F, tuned (k1 0.9, title ×2) | 0.6922 |
| Lucene BM25, single field | 0.6826 |

**Query language.** `QueryParser`: `AND`/`OR`, `+`/`-`/`NOT`, `"phrases"`, `title:`/`body:`,
`prefix*`, parentheses. Decisions driven by the evaluation:
- hyphenated words (`IL-6`, `iPSC-derived`) become optional terms, not strict phrases
  (strict phrases cost 1.8 points of Recall@100);
- repeated words count once, matching the tuning setup;
- operators are upper-case only, so natural-language text is never misread as syntax;
- lone punctuation (`(+)-`) is dropped instead of being read as an operator (found by diffing
  parser output against bag-of-words on all 300 test queries).

Final: 0.6914 nDCG@10 with the parser (one query differs, where `+1` is legitimately syntax).
