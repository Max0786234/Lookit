import { useCallback, useEffect, useState } from 'react'
import './App.css'

const API = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api'
const DOCS_URL = `${API.replace(/\/api$/, '')}/swagger-ui/index.html`
const PAGE_SIZE = 10

const MODES = {
  keyword: { label: 'Keyword', help: 'Matches your words (with stemming): best for exact terms, names and phrases.' },
  semantic: { label: 'Semantic', help: 'Matches meaning with a neural model: finds documents that use different words.' },
  hybrid: { label: 'Hybrid', help: 'Combines both rankings: usually the most reliable choice.' },
}

const SYNTAX = [
  ['"gene expression"', 'exact phrase'],
  ['vitamin -mice', 'exclude a word'],
  ['+cancer +therapy', 'require every word'],
  ['title:diabetes', 'search only titles'],
  ['immun*', 'words starting with…'],
  ['(covid OR influenza) AND vaccine', 'boolean logic'],
]

/** Snippet text with [start, end) highlight ranges rendered as <mark>, without innerHTML. */
function Highlighted({ text, ranges }) {
  const parts = []
  let cursor = 0
  for (const [start, end] of ranges ?? []) {
    if (start > cursor) parts.push(text.slice(cursor, start))
    parts.push(<mark key={start}>{text.slice(start, end)}</mark>)
    cursor = end
  }
  parts.push(text.slice(cursor))
  return <p className="snippet">{parts}</p>
}

function sourceOf(url) {
  if (url.startsWith('beir://scifact/')) return 'SciFact abstract'
  if (url.startsWith('beir://')) return 'Benchmark corpus'
  if (url.startsWith('local://')) return 'Sample article'
  try {
    return new URL(url).hostname
  } catch {
    return 'Document'
  }
}

const isWebUrl = (url) => /^https?:\/\//i.test(url)

async function getJson(path) {
  const response = await fetch(`${API}${path}`)
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}))
    throw new Error(problem.detail ?? `HTTP ${response.status}`)
  }
  return response.json()
}

