$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

docker compose down
Write-Host "PostgreSQL container has been stopped."
