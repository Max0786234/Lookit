& "$PSScriptRoot\setup-env.ps1"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$files = Get-ChildItem -Recurse src/main/java -Filter *.java | ForEach-Object { $_.FullName }
javac -d out $files
java -cp out com.searchengine.App
