# Builds the single-container image, pushes it to Azure Container Registry and rolls out a new
# revision of the Lookit container app. Needs Docker Desktop running and `az login` done once.
#   scripts\deploy-azure.ps1            # deploys the commit pinned in deploy/huggingface/Dockerfile
#   scripts\deploy-azure.ps1 -Commit <sha>   # deploys another pushed commit
param(
    [string]$Commit = '',
    [string]$Registry = 'billiemaxacr',
    [string]$ResourceGroup = 'lookit-rg',
    [string]$App = 'lookit'
)
$ErrorActionPreference = 'Stop'
$env:Path = "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin;$env:Path"
$root = Split-Path -Parent $PSScriptRoot
$context = Join-Path $root 'deploy\huggingface'

$buildArgs = @()
if ($Commit) { $buildArgs = @('--build-arg', "COMMIT=$Commit") }
$tag = if ($Commit) { $Commit.Substring(0, 7) } else { (git -C $root rev-parse --short HEAD).Trim() }
$image = "$Registry.azurecr.io/lookit:$tag"

docker build @buildArgs -t $image $context
az acr login -n $Registry | Out-Null
docker push $image
az containerapp update -n $App -g $ResourceGroup --image $image --query properties.latestRevisionName -o tsv
$fqdn = az containerapp show -n $App -g $ResourceGroup --query properties.configuration.ingress.fqdn -o tsv
Write-Host "Deployed $image -> https://$fqdn"
