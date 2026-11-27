# M4 — On-disk index and dynamic pruning

- `ByteIO`: varint codec. `SegmentWriter`/`SegmentReader`: immutable, memory-mapped segment files
  (stored docs, per-field lengths, sorted term dictionary with df/ttf/WAND bounds, delta+varint
  positional postings with 128-doc skip blocks).
- `MultiReader`: k-way merged view over segments + RAM buffer. `SegmentedIndex`: buffer → flush →
  segments, tombstone deletes, atomic manifest commits, size-tiered merges, cleanup of
  unreferenced files (deferred on Windows while mapped).
- Spring: `lookit.index.dir` enables persistence; the index is reused if it matches the DB and
  rebuilt otherwise; commits every 10 s when dirty and on shutdown.
- `Wand`: Weak-AND over pure disjunctions with stored per-term bounds; exact top-k.

Verification: round-trip tests compare every posting, statistic and stored doc of a segment with
the source `MemoryIndex`; randomized `advance()` across block boundaries; rankings on segmented
indexes equal in-memory rankings; WAND equals exhaustive on 1,200 random query/k pairs.

Results (FiQA, 57k docs): 22× fewer docs scored, 2.8× lower latency on mmap segments, 859 QPS on
4 threads. Details: [docs/performance.md](../performance.md).
