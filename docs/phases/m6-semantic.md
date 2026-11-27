# M6 — Semantic search and query assistance

- `WordPieceTokenizer`: Java port of BERT's uncased BasicTokenizer + WordPiece (matches the
  HuggingFace reference on its documentation example).
- `OnnxEncoder`: all-MiniLM-L6-v2 on ONNX Runtime, batched, mean-pooled, L2-normalised.
- `HnswIndex`: hand-written HNSW with the diversity heuristic and tombstone deletes;
  `ExactVectorIndex` as ground truth. `VectorStore` persists embeddings so only new documents are
  encoded.
- `RankFusion`: Reciprocal Rank Fusion; `SearchEngine` modes `KEYWORD | SEMANTIC | HYBRID`.
- `QuerySuggester`: "did you mean" (only for words matching nothing) and autocomplete.

| | SciFact nDCG@10 | FiQA nDCG@10 |
|---|---|---|
| BM25F | 0.691 | 0.248 |
| Dense (exact / HNSW) | 0.645 / 0.645 | 0.369 / 0.365 |
| Hybrid RRF | **0.721** | 0.361 |

Dense scores equal the published model numbers, which validates the pipeline. Fusion wins where both
retrievers are competitive (SciFact) and slightly loses to dense alone where lexical matching is weak
(FiQA). HNSW at ef = 64 reaches recall@10 of 0.994 / 0.990 against exact search, 39× faster on FiQA.
