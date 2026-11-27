#!/usr/bin/env sh
# Downloads the all-MiniLM-L6-v2 sentence-embedding model (ONNX, 90 MB) used for dense retrieval.
set -eu
DIR="$(dirname "$0")/../data/models/all-MiniLM-L6-v2"
BASE="https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main"
mkdir -p "$DIR"
curl -fsSL -o "$DIR/model.onnx" "$BASE/onnx/model.onnx"
curl -fsSL -o "$DIR/vocab.txt"  "$BASE/vocab.txt"
echo "model ready in $DIR"
