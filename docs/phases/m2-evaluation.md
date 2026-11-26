# M2 — Evaluation harness

Moved ahead of the storage work so every later change is backed by a number.

- `eval/BeirDataset` loads any BEIR-format directory (corpus, queries, test qrels; plain or gzip).
- `eval/Metrics` implements nDCG@k, MRR@k, Recall@k with trec_eval conventions, unit-tested
  against hand-computed values.
- `eval/Evaluator` runs a `Retriever` over all judged queries and reports means plus latency.
- `eval/EvalRunner` is a CLI with a registry of named system configurations; the Lucene reference
  systems live in test scope (`LuceneBaseline`).

Baseline on SciFact: our BM25 0.6675 nDCG@10 vs Lucene multi-field 0.6697 and Lucene flat 0.6826.
Full table and analysis: [docs/evaluation.md](../evaluation.md).
