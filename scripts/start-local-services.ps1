$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Docker is not installed or not on PATH. Install Docker Desktop or a Docker-compatible runtime, then re-run this script."
    exit 1
}

docker compose up -d --wait postgres
Write-Host "PostgreSQL is running on localhost:5432."
