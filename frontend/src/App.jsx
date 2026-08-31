import { useState, useEffect } from 'react'
import './App.css'

function App() {
  const [mode, setMode] = useState('search') // 'search' or 'crawl'
  const [query, setQuery] = useState('java search')
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [crawlUrl, setCrawlUrl] = useState('https://example.com')
  const [crawlMaxPages, setCrawlMaxPages] = useState(5)
  const [crawlMaxDepth, setCrawlMaxDepth] = useState(1)
  const [crawlStatus, setCrawlStatus] = useState('')
  const [stats, setStats] = useState(null)

  const refreshStats = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/stats')
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

  const handleSearch = async (event) => {
    event.preventDefault()
    setLoading(true)

    try {
      const response = await fetch(`http://localhost:8080/api/search?q=${encodeURIComponent(query)}`)
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      const data = await response.json()
      setResults(data.results ?? [])
    } catch (error) {
      console.error('Search failed', error)
      setResults([])
      window.alert('Search failed. Make sure the backend is running on http://localhost:8080')
    } finally {
      setLoading(false)
    }
  }

  const handleCrawl = async (event) => {
    event.preventDefault()
    setLoading(true)
    setCrawlStatus('Crawling in progress...')

    try {
      const response = await fetch('http://localhost:8080/api/crawl', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          seedUrl: crawlUrl,
          maxPages: parseInt(crawlMaxPages),
          maxDepth: parseInt(crawlMaxDepth),
        }),
        signal: AbortSignal.timeout(60000), // 60 second timeout
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      const data = await response.json()
      setCrawlStatus(`✅ Crawled ${data.crawledPages} pages from ${data.seedUrl}`)

      // Fetch updated stats
      try {
        const statsResponse = await fetch('http://localhost:8080/api/stats')
        const statsData = await statsResponse.json()
        setStats(statsData)
      } catch (statsError) {
        console.error('Failed to fetch stats:', statsError)
      }

      // Clear search results to show crawler ran successfully
      setResults([])
    } catch (error) {
      console.error('Crawl failed', error)
      setCrawlStatus(`❌ Crawl failed: ${error.message}. Try a simpler URL like https://example.com`)
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
              type="text"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search documents..."
            />
            <button type="submit" disabled={loading}>
              {loading ? 'Searching...' : 'Search'}
            </button>
          </form>

          <section className="results-panel">
            {results.length === 0 ? (
              <p className="empty-state">No results yet. Try a search.</p>
            ) : (
              results.map((result, index) => (
                <article key={`${result.url}-${index}`} className="result-card">
                  <div className="result-meta">
                    <span>#{index + 1}</span>
                    <span>score {result.score.toFixed(2)}</span>
                  </div>
                  <h2>{result.title}</h2>
                  <a href={result.url} target="_blank" rel="noreferrer">
                    {result.url}
                  </a>
                  <p>{result.snippet}</p>
                </article>
              ))
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
                  max="100"
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
                  max="3"
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