function App() {
  const [tab, setTab] = useState('search')
  const [query, setQuery] = useState('')
  const [mode, setMode] = useState('hybrid')
  const [searched, setSearched] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [suggestions, setSuggestions] = useState([])
  const [stats, setStats] = useState(null)
  const [examples, setExamples] = useState([])
  const [reader, setReader] = useState(null)

  const refreshHome = useCallback(async () => {
    try {
      const s = await getJson('/stats')
      setStats(s)
      if (!s.semanticEnabled) setMode('keyword')
      setExamples(await getJson('/examples?count=6'))
    } catch {
      setError(`Cannot reach the search API at ${API}. Is the backend running?`)
    }
  }, [])

  useEffect(() => {
    // Initial load of corpus statistics and example queries from the API.
    refreshHome()
  }, [refreshHome])

  // Autocomplete: completions of the last word, debounced.
  useEffect(() => {
    if (!query.trim() || query.endsWith(' ')) return undefined
    const timer = setTimeout(async () => {
      try {
        setSuggestions((await getJson(`/suggest?${new URLSearchParams({ q: query, limit: 8 })}`)).suggestions)
      } catch {
        // suggestions are best-effort
      }
    }, 150)
    return () => clearTimeout(timer)
  }, [query])

  const runSearch = async (text, page = 1, searchMode = mode) => {
    if (!text.trim()) return
    setQuery(text)
    setTab('search')
    setReader(null)
    setLoading(true)
    setError('')
    try {
      const params = new URLSearchParams({ q: text, page, size: PAGE_SIZE, mode: searchMode })
      const data = await getJson(`/search?${params}`)
      setSearched({ ...data, query: text })
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch (e) {
      setError(`Search failed: ${e.message}`)
    } finally {
      setLoading(false)
    }
  }

  const openDocument = async (id) => {
    try {
      setReader(await getJson(`/documents/${id}`))
      window.scrollTo({ top: 0 })
    } catch (e) {
      setError(`Could not open the document: ${e.message}`)
    }
  }

  const goHome = () => {
    setSearched(null)
    setReader(null)
    setQuery('')
    setTab('search')
    refreshHome()
  }

  return (
    <div className="page">
      <header className="topbar">
        <button type="button" className="brand" onClick={goHome} aria-label="Lookit home">
          <span className="brand-mark" aria-hidden="true">◎</span> Lookit
        </button>
        <nav className="tabs" aria-label="Sections">
          <button type="button" className={`tab ${tab === 'search' ? 'active' : ''}`} onClick={() => setTab('search')}>
            Search
          </button>
          <button type="button" className={`tab ${tab === 'crawl' ? 'active' : ''}`} onClick={() => setTab('crawl')}>
            Add web pages
          </button>
        </nav>
      </header>

      <main>
        {tab === 'search' && (
          <SearchBox
            query={query}
            setQuery={setQuery}
            mode={mode}
            setMode={(m) => {
              setMode(m)
              if (searched) runSearch(searched.query, 1, m)
            }}
            semanticEnabled={stats?.semanticEnabled ?? true}
            suggestions={suggestions}
            loading={loading}
            onSearch={() => runSearch(query)}
          />
        )}

        {error && <div className="notice error" role="alert">{error}</div>}

        {tab === 'crawl' && <Crawler onDone={refreshHome} onSearch={(q) => runSearch(q)} />}

        {tab === 'search' && reader && <Reader doc={reader} onBack={() => setReader(null)} />}

        {tab === 'search' && !reader && searched && (
          <Results
            data={searched}
            loading={loading}
            onOpen={openDocument}
            onPage={(p) => runSearch(searched.query, p)}
            onCorrect={(q) => runSearch(q)}
          />
        )}

        {tab === 'search' && !reader && !searched && (
          <Home stats={stats} examples={examples} onTry={(q) => runSearch(q)} />
        )}
      </main>

      <footer className="footer">
        <span>Lookit — BM25F + WAND keyword search, MiniLM + HNSW semantic search, RRF hybrid.</span>
        <a href={DOCS_URL} target="_blank" rel="noreferrer">API documentation ↗</a>
      </footer>
    </div>
  )
}

function SearchBox({ query, setQuery, mode, setMode, semanticEnabled, suggestions, loading, onSearch }) {
  return (
    <section className="search-area">
      <form
        className="search-bar"
        role="search"
        onSubmit={(e) => {
          e.preventDefault()
          onSearch()
        }}
      >
        <input
          type="search"
          value={query}
          list="query-suggestions"
          autoComplete="off"
          aria-label="Search query"
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Ask a question or type keywords…"
          autoFocus
        />
        <datalist id="query-suggestions">
          {suggestions.map((s) => <option key={s} value={s} />)}
        </datalist>
        <button type="submit" disabled={loading || !query.trim()}>
          {loading ? 'Searching…' : 'Search'}
        </button>
      </form>
      <div className="modes" role="radiogroup" aria-label="Search mode">
        {Object.entries(MODES).map(([key, m]) => {
          const disabled = key !== 'keyword' && !semanticEnabled
          return (
            <button
              key={key}
              type="button"
              role="radio"
              aria-checked={mode === key}
              disabled={disabled}
              className={`mode ${mode === key ? 'active' : ''}`}
              title={disabled ? 'Needs the embedding model (scripts/fetch-model)' : m.help}
              onClick={() => setMode(key)}
            >
              {m.label}
            </button>
          )
        })}
        <span className="mode-help">{MODES[mode].help}</span>
      </div>
    </section>
  )
}

function Home({ stats, examples, onTry }) {
  const starters = ['Does vitamin D reduce fracture risk?', 'how do vaccines train the immune system', ...examples]
  return (
    <section className="home">
      <div className="card intro">
        <h1>Search {stats ? stats.totalDocuments.toLocaleString() : '…'} documents</h1>
        <p className="muted">
          A search engine built from scratch. Type a question in plain English, or use keywords and the
          syntax below. Click any result to read the full document.
        </p>
        {stats && stats.sources.length > 0 && (
          <ul className="sources" aria-label="What is in the index">
            {stats.sources.map((s) => (
              <li key={s.name}>
                <strong>{s.documents.toLocaleString()}</strong> {s.name}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="card">
        <h2>Try one of these</h2>
        <div className="chips">
          {starters.slice(0, 8).map((q) => (
            <button key={q} type="button" className="chip" onClick={() => onTry(q)}>{q}</button>
          ))}
        </div>
      </div>

      <div className="grid-2">
        <div className="card">
          <h2>Search modes</h2>
          <dl className="defs">
            {Object.values(MODES).map((m) => (
              <div key={m.label}>
                <dt>{m.label}</dt>
                <dd>{m.help}</dd>
              </div>
            ))}
          </dl>
        </div>
        <div className="card">
          <h2>Search syntax <span className="muted small">(keyword &amp; hybrid modes)</span></h2>
          <table className="syntax">
            <tbody>
              {SYNTAX.map(([example, meaning]) => (
                <tr key={example}>
                  <td>
                    <button type="button" className="code-link" onClick={() => onTry(example)}>{example}</button>
                  </td>
                  <td>{meaning}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  )
}

function Results({ data, loading, onOpen, onPage, onCorrect }) {
  const lastPage = data.totalIsLowerBound
    ? (data.results.length === PAGE_SIZE ? data.page + 1 : data.page)
    : Math.max(1, Math.ceil(data.total / PAGE_SIZE))
  return (
    <section className="results" aria-busy={loading}>
      {data.didYouMean && (
        <p className="did-you-mean">
          Did you mean{' '}
          <button type="button" className="link-button" onClick={() => onCorrect(data.didYouMean)}>
            {data.didYouMean}
          </button>
          ?
        </p>
      )}
      <p className="muted small">
        {data.totalIsLowerBound ? 'About ' : ''}{data.total.toLocaleString()} result{data.total === 1 ? '' : 's'}
        {' '}· {MODES[data.mode]?.label ?? data.mode} search · {data.tookMs} ms
      </p>

      {data.results.length === 0 ? (
        <div className="card empty">
          <h2>No results for “{data.query}”</h2>
          <p className="muted">
            Try fewer or more general words, switch to <strong>Semantic</strong> mode to match meaning instead of
            exact words, or add pages about this topic from the <strong>Add web pages</strong> tab.
          </p>
        </div>
      ) : (
        data.results.map((r, i) => (
          <article key={r.id} className="card result">
            <div className="result-meta">
              <span className="badge">{sourceOf(r.url)}</span>
              <span className="muted small">#{(data.page - 1) * PAGE_SIZE + i + 1}</span>
            </div>
            <h3>
              <button type="button" className="title-link" onClick={() => onOpen(r.id)}>{r.title}</button>
            </h3>
            <Highlighted text={r.snippet} ranges={r.highlights} />
            <div className="result-actions">
              <button type="button" className="link-button" onClick={() => onOpen(r.id)}>Read full document</button>
              {isWebUrl(r.url) && <a href={r.url} target="_blank" rel="noreferrer">Open original ↗</a>}
            </div>
          </article>
        ))
      )}

      {lastPage > 1 && (
        <nav className="pager" aria-label="Result pages">
          <button type="button" disabled={data.page <= 1 || loading} onClick={() => onPage(data.page - 1)}>
            ← Previous
          </button>
          <span className="muted">Page {data.page}{data.totalIsLowerBound ? '' : ` of ${lastPage}`}</span>
          <button type="button" disabled={data.page >= lastPage || loading} onClick={() => onPage(data.page + 1)}>
            Next →
          </button>
        </nav>
      )}
    </section>
  )
}

function Reader({ doc, onBack }) {
  return (
    <article className="card reader">
      <button type="button" className="link-button" onClick={onBack}>← Back to results</button>
      <div className="result-meta">
        <span className="badge">{sourceOf(doc.url)}</span>
      </div>
      <h1>{doc.title}</h1>
      {isWebUrl(doc.url) && (
        <p><a href={doc.url} target="_blank" rel="noreferrer">{doc.url} ↗</a></p>
      )}
      <div className="reader-body">
        {doc.content.split(/\n{2,}/).map((para, i) => <p key={i}>{para}</p>)}
      </div>
    </article>
  )
}

function Crawler({ onDone, onSearch }) {
  const [url, setUrl] = useState('https://en.wikipedia.org/wiki/Search_engine')
  const [maxPages, setMaxPages] = useState(10)
  const [maxDepth, setMaxDepth] = useState(1)
  const [job, setJob] = useState(null)
  const [error, setError] = useState('')
  const running = job?.status === 'RUNNING'

  const start = async (e) => {
    e.preventDefault()
    setError('')
    try {
      const response = await fetch(`${API}/crawl`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ seedUrl: url, maxPages: Number(maxPages), maxDepth: Number(maxDepth) }),
      })
      if (!response.ok) {
        const problem = await response.json().catch(() => ({}))
        throw new Error(problem.detail ?? `HTTP ${response.status}`)
      }
      let current = await response.json()
      setJob(current)
      while (current.status === 'RUNNING') {
        await new Promise((resolve) => setTimeout(resolve, 1000))
        current = await getJson(`/crawl/${current.jobId}`)
        setJob(current)
      }
      onDone()
    } catch (err) {
      setError(err.message)
    }
  }

  const topic = (() => {
    try {
      return decodeURIComponent(new URL(url).pathname.split('/').filter(Boolean).pop() ?? '').replace(/[_-]+/g, ' ')
    } catch {
      return ''
    }
  })()

  return (
    <section className="card crawler">
      <h1>Add web pages to the index</h1>
      <p className="muted">
        Give a starting page. The crawler downloads it, follows links <em>on the same site</em> up to the depth you
        choose, and makes every page searchable. It respects each site&apos;s <code>robots.txt</code>, waits at least
        one second between requests to the same site, and skips near-duplicate pages.
      </p>
      <form className="crawl-form" onSubmit={start}>
        <label>
          Start page
          <input type="url" value={url} onChange={(e) => setUrl(e.target.value)} required />
        </label>
        <div className="row">
          <label>
            Pages to fetch (1–500)
            <input type="number" min="1" max="500" value={maxPages} onChange={(e) => setMaxPages(e.target.value)} />
          </label>
          <label>
            Link depth (0–5)
            <input type="number" min="0" max="5" value={maxDepth} onChange={(e) => setMaxDepth(e.target.value)} />
          </label>
        </div>
        <p className="muted small">Depth 0 = only the start page. Depth 1 = it plus the pages it links to.</p>
        <button type="submit" className="primary" disabled={running}>
          {running ? 'Crawling…' : 'Start crawl'}
        </button>
      </form>

      {error && <div className="notice error" role="alert">Crawl failed: {error}</div>}

      {job && (
        <div className={`notice ${job.status === 'DONE' ? 'success' : job.status === 'RUNNING' ? 'info' : 'error'}`}>
          <strong>{job.status === 'RUNNING' ? 'Crawling…' : job.status === 'DONE' ? 'Finished' : job.status}</strong>
          <ul className="job-stats">
            <li>{job.fetched} fetched</li>
            <li>{job.stored} added to the index</li>
            <li>{job.duplicates} duplicates skipped</li>
            <li>{job.robotsBlocked} blocked by robots.txt</li>
            <li>{job.errors} errors</li>
          </ul>
          {job.error && <p>{job.error}</p>}
          {job.status === 'DONE' && job.stored > 0 && topic && (
            <button type="button" className="primary" onClick={() => onSearch(topic)}>
              Search the new pages for “{topic}”
            </button>
          )}
          {job.status === 'DONE' && job.stored === 0 && (
            <p>No new pages were added — they may already be indexed, blocked by robots.txt, or not HTML.</p>
          )}
        </div>
      )}
    </section>
  )
}

export default App
