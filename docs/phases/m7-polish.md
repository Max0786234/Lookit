# M7 — Polish

- **Observability.** Actuator health (liveness/readiness probes), `/actuator/prometheus` with
  `lookit_search_seconds{mode}` latency histograms, a zero-result counter and index-size gauges.
- **API documentation.** springdoc generates the OpenAPI spec (`/v3/api-docs`) and Swagger UI.
- **Deployment.** Multi-stage Dockerfiles (backend on a glibc JRE for ONNX Runtime, frontend on
  nginx proxying `/api`), compose with PostgreSQL, persistent index volume and read-only model mount.
  These files were written without a Docker runtime available and have not been built.
- **UI.** Search modes (keyword / semantic / hybrid), autocomplete via `<datalist>`, "did you mean",
  highlighted snippets rendered from offsets (no `innerHTML`), pagination that copes with WAND
  lower-bound totals, and polling of asynchronous crawl jobs.
- **Docs.** README rewritten around measured results; prototype notes moved to `docs/archive/`.
