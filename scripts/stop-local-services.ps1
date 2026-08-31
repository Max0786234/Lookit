$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

docker compose down
Write-Host "PostgreSQL and Redis containers have been stopped."
