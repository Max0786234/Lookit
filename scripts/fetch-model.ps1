# Downloads the all-MiniLM-L6-v2 sentence-embedding model (ONNX, 90 MB) used for dense retrieval.
$ErrorActionPreference = 'Stop'
$dir = Join-Path $PSScriptRoot '..\data\models\all-MiniLM-L6-v2'
$base = 'https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main'
New-Item -ItemType Directory -Force $dir | Out-Null
Invoke-WebRequest "$base/onnx/model.onnx" -OutFile (Join-Path $dir 'model.onnx')
Invoke-WebRequest "$base/vocab.txt" -OutFile (Join-Path $dir 'vocab.txt')
Write-Host "model ready in $dir"
