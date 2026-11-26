#!/usr/bin/env sh
# Downloads the BEIR SciFact dataset (5,183 abstracts, 300 test queries) into data/beir/scifact.
# Pinned to the last revision that ships JSONL (later revisions are Parquet only).
set -eu
REV=984eed826375f18d27936c4a32bf0f8491e3f414
DIR="$(dirname "$0")/../data/beir/scifact"
mkdir -p "$DIR"
curl -fsSL -o "$DIR/corpus.jsonl.gz"  "https://huggingface.co/datasets/BeIR/scifact/resolve/$REV/corpus.jsonl.gz"
curl -fsSL -o "$DIR/queries.jsonl.gz" "https://huggingface.co/datasets/BeIR/scifact/resolve/$REV/queries.jsonl.gz"
curl -fsSL -o "$DIR/test.tsv"         "https://huggingface.co/datasets/BeIR/scifact-qrels/resolve/main/test.tsv"
curl -fsSL -o "$DIR/train.tsv"        "https://huggingface.co/datasets/BeIR/scifact-qrels/resolve/main/train.tsv"
echo "SciFact ready in $DIR"
