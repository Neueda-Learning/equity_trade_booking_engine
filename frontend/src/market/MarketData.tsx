import { useEffect, useState, type FormEvent } from 'react'
import {
  ApiProblemError,
  disableDemoMarketDataOutage,
  enableDemoMarketDataOutage,
  getAccounts,
  getDemoMarketDataOutage,
  getMarketDataProviderStatus,
  getMarketQuote,
  getMarketQuotes,
  refreshMarketQuote,
  type Account,
  type DemoOutage,
  type MarketDataProviderStatus,
  type MarketQuote,
} from '../api'
import {
  formatDateTime,
  formatDecimal,
  formatSignedDecimal,
} from '../format'
import './MarketData.css'

function MarketData() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [accountId, setAccountId] = useState('')
  const [quotes, setQuotes] = useState<MarketQuote[]>([])
  const [providerStatus, setProviderStatus] =
    useState<MarketDataProviderStatus | null>(null)
  const [demoOutage, setDemoOutage] = useState<DemoOutage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [unavailable, setUnavailable] = useState(false)
  const [search, setSearch] = useState('')
  const [searching, setSearching] = useState(false)
  const [searchResult, setSearchResult] = useState<MarketQuote | null>(null)
  const [refreshingTicker, setRefreshingTicker] = useState<string | null>(null)
  const [demoChanging, setDemoChanging] = useState(false)
  const [message, setMessage] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([
      getAccounts(controller.signal),
      getMarketDataProviderStatus(controller.signal),
    ])
      .then(([loadedAccounts, status]) => {
        setAccounts(loadedAccounts)
        setProviderStatus(status)
        if (status.demoControlsEnabled) {
          void getDemoMarketDataOutage(controller.signal)
            .then(setDemoOutage)
            .catch((reason: unknown) => {
              if (!isAbort(reason)) {
                setError('Demo controls are unavailable.')
              }
            })
        }
      })
      .catch((reason: unknown) => {
        if (!isAbort(reason)) setError('Market data status is unavailable.')
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
    if (isAbort(reason)) return
    if (reason instanceof ApiProblemError && reason.problem.status === 503) {
      setUnavailable(true)
      setError(providerError(reason))
      return
    }
    setError(
      reason instanceof ApiProblemError
        ? reason.problem.detail ?? reason.message
        : 'Market data could not be loaded.',
    )
  }

  async function reloadProviderStatus() {
    setProviderStatus(await getMarketDataProviderStatus())
  }

  async function submitSearch(event: FormEvent) {
    event.preventDefault()
    setSearching(true)
    setError('')
    setUnavailable(false)
    setSearchResult(null)
    try {
      setSearchResult(await getMarketQuote(search))
      await reloadProviderStatus()
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
      setMessage(
        `${refreshed.ticker} ${refreshed.source} quote refreshed.`,
      )
      await reloadProviderStatus()
    } catch (reason) {
      handleError(reason)
      await reloadProviderStatus().catch(() => undefined)
    } finally {
      setRefreshingTicker(null)
    }
  }

  async function setOutage(enabled: boolean) {
    setDemoChanging(true)
    setError('')
    setMessage('')
    try {
      const status = enabled
        ? await enableDemoMarketDataOutage()
        : await disableDemoMarketDataOutage()
      setDemoOutage(status)
      await reloadProviderStatus()
      setMessage(status.message)
    } catch (reason) {
      handleError(reason)
    } finally {
      setDemoChanging(false)
    }
  }

  return (
    <section aria-labelledby="market-heading">
      <p className="section-kicker">Market Data</p>
      <div className="market-heading">
        <div>
          <h2 id="market-heading">Market Data</h2>
          <ProviderDisclosure status={providerStatus} />
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

      {providerStatus?.demoControlsEnabled && demoOutage && (
        <DemoControls
          outage={demoOutage}
          changing={demoChanging}
          onChange={setOutage}
        />
      )}

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
          {error || 'Market data is currently unavailable.'}
        </p>
      )}
      {!unavailable && error && (
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

function ProviderDisclosure({
  status,
}: {
  status: MarketDataProviderStatus | null
}) {
  if (!status) {
    return <p className="provider-disclosure">Loading provider status…</p>
  }
  if (status.provider === 'MOCK') {
    return (
      <p className="provider-disclosure provider-disclosure--mock">
        <strong>Source: MOCK</strong> — generated locally; not live market
        data. Last successful update:{' '}
        {status.lastSuccessAt
          ? formatDateTime(status.lastSuccessAt)
          : 'Not yet'}
      </p>
    )
  }
  return (
    <div className="provider-disclosure provider-disclosure--live">
      <strong>Source: FINNHUB</strong>
      <span>Live provider configured</span>
      <span>
        Last successful update:{' '}
        {status.lastSuccessAt
          ? formatDateTime(status.lastSuccessAt)
          : 'Not yet'}
      </span>
      {status.lastFailureCategory && (
        <span>Last failure: {friendlyCategory(status.lastFailureCategory)}</span>
      )}
    </div>
  )
}

function DemoControls({
  outage,
  changing,
  onChange,
}: {
  outage: DemoOutage
  changing: boolean
  onChange: (enabled: boolean) => Promise<void>
}) {
  return (
    <div className="demo-controls">
      <div>
        <strong>Demo Only</strong>
        <span>
          Provider outage: {outage.enabled ? 'SIMULATED' : 'OFF'}
        </span>
      </div>
      <button
        type="button"
        disabled={changing || outage.enabled}
        onClick={() => void onChange(true)}
      >
        {changing ? 'Changing…' : 'Simulate outage'}
      </button>
      <button
        type="button"
        className="button-secondary"
        disabled={changing || !outage.enabled}
        onClick={() => void onChange(false)}
      >
        Restore provider
      </button>
    </div>
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
            <th>Quote time</th>
            <th>Updated</th>
            <th>Labels</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          {quotes.map((quote) => (
            <tr key={quote.ticker}>
              <td className="ticker-cell">{quote.ticker}</td>
              <td>{formatDecimal(quote.price)}</td>
              <td>{formatDecimal(quote.previousClose)}</td>
              <td>{formatSignedDecimal(quote.change)}</td>
              <td>{formatSignedDecimal(quote.changePercent)}%</td>
              <td>{formatDateTime(quote.marketTimestamp)}</td>
              <td>
                {quote.stale ? 'Last successful update ' : ''}
                {formatDateTime(quote.fetchedAt)}
              </td>
              <td>
                <div className="quote-labels">
                  <span>{quote.source}</span>
                  {quote.source === 'FINNHUB' &&
                    !quote.cached &&
                    !quote.stale && <span className="label-live">LIVE</span>}
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

function providerError(error: ApiProblemError) {
  const reason = error.problem.errors?.provider
  if (reason === 'provider timeout') {
    return 'Live market data timed out and no cached quote is available.'
  }
  if (reason === 'provider rate limit') {
    return 'The live provider rate limit was reached. Try again later.'
  }
  if (reason === 'DEMO outage enabled') {
    return 'Demo outage is enabled and no cached quote is available.'
  }
  return error.problem.detail ?? 'Market data is currently unavailable.'
}

function friendlyCategory(category: string) {
  return category.toLowerCase().replaceAll('_', ' ')
}

function isAbort(reason: unknown) {
  return reason instanceof DOMException && reason.name === 'AbortError'
}

export default MarketData
