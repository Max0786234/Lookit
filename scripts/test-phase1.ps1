& "$PSScriptRoot\setup-env.ps1"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$mainFiles = Get-ChildItem -Recurse src/main/java -Filter *.java | ForEach-Object { $_.FullName }
$testFiles = Get-ChildItem -Recurse src/test/java -Filter *.java | ForEach-Object { $_.FullName }

javac -d out $mainFiles $testFiles
java -cp out com.searchengine.SearchEngineTests
