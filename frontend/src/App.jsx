import { useState, useEffect } from 'react'
import './App.css'

const API = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api'
const PAGE_SIZE = 10

/** Renders snippet text with [start, end) highlight ranges as <mark>, without innerHTML. */
function Highlighted({ text, ranges }) {
  const parts = []
  let cursor = 0
  for (const [start, end] of ranges ?? []) {
    if (start > cursor) parts.push(text.slice(cursor, start))
    parts.push(<mark key={start}>{text.slice(start, end)}</mark>)
    cursor = end
  }
  parts.push(text.slice(cursor))
  return <p>{parts}</p>
}

function App() {
  const [mode, setMode] = useState('search') // 'search' or 'crawl'
  const [query, setQuery] = useState('java search')
  const [searchMode, setSearchMode] = useState('keyword')
  const [suggestions, setSuggestions] = useState([])
  const [didYouMean, setDidYouMean] = useState(null)
  const [results, setResults] = useState([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [totalIsLowerBound, setTotalIsLowerBound] = useState(false)
  const [tookMs, setTookMs] = useState(null)
  const [loading, setLoading] = useState(false)
  const [crawlUrl, setCrawlUrl] = useState('https://example.com')
  const [crawlMaxPages, setCrawlMaxPages] = useState(5)
  const [crawlMaxDepth, setCrawlMaxDepth] = useState(1)
  const [crawlStatus, setCrawlStatus] = useState('')
  const [stats, setStats] = useState(null)

  const refreshStats = async () => {
    try {
      const response = await fetch(`${API}/stats`)
      const data = await response.json()
      setStats(data)
    } catch (error) {
      console.error('Failed to refresh stats:', error)
    }
  }

  // Load stats when component mounts
  useEffect(() => {
    refreshStats()
  }, [])

  // Load stats when switching to crawler tab
  useEffect(() => {
    if (mode === 'crawl') {
      refreshStats()
    }
  }, [mode])

  const runSearch = async (targetPage, text = query) => {
    setLoading(true)

    try {
      const params = new URLSearchParams({ q: text, page: targetPage, size: PAGE_SIZE, mode: searchMode })
      const response = await fetch(`${API}/search?${params}`)
      if (!response.ok) {
        const problem = await response.json().catch(() => ({}))
        throw new Error(problem.detail ?? `HTTP ${response.status}`)
      }

      const data = await response.json()
      setResults(data.results ?? [])
      setPage(data.page)
      setTotal(data.total)
      setTotalIsLowerBound(data.totalIsLowerBound)
      setTookMs(data.tookMs)
      setDidYouMean(data.didYouMean)
    } catch (error) {
      console.error('Search failed', error)
      setResults([])
      window.alert(`Search failed: ${error.message}. Is the backend running on ${API}?`)
    } finally {
      setLoading(false)
    }
  }

  const handleSearch = (event) => {
    event.preventDefault()
    runSearch(1)
  }

  const acceptCorrection = () => {
    setQuery(didYouMean)
    runSearch(1, didYouMean)
  }

  // Autocomplete: ask the backend for completions of the last word, debounced.
  useEffect(() => {
    if (!query.trim() || query.endsWith(' ')) return undefined
    const timer = setTimeout(async () => {
      try {
        const res = await fetch(`${API}/suggest?${new URLSearchParams({ q: query, limit: 8 })}`)
        if (res.ok) setSuggestions((await res.json()).suggestions)
      } catch {
        // suggestions are best-effort
      }
    }, 150)
    return () => clearTimeout(timer)
  }, [query])

  // With WAND pruning the total is a lower bound: allow "Next" while pages keep coming back full.
  const pageCount = Math.max(Math.ceil(total / PAGE_SIZE), totalIsLowerBound && results.length === PAGE_SIZE ? page + 1 : 0)

  const describeJob = (job) =>
    `${job.status === 'RUNNING' ? '⏳' : job.status === 'DONE' ? '✅' : '❌'} ${job.status.toLowerCase()} · ` +
    `fetched ${job.fetched}, stored ${job.stored}, duplicates ${job.duplicates}, ` +
    `blocked by robots.txt ${job.robotsBlocked}, errors ${job.errors}` +
    (job.error ? ` — ${job.error}` : '')

  const handleCrawl = async (event) => {
    event.preventDefault()
    setLoading(true)
    setCrawlStatus('Starting crawl…')

    try {
      const response = await fetch(`${API}/crawl`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          seedUrl: crawlUrl,
          maxPages: parseInt(crawlMaxPages),
          maxDepth: parseInt(crawlMaxDepth),
        }),
      })
      if (!response.ok) {
        const problem = await response.json().catch(() => ({}))
        throw new Error(problem.detail ?? `HTTP ${response.status}`)
      }

      // The crawl runs in the background (politely, ~1 page/s per host): poll its status.
      let job = await response.json()
      setCrawlStatus(describeJob(job))
      while (job.status === 'RUNNING') {
        await new Promise((resolve) => setTimeout(resolve, 1000))
        job = await (await fetch(`${API}/crawl/${job.jobId}`)).json()
        setCrawlStatus(describeJob(job))
      }
      await refreshStats()
      setResults([])
    } catch (error) {
      console.error('Crawl failed', error)
      setCrawlStatus(`❌ Crawl failed: ${error.message}`)
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <h1>🔍 Search Engine</h1>
        <div className="tabs">
          <button
            className={`tab ${mode === 'search' ? 'active' : ''}`}
            onClick={() => setMode('search')}
          >
            Search
          </button>
          <button
            className={`tab ${mode === 'crawl' ? 'active' : ''}`}
            onClick={() => setMode('crawl')}
          >
            Crawler
          </button>
        </div>
      </header>

      {mode === 'search' ? (
        <>
          <form className="search-bar" onSubmit={handleSearch}>
            <input
              type="search"
              value={query}
              list="query-suggestions"
              autoComplete="off"
              aria-label="Search query"
              onChange={(event) => setQuery(event.target.value)}
              placeholder='Search… try "phrases", +must -not, title:java, retriev*'
            />
            <datalist id="query-suggestions">
              {suggestions.map((s) => <option key={s} value={s} />)}
            </datalist>
            <select value={searchMode} onChange={(e) => setSearchMode(e.target.value)} aria-label="Search mode">
              <option value="keyword">Keyword (BM25F)</option>
              <option value="semantic">Semantic</option>
              <option value="hybrid">Hybrid</option>
            </select>
            <button type="submit" disabled={loading}>
              {loading ? 'Searching...' : 'Search'}
            </button>
          </form>

          <section className="results-panel">
            {didYouMean && (
              <p className="did-you-mean">
                Did you mean <button type="button" className="link-button" onClick={acceptCorrection}>{didYouMean}</button>?
              </p>
            )}
            {tookMs !== null && (
              <p className="result-summary">
                {totalIsLowerBound ? 'more than ' : ''}{total} result{total === 1 && !totalIsLowerBound ? '' : 's'} · {tookMs} ms
              </p>
            )}
            {results.length === 0 ? (
              <p className="empty-state">No results yet. Try a search.</p>
            ) : (
              results.map((result, index) => (
                <article key={result.id} className="result-card">
                  <div className="result-meta">
                    <span>#{(page - 1) * PAGE_SIZE + index + 1}</span>
                    <span>score {result.score.toFixed(2)}</span>
                  </div>
                  <h2>{result.title}</h2>
                  <a href={result.url} target="_blank" rel="noreferrer">
                    {result.url}
                  </a>
                  <Highlighted text={result.snippet} ranges={result.highlights} />
                </article>
              ))
            )}
            {pageCount > 1 && (
              <nav className="pager">
                <button type="button" disabled={page <= 1 || loading} onClick={() => runSearch(page - 1)}>
                  Previous
                </button>
                <span>Page {page}{totalIsLowerBound ? '' : ` of ${pageCount}`}</span>
                <button type="button" disabled={page >= pageCount || loading} onClick={() => runSearch(page + 1)}>
                  Next
                </button>
              </nav>
            )}
          </section>
        </>
      ) : (
        <>
          <form className="crawl-form" onSubmit={handleCrawl}>
            <div className="form-group">
              <label htmlFor="crawl-url">Website URL</label>
              <input
                id="crawl-url"
                type="url"
                value={crawlUrl}
                onChange={(event) => setCrawlUrl(event.target.value)}
                placeholder="https://example.com"
                required
              />
            </div>

            <div className="form-row">
              <div className="form-group">
                <label htmlFor="max-pages">Max Pages</label>
                <input
                  id="max-pages"
                  type="number"
                  value={crawlMaxPages}
                  onChange={(event) => setCrawlMaxPages(event.target.value)}
                  min="1"
                  max="500"
                />
              </div>

              <div className="form-group">
                <label htmlFor="max-depth">Max Depth</label>
                <input
                  id="max-depth"
                  type="number"
                  value={crawlMaxDepth}
                  onChange={(event) => setCrawlMaxDepth(event.target.value)}
                  min="0"
                  max="5"
                />
              </div>
            </div>

            <button type="submit" disabled={loading}>
              {loading ? 'Crawling...' : 'Start Crawl'}
            </button>
          </form>

          <section className="crawl-status">
            {crawlStatus && (
              <div className={`status-message ${crawlStatus.includes('❌') ? 'error' : 'success'}`}>
                {crawlStatus}
              </div>
            )}

            <div className="refresh-button-container">
              <button type="button" onClick={refreshStats} className="refresh-btn">
                🔄 Refresh Stats
              </button>
            </div>

            {stats && (
              <div className="stats-display">
                <h3>Corpus Statistics</h3>
                <div className="stats-grid">
                  <div className="stat">
                    <span className="label">Documents</span>
                    <span className="value">{stats.totalDocuments}</span>
                  </div>
                  <div className="stat">
                    <span className="label">Total Terms</span>
                    <span className="value">{stats.totalTerms}</span>
                  </div>
                  <div className="stat">
                    <span className="label">Unique Terms</span>
                    <span className="value">{stats.uniqueTerms}</span>
                  </div>
                </div>
              </div>
            )}
          </section>
        </>
      )}
    </main>
  )
}

export default App
