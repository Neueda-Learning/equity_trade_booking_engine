import { useEffect, useState, type FormEvent } from 'react'
import {
  ApiProblemError,
  getAccounts,
  getMarketQuote,
  getMarketQuotes,
  refreshMarketQuote,
  type Account,
  type MarketQuote,
} from '../api'
import './MarketData.css'

function MarketData() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [accountId, setAccountId] = useState('')
  const [quotes, setQuotes] = useState<MarketQuote[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [unavailable, setUnavailable] = useState(false)
  const [search, setSearch] = useState('')
  const [searching, setSearching] = useState(false)
  const [searchResult, setSearchResult] = useState<MarketQuote | null>(null)
  const [refreshingTicker, setRefreshingTicker] = useState<string | null>(null)
  const [message, setMessage] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    getAccounts(controller.signal)
      .then(setAccounts)
      .catch((reason: unknown) => {
        if (!(reason instanceof DOMException && reason.name === 'AbortError')) {
          setError('Accounts are unavailable.')
        }
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    getMarketQuotes(accountId || undefined, controller.signal)
      .then((result) => setQuotes(result.items))
      .catch((reason: unknown) => handleError(reason))
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [accountId])

  function handleError(reason: unknown) {
    if (reason instanceof DOMException && reason.name === 'AbortError') return
    if (reason instanceof ApiProblemError && reason.problem.status === 503) {
      setUnavailable(true)
      return
    }
    setError(
      reason instanceof ApiProblemError
        ? reason.problem.detail ?? reason.message
        : 'Market data could not be loaded.',
    )
  }

  async function submitSearch(event: FormEvent) {
    event.preventDefault()
    setSearching(true)
    setError('')
    setUnavailable(false)
    setSearchResult(null)
    try {
      setSearchResult(await getMarketQuote(search))
    } catch (reason) {
      handleError(reason)
    } finally {
      setSearching(false)
    }
  }

  async function refresh(quote: MarketQuote) {
    setRefreshingTicker(quote.ticker)
    setMessage('')
    setError('')
    setUnavailable(false)
    try {
      const refreshed = await refreshMarketQuote(quote.ticker)
      setQuotes((current) =>
        current.map((item) =>
          item.ticker === refreshed.ticker ? refreshed : item,
        ),
      )
      if (searchResult?.ticker === refreshed.ticker) {
        setSearchResult(refreshed)
      }
      setMessage(`${refreshed.ticker} mock quote refreshed.`)
    } catch (reason) {
      handleError(reason)
    } finally {
      setRefreshingTicker(null)
    }
  }

  return (
    <section aria-labelledby="market-heading">
      <p className="section-kicker">Market Data</p>
      <div className="market-heading">
        <div>
          <h2 id="market-heading">Market Data</h2>
          <p className="mock-disclosure">
            MOCK DATA — generated locally for development and testing. This is
            not live market data.
          </p>
        </div>
        <label>
          Account
          <select
            value={accountId}
            onChange={(event) => {
              setLoading(true)
              setError('')
              setUnavailable(false)
              setAccountId(event.target.value)
              setSearchResult(null)
            }}
          >
            <option value="">All Accounts</option>
            {accounts.map((account) => (
              <option key={account.id} value={account.id}>
                {account.name}
              </option>
            ))}
          </select>
        </label>
      </div>

      <form className="market-search" onSubmit={submitSearch}>
        <label>
          Ticker search
          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="AAPL"
            maxLength={10}
            required
          />
        </label>
        <button type="submit" disabled={searching}>
          {searching ? 'Searching…' : 'Search'}
        </button>
      </form>

      {message && <p className="notice notice--success">{message}</p>}
      {loading && <p className="table-state">Loading market data…</p>}
      {unavailable && (
        <p className="table-state table-state--error" role="alert">
          Market data is currently unavailable.
        </p>
      )}
      {error && (
        <p className="table-state table-state--error" role="alert">
          {error}
        </p>
      )}
      {searchResult && (
        <div className="panel market-search-result">
          <h3>Search result</h3>
          <QuoteTable
            quotes={[searchResult]}
            refreshingTicker={refreshingTicker}
            onRefresh={refresh}
          />
        </div>
      )}
      {!loading && !error && !unavailable && quotes.length === 0 && (
        <p className="table-state">No open positions to quote.</p>
      )}
      {!loading && !error && !unavailable && quotes.length > 0 && (
        <div className="panel">
          <h3>Position quotes</h3>
          <QuoteTable
            quotes={quotes}
            refreshingTicker={refreshingTicker}
            onRefresh={refresh}
          />
        </div>
      )}
    </section>
  )
}

function QuoteTable({
  quotes,
  refreshingTicker,
  onRefresh,
}: {
  quotes: MarketQuote[]
  refreshingTicker: string | null
  onRefresh: (quote: MarketQuote) => Promise<void>
}) {
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            <th>Ticker</th>
            <th>Price</th>
            <th>Previous close</th>
            <th>Change</th>
            <th>Change %</th>
            <th>Fetched</th>
            <th>Labels</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          {quotes.map((quote) => (
            <tr key={quote.ticker}>
              <td className="ticker-cell">{quote.ticker}</td>
              <td>{formatNumber(quote.price)}</td>
              <td>{formatNumber(quote.previousClose)}</td>
              <td>{formatSigned(quote.change)}</td>
              <td>{formatSigned(quote.changePercent)}%</td>
              <td>{new Date(quote.fetchedAt).toLocaleString()}</td>
              <td>
                <div className="quote-labels">
                  {quote.mock && <span>MOCK</span>}
                  {quote.cached && <span>CACHED</span>}
                  {quote.stale && <span className="label-warning">STALE</span>}
                </div>
              </td>
              <td>
                <button
                  type="button"
                  className="button-secondary"
                  disabled={refreshingTicker === quote.ticker}
                  onClick={() => void onRefresh(quote)}
                >
                  {refreshingTicker === quote.ticker
                    ? 'Refreshing…'
                    : `Refresh ${quote.ticker}`}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function formatNumber(value: number) {
  return value.toLocaleString(undefined, { maximumFractionDigits: 6 })
}

function formatSigned(value: number) {
  return `${value > 0 ? '+' : ''}${formatNumber(value)}`
}

export default MarketData
