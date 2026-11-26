# Downloads the BEIR SciFact dataset into data/beir/scifact (see fetch-scifact.sh).
$ErrorActionPreference = 'Stop'
$rev = '984eed826375f18d27936c4a32bf0f8491e3f414'
$dir = Join-Path $PSScriptRoot '..\data\beir\scifact'
New-Item -ItemType Directory -Force $dir | Out-Null
Invoke-WebRequest "https://huggingface.co/datasets/BeIR/scifact/resolve/$rev/corpus.jsonl.gz" -OutFile "$dir\corpus.jsonl.gz"
Invoke-WebRequest "https://huggingface.co/datasets/BeIR/scifact/resolve/$rev/queries.jsonl.gz" -OutFile "$dir\queries.jsonl.gz"
Invoke-WebRequest "https://huggingface.co/datasets/BeIR/scifact-qrels/resolve/main/test.tsv" -OutFile "$dir\test.tsv"
Write-Host "SciFact ready in $dir"
