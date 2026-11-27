#!/usr/bin/env sh
# Downloads a BEIR dataset into data/beir/<name>:  scripts/fetch-beir.sh scifact|fiqa
# Revisions are pinned to the last commit that ships JSONL (later ones are Parquet only).
set -eu
NAME="${1:-scifact}"
case "$NAME" in
  scifact) REV=984eed826375f18d27936c4a32bf0f8491e3f414 ;;
  fiqa)    REV=e31855201132ea2a257d7df77c828d7c02427521 ;;
  *) echo "unknown dataset $NAME (known: scifact, fiqa)" >&2; exit 1 ;;
esac
DIR="$(dirname "$0")/../data/beir/$NAME"
BASE="https://huggingface.co/datasets/BeIR"
mkdir -p "$DIR"
curl -fsSL -o "$DIR/corpus.jsonl.gz"  "$BASE/$NAME/resolve/$REV/corpus.jsonl.gz"
curl -fsSL -o "$DIR/queries.jsonl.gz" "$BASE/$NAME/resolve/$REV/queries.jsonl.gz"
curl -fsSL -o "$DIR/test.tsv"         "$BASE/$NAME-qrels/resolve/main/test.tsv"
curl -fsSL -o "$DIR/train.tsv"        "$BASE/$NAME-qrels/resolve/main/train.tsv"
echo "$NAME ready in $DIR"
