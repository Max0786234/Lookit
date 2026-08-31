$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Docker is not installed or not on PATH. Install Docker Desktop or a Docker-compatible runtime, then re-run this script."
    exit 1
}

docker compose up -d --wait
Write-Host "PostgreSQL and Redis are running on localhost:5432 and localhost:6379."
