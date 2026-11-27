# M5 — Crawler

| Component | What it does | Verified by |
|---|---|---|
| `HtmlExtractor` | title / main text / normalised links; robots meta, rel=nofollow, boilerplate removal | `HtmlExtractorTest` |
| `HttpFetcher` | verified TLS, honest UA, gzip/deflate, charset, 5 MB cap | `UrlNormalizerTest` |
| `RobotsTxt` | RFC 9309 groups, longest match, `*`/`$`, Crawl-delay, unreachable policy | `RobotsTxtTest` (7) |
| `SimHash` | 64-bit fingerprints, 4-band index with guaranteed recall at distance ≤ 3 | `SimHashTest` |
| `Crawler` | per-host polite frontier, depth/page limits, same-site filter, cancellation | `CrawlerTest` against a fake web + fake clock |
| `PageRank` | power iteration, dangling redistribution | `PageRankTest` (analytic fixed point) |
| `CrawlJobService` + `/api/crawl` | async jobs, link storage, PageRank → ranking prior | `CrawlJobServiceTest`, `CrawlControllerTest`, live run on example.com |

The crawl endpoint used to block the request for up to a minute; it now returns `202` with a job
id that the UI polls. PageRank is applied as a log-prior on the top 100 hits (ADR-012).
