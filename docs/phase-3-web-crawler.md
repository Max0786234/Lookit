# Phase 3: Web Crawler Implementation ✅

## Summary

A real web crawler has been integrated into the search engine, enabling automated discovery and indexing of web content. The crawler supports depth-limited crawling with rate limiting and domain restriction.

---

## New Components

### WebFetcherService
**Location:** `src/main/java/com/searchengine/crawler/WebFetcherService.java`

Handles:
- HTTP page fetching with proper User-Agent headers
- HTML parsing via `CrawlerService`
- Document extraction (title, text, links)
- URL normalization and deduplication
- Same-domain crawling restriction
- Rate limiting (500ms between requests)
- Automatic database storage of discovered pages
- Crawl statistics and progress tracking

**Key Methods:**
```java
CrawlStats crawl(String seedUrl, int maxDepth, int maxPages, Consumer<String> onDocumentFound)
CrawlStats crawl(String seedUrl, int maxDepth, int maxPages)
```

### API Endpoint: POST /api/crawl
**Location:** `src/main/java/com/searchengine/api/SearchController.java`

**Request:**
```json
{
  "seedUrl": "https://example.com",
  "maxPages": 10,
  "maxDepth": 2
}
```

**Response:**
```json
{
  "seedUrl": "https://example.com",
  "crawledPages": 3,
  "urls": [
    "Crawled example.com, found 3 documents, attempted 5"
  ]
}
```

**Limits:**
- Max 100 pages per crawl (enforced)
- Max depth 3 (enforced)
- 500ms delay between requests (politeness)
- Only follows links on same domain

---

## Integration Changes

### SearchEngineService
- Added `reindexAllDocuments()` method
- Rebuilds in-memory search index from database
- Called after crawling to make new documents searchable
- Idempotent: re-adding same document updates index

### SearchController
- Injected `WebFetcherService`
- Updated `/api/crawl` endpoint to:
  1. Crawl and store pages in database
  2. Trigger index rebuild
  3. Return crawl statistics

---

## How Crawling Works

```
1. User calls POST /api/crawl
   ↓
2. WebFetcherService.crawl() processes seed URL
   ↓
3. BFS traversal with rate limiting
   - Fetch page with HttpClient
   - Extract title, text, links via CrawlerService
   - Store in DocumentRepository (if not empty)
   - Add child links to queue (same domain only)
   - Rate limit: 500ms per request
   ↓
4. SearchEngineService.reindexAllDocuments()
   - Load all DB documents
   - Add new ones to in-memory index
   ↓
5. Return CrawlStats to client
```

---

## Crawling Behavior

### What Gets Indexed
- Page title (from `<title>` tag)
- Visible text content (HTML stripped, lowercased)
- Linked URLs (for discovering more pages)

### What's Filtered
- Blank titles or empty text
- Cross-domain links (only follows same domain)
- Data URIs and special schemes (data:, mailto:, etc.)
- Duplicate URLs (deduplicated via HashSet)

### Rate Limiting
- 500ms delay between HTTP requests
- Single-threaded crawling (no concurrency yet)
- Proper User-Agent identification

### Safety Limits
- Max 100 pages per crawl (safety limit, configurable)
- Max depth 3 (prevents runaway traversal)
- 10-second timeout per HTTP request
- Handles missing pages gracefully

---

## Example Crawl

```powershell
# Crawl example.com with small limits
$body = @{
    seedUrl = "https://example.com"
    maxPages = 5
    maxDepth = 1
} | ConvertTo-Json

Invoke-WebRequest -Uri 'http://localhost:8080/api/crawl' `
    -Method POST `
    -Body $body `
    -ContentType 'application/json'
```

Response indicates successful crawl of 3 pages.

---

## Technical Details

### URL Normalization
- Converts relative URLs to absolute
- Handles protocol-relative URLs (//example.com/path)
- Normalizes trailing slashes
- Validates URI structure

### HTML Parsing
Uses regex patterns for:
- `<title>` extraction (case-insensitive, dotall mode)
- Link extraction with href attribute parsing
- HTML tag stripping (removes all tags, preserves text)
- Multiple whitespace normalization

### Domain Checking
- Extracts domain from URI
- Ensures crawled links end with same domain
- Prevents accidentally crawling external links
- Handles subdomains correctly

---

## Production Considerations

### Not Yet Implemented
- ⏳ robots.txt respect
- ⏳ Concurrent crawling (thread pool)
- ⏳ JavaScript rendering (requires Selenium/Puppeteer)
- ⏳ Duplicate content detection
- ⏳ Crawl queue persistence
- ⏳ Crawl cancellation
- ⏳ Link prioritization (PageRank-style)
- ⏳ Last-Modified header caching

### What Works
- ✅ Basic HTML fetching and parsing
- ✅ Link discovery and same-domain filtering
- ✅ Rate limiting and politeness
- ✅ Database persistence
- ✅ Search index integration
- ✅ Error handling and resilience

---

## Usage Patterns

### Basic Crawl
```json
{
  "seedUrl": "https://docs.example.com",
  "maxPages": 20,
  "maxDepth": 2
}
```

### Single-Page Fetch
```json
{
  "seedUrl": "https://example.com/article",
  "maxPages": 1,
  "maxDepth": 0
}
```

### Deep Crawl
```json
{
  "seedUrl": "https://example.com",
  "maxPages": 100,
  "maxDepth": 3
}
```

---

## Search Integration

After crawling, new documents are immediately searchable:

```powershell
# After crawl completes, search for terms from crawled content
Invoke-WebRequest 'http://localhost:8080/api/search?q=term+from+crawled+page'
```

The `/api/stats` endpoint shows updated corpus size including crawled pages.

---

## Next Steps

### Immediate Improvements
1. Add robots.txt respect
2. Implement concurrent crawling with thread pool
3. Add crawl cancellation support
4. Persist crawl queue for large jobs

### Future Enhancements
1. JavaScript rendering for SPA sites
2. Duplicate content detection (simhash)
3. Link quality filtering (spam detection)
4. Refresh schedule for continuous crawling
5. Crawl priority/budget allocation
6. Link text extraction for anchor text ranking

---

## Architecture Summary

The web crawler completes the basic search engine pipeline:

```
User Query
    ↓
/api/search endpoint
    ↓
SearchEngineService (routes to)
    ↓
SearchService + BM25Ranker
    ↓
InvertedIndex (built from)
    ↓
DocumentRepository (persisted by)
    ↓
WebFetcherService + DocumentLoaderService + Manual API
    ↓
Real Web Content (crawled) + File Content (loaded) + Manual Input
```

The system can now:
✅ Fetch content from the web
✅ Parse and extract text
✅ Store persistently
✅ Index for search
✅ Rank results with BM25
✅ Scale to PostgreSQL
✅ Cache with Redis

This is a **functional search engine** that can grow beyond demos.
