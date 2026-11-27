# Deployment

Live demo: **https://lookit.ashymeadow-cdaa71fc.uaenorth.azurecontainerapps.io**

## Image

`deploy/huggingface/Dockerfile` builds one self-contained image from the GitHub repository at a
pinned commit: the React UI is bundled into the Spring Boot jar (same-origin `/api`), and the
all-MiniLM-L6-v2 model, the SciFact corpus and its embeddings are prepared at build time. A fresh
container is searchable about 30 seconds after start. It listens on port 7860 and runs as UID 1000,
so it also fits Hugging Face Spaces, but it runs on any container host.

## Azure Container Apps (current)

| Resource | Name | Notes |
|---|---|---|
| Resource group | `lookit-rg` (UAE North) | Lookit only; delete it to remove everything except the image |
| Environment | `lookit-env` | Consumption plan, log shipping disabled (no Log Analytics cost) |
| Container app | `lookit` | 1 vCPU / 2 GiB, **scale 0–1**: free while idle, ~30–60 s cold start |
| Registry | `billiemaxacr` (shared) | image repository `lookit` |

Settings: `LOOKIT_CORS_ORIGINS` is set on the app to its own HTTPS address, so browser POSTs (crawl
jobs) are accepted.

Redeploy after pushing new commits:

```powershell
scripts\deploy-azure.ps1 -Commit <pushed-commit-sha>
```

Data is not persistent: the index lives in the container's `/tmp`, so a restart or scale-to-zero
reloads the SciFact corpus and drops crawled pages. For persistence, mount an Azure Files share at
`LOOKIT_INDEX_DIR` and point the app at Azure Database for PostgreSQL (`postgres` profile).
