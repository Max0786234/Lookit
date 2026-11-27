# Downloads a BEIR dataset into data\beir\<name>:  scripts\fetch-beir.ps1 scifact|fiqa  (see fetch-beir.sh)
param([string]$Name = 'scifact')
$ErrorActionPreference = 'Stop'
$revs = @{ scifact = '984eed826375f18d27936c4a32bf0f8491e3f414'; fiqa = 'e31855201132ea2a257d7df77c828d7c02427521' }
if (-not $revs.ContainsKey($Name)) { throw "unknown dataset $Name (known: $($revs.Keys -join ', '))" }
$rev = $revs[$Name]
$dir = Join-Path $PSScriptRoot "..\data\beir\$Name"
$base = 'https://huggingface.co/datasets/BeIR'
New-Item -ItemType Directory -Force $dir | Out-Null
Invoke-WebRequest "$base/$Name/resolve/$rev/corpus.jsonl.gz" -OutFile (Join-Path $dir 'corpus.jsonl.gz')
Invoke-WebRequest "$base/$Name/resolve/$rev/queries.jsonl.gz" -OutFile (Join-Path $dir 'queries.jsonl.gz')
Invoke-WebRequest "$base/$Name-qrels/resolve/main/test.tsv" -OutFile (Join-Path $dir 'test.tsv')
Invoke-WebRequest "$base/$Name-qrels/resolve/main/train.tsv" -OutFile (Join-Path $dir 'train.tsv')
Write-Host "$Name ready in $dir"
