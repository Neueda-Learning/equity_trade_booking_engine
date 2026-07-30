import { useCallback, useEffect, useState, type FormEvent } from 'react'
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
import { useI18n } from '../i18n'
import './MarketData.css'

const SEARCHED_TICKERS_KEY = 'equity-market-searched-tickers'
const TICKER_PATTERN = /^[A-Z][A-Z0-9.-]{0,9}$/

interface UnavailableQuote {
  ticker: string
  message: string
}

function MarketData() {
  const { t } = useI18n()
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
  const [searchedQuotes, setSearchedQuotes] = useState<MarketQuote[]>([])
  const [searchFailures, setSearchFailures] =
    useState<Record<string, string>>({})
  const [refreshingTicker, setRefreshingTicker] = useState<string | null>(null)
  const [refreshingAll, setRefreshingAll] = useState(false)
  const [refreshAllError, setRefreshAllError] = useState('')
  const [demoChanging, setDemoChanging] = useState(false)
  const [message, setMessage] = useState('')

  const handleError = useCallback((reason: unknown) => {
    if (isAbort(reason)) return
    setUnavailable(
      reason instanceof ApiProblemError && reason.problem.status === 503,
    )
    setError(marketError(reason, t))
  }, [t])

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
                setError(t('market.controlsUnavailable'))
              }
            })
        }
      })
      .catch((reason: unknown) => {
        if (!isAbort(reason)) setError(t('market.statusUnavailable'))
      })
    return () => controller.abort()
  }, [t])

  useEffect(() => {
    const controller = new AbortController()
    getMarketQuotes(accountId || undefined, controller.signal)
      .then((result) => setQuotes(result.items))
      .catch((reason: unknown) => handleError(reason))
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [accountId, handleError])

  useEffect(() => {
    const tickers = loadSearchedTickers()
    if (tickers.length === 0) return
    const controller = new AbortController()
    void Promise.allSettled(
      tickers.map((ticker) => getMarketQuote(ticker, controller.signal)),
    ).then((results) => {
      if (controller.signal.aborted) return
      const loaded: MarketQuote[] = []
      const failures: Record<string, string> = {}
      results.forEach((result, index) => {
        const ticker = tickers[index]
        if (result.status === 'fulfilled') {
          loaded.push(result.value)
        } else if (!isAbort(result.reason)) {
          failures[ticker] = marketError(result.reason, t)
        }
      })
      setSearchedQuotes(loaded)
      setSearchFailures(failures)
    })
    return () => controller.abort()
  }, [t])

  async function reloadProviderStatus() {
    setProviderStatus(await getMarketDataProviderStatus())
  }

  async function submitSearch(event: FormEvent) {
    event.preventDefault()
    const ticker = search.trim().toUpperCase()
    setSearching(true)
    if (TICKER_PATTERN.test(ticker)) {
      rememberSearchedTicker(ticker)
    }
    try {
      const quote = await getMarketQuote(ticker)
      setSearchedQuotes((current) => upsertQuote(current, quote))
      setSearchFailures((current) => withoutKey(current, ticker))
      await reloadProviderStatus()
    } catch (reason) {
      if (!isAbort(reason)) {
        setSearchFailures((current) => ({
          ...current,
          [ticker]: marketError(reason, t),
        }))
      }
    } finally {
      setSearching(false)
    }
  }

  async function refresh(quote: MarketQuote) {
    setRefreshingTicker(quote.ticker)
    setMessage('')
    setRefreshAllError('')
    setError('')
    setUnavailable(false)
    try {
      const refreshed = await refreshMarketQuote(quote.ticker)
      setQuotes((current) =>
        current.map((item) =>
          item.ticker === refreshed.ticker ? refreshed : item,
        ),
      )
      setSearchedQuotes((current) =>
        current.some((item) => item.ticker === refreshed.ticker)
          ? upsertQuote(current, refreshed)
          : current,
      )
      setSearchFailures((current) =>
        withoutKey(current, refreshed.ticker),
      )
      setMessage(
        t('market.refreshed', {
          ticker: refreshed.ticker,
          source: refreshed.source,
        }),
      )
      await reloadProviderStatus()
    } catch (reason) {
      const searched =
        searchedQuotes.some((item) => item.ticker === quote.ticker)
        || searchFailures[quote.ticker] !== undefined
      if (searched && !isAbort(reason)) {
        setSearchFailures((current) => ({
          ...current,
          [quote.ticker]: marketError(reason, t),
        }))
      } else {
        handleError(reason)
      }
      await reloadProviderStatus().catch(() => undefined)
    } finally {
      setRefreshingTicker(null)
    }
  }

  async function refreshAll() {
    setRefreshingAll(true)
    setMessage('')
    setRefreshAllError('')
    setError('')
    setUnavailable(false)
    try {
      const results = await Promise.allSettled(
        quotes.map((quote) => refreshMarketQuote(quote.ticker)),
      )
      const refreshedQuotes = results.flatMap((result) =>
        result.status === 'fulfilled' ? [result.value] : [],
      )
      const refreshedByTicker = new Map(
        refreshedQuotes.map((quote) => [quote.ticker, quote]),
      )
      setQuotes((current) =>
        current.map((quote) => refreshedByTicker.get(quote.ticker) ?? quote),
      )
      setSearchedQuotes((current) =>
        current.map(
          (quote) => refreshedByTicker.get(quote.ticker) ?? quote,
        ),
      )

      const failedCount = results.length - refreshedQuotes.length
      if (failedCount === 0) {
        setMessage(t('market.allRefreshed', {
          count: refreshedQuotes.length,
        }))
      } else if (refreshedQuotes.length === 0) {
        setRefreshAllError(t('market.refreshAllFailed', {
          failed: failedCount,
        }))
      } else {
        setRefreshAllError(t('market.refreshAllPartial', {
          success: refreshedQuotes.length,
          failed: failedCount,
        }))
      }
      await reloadProviderStatus().catch(() => undefined)
    } finally {
      setRefreshingAll(false)
    }
  }

  async function refreshVisibleQuotes() {
    const searchedTickers = new Set([
      ...searchedQuotes.map((quote) => quote.ticker),
      ...Object.keys(searchFailures),
    ])
    const tickers = Array.from(
      new Set([
        ...quotes.map((quote) => quote.ticker),
        ...searchedTickers,
      ]),
    )
    const results = await Promise.allSettled(
      tickers.map((ticker) => refreshMarketQuote(ticker)),
    )
    const refreshedByTicker = new Map<string, MarketQuote>()
    let firstFailure: unknown

    const failedSearches: Record<string, string> = {}
    results.forEach((result, index) => {
      const ticker = tickers[index]
      if (result.status === 'fulfilled') {
        refreshedByTicker.set(result.value.ticker, result.value)
      } else if (searchedTickers.has(ticker)) {
        failedSearches[ticker] = marketError(result.reason, t)
      } else if (firstFailure === undefined) {
        firstFailure = result.reason
      }
    })

    if (refreshedByTicker.size > 0) {
      setQuotes((current) =>
        current.map(
          (quote) => refreshedByTicker.get(quote.ticker) ?? quote,
        ),
      )
      setSearchedQuotes((current) =>
        Array.from(searchedTickers).reduce(
          (updated, ticker) => {
            const refreshed = refreshedByTicker.get(ticker)
            return refreshed ? upsertQuote(updated, refreshed) : updated
          },
          current,
        ),
      )
    }
    setSearchFailures((current) => {
      let updated = current
      searchedTickers.forEach((ticker) => {
        if (refreshedByTicker.has(ticker)) {
          updated = withoutKey(updated, ticker)
        }
      })
      return { ...updated, ...failedSearches }
    })
    if (firstFailure !== undefined) {
      handleError(firstFailure)
    }
    return refreshedByTicker.size
  }

  async function setOutage(enabled: boolean) {
    setDemoChanging(true)
    setError('')
    setUnavailable(false)
    setMessage('')
    try {
      const status = enabled
        ? await enableDemoMarketDataOutage()
        : await disableDemoMarketDataOutage()
      setDemoOutage(status)
      const refreshedCount = await refreshVisibleQuotes()
      await reloadProviderStatus()
      setMessage(
        t(
          status.enabled && refreshedCount > 0
            ? 'market.outageFallbackShown'
            : status.enabled
              ? 'market.outageEnabled'
              : refreshedCount > 0
                ? 'market.outageRestored'
                : 'market.outageDisabled',
        ),
      )
    } catch (reason) {
      handleError(reason)
    } finally {
      setDemoChanging(false)
    }
  }

  const unavailableSearches = Object.entries(searchFailures).map(
    ([ticker, failure]) => ({ ticker, message: failure }),
  )

  return (
    <section aria-labelledby="market-heading">
      <p className="section-kicker">{t('market.kicker')}</p>
      <div className="market-heading">
        <div>
          <h2 id="market-heading">{t('nav.marketData')}</h2>
          <ProviderDisclosure status={providerStatus} />
        </div>
        <label>
          {t('common.account')}
          <select
            value={accountId}
            onChange={(event) => {
              setLoading(true)
              setError('')
              setRefreshAllError('')
              setUnavailable(false)
              setAccountId(event.target.value)
            }}
          >
            <option value="">{t('common.allAccounts')}</option>
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
          {t('market.searchLabel')}
          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="AAPL"
            maxLength={10}
            required
          />
        </label>
        <button type="submit" disabled={searching}>
          {searching ? t('market.searching') : t('market.search')}
        </button>
      </form>

      {message && <p className="notice notice--success">{message}</p>}
      {refreshAllError && (
        <p className="notice notice--error" role="alert">
          {refreshAllError}
        </p>
      )}
      {loading && <p className="table-state">{t('market.loading')}</p>}
      {unavailable && (
        <p className="table-state table-state--error" role="alert">
          {error || t('market.currentlyUnavailable')}
        </p>
      )}
      {!unavailable && error && (
        <p className="table-state table-state--error" role="alert">
          {error}
        </p>
      )}
      {(searchedQuotes.length > 0 || unavailableSearches.length > 0) && (
        <div className="panel market-search-result">
          <h3>{t('market.searchResult')}</h3>
          <QuoteTable
            quotes={searchedQuotes}
            unavailableQuotes={unavailableSearches}
            refreshingTicker={refreshingTicker}
            refreshingAll={refreshingAll}
            controlsLocked={demoChanging}
            onRefresh={refresh}
          />
        </div>
      )}
      {!loading && !error && quotes.length === 0 && (
        <p className="table-state">{t('market.noPositions')}</p>
      )}
      {!loading && quotes.length > 0 && (
        <div className="panel">
          <div className="market-quotes-heading">
            <h3>{t('market.positionQuotes')}</h3>
            <button
              type="button"
              className="market-refresh-all"
              disabled={
                demoChanging || refreshingAll || refreshingTicker !== null
              }
              onClick={() => void refreshAll()}
            >
              {refreshingAll
                ? t('market.refreshingAll')
                : t('market.refreshAll')}
            </button>
          </div>
          <QuoteTable
            quotes={quotes}
            refreshingTicker={refreshingTicker}
            refreshingAll={refreshingAll}
            controlsLocked={demoChanging}
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
  const { locale, t } = useI18n()
  if (!status) {
    return <p className="provider-disclosure">{t('market.providerLoading')}</p>
  }
  if (status.provider === 'MOCK') {
    return (
      <p className="provider-disclosure provider-disclosure--mock">
        {t('market.mockDisclosure', {
          date: status.lastSuccessAt
            ? formatDateTime(status.lastSuccessAt, locale)
            : t('common.notYet'),
        })}
      </p>
    )
  }
  return (
    <div className="provider-disclosure provider-disclosure--live">
      <strong>{t('market.sourceFinnhub')}</strong>
      <span>{t('market.liveConfigured')}</span>
      <span>
        {t('market.lastSuccessful', {
          date: status.lastSuccessAt
            ? formatDateTime(status.lastSuccessAt, locale)
            : t('common.notYet'),
        })}
      </span>
      {status.lastFailureCategory && (
        <span>
          {t('market.lastFailure', {
            category: friendlyCategory(status.lastFailureCategory),
          })}
        </span>
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
  const { t } = useI18n()
  return (
    <div className="demo-controls">
      <div>
        <strong>{t('market.demoOnly')}</strong>
        <span>
          {t('market.outage', {
            state: outage.enabled ? t('market.simulated') : t('market.off'),
          })}
        </span>
      </div>
      <button
        type="button"
        disabled={changing || outage.enabled}
        onClick={() => void onChange(true)}
      >
        {changing ? t('market.changing') : t('market.simulate')}
      </button>
      <button
        type="button"
        className="button-secondary"
        disabled={changing || !outage.enabled}
        onClick={() => void onChange(false)}
      >
        {t('market.restore')}
      </button>
    </div>
  )
}

function QuoteTable({
  quotes,
  unavailableQuotes = [],
  refreshingTicker,
  refreshingAll,
  controlsLocked,
  onRefresh,
}: {
  quotes: MarketQuote[]
  unavailableQuotes?: UnavailableQuote[]
  refreshingTicker: string | null
  refreshingAll: boolean
  controlsLocked: boolean
  onRefresh: (quote: MarketQuote) => Promise<void>
}) {
  const { locale, t } = useI18n()
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            <th>{t('common.ticker')}</th>
            <th>{t('common.price')}</th>
            <th>{t('market.previousClose')}</th>
            <th>{t('market.change')}</th>
            <th>{t('market.changePercent')}</th>
            <th>{t('market.quoteTime')}</th>
            <th>{t('market.updated')}</th>
            <th>{t('market.labels')}</th>
            <th>{t('market.action')}</th>
          </tr>
        </thead>
        <tbody>
          {quotes.map((quote) => (
            <tr key={quote.ticker}>
              <td className="ticker-cell">{quote.ticker}</td>
              <td>{formatDecimal(quote.price, locale)}</td>
              <td>{formatDecimal(quote.previousClose, locale)}</td>
              <td>{formatSignedDecimal(quote.change, locale)}</td>
              <td>{formatSignedDecimal(quote.changePercent, locale)}%</td>
              <td>{formatDateTime(quote.marketTimestamp, locale)}</td>
              <td>
                {quote.stale ? t('market.lastSuccessfulPrefix') : ''}
                {formatDateTime(quote.fetchedAt, locale)}
              </td>
              <td>
                <div className="quote-labels">
                  <span>{quote.source}</span>
                  {quote.source === 'FINNHUB' &&
                    !quote.cached &&
                    !quote.stale && (
                      <span className="label-live">{t('status.live')}</span>
                    )}
                  {quote.mock && <span>{t('status.mock')}</span>}
                  {quote.cached && <span>{t('status.cached')}</span>}
                  {quote.stale && (
                    <span className="label-warning">{t('status.stale')}</span>
                  )}
                </div>
              </td>
              <td>
                <button
                  type="button"
                  className="button-secondary"
                  disabled={
                    controlsLocked
                    || refreshingAll
                    || refreshingTicker === quote.ticker
                  }
                  onClick={() => void onRefresh(quote)}
                >
                  {refreshingAll || refreshingTicker === quote.ticker
                    ? t('common.refreshing')
                    : t('market.refreshTicker', { ticker: quote.ticker })}
                </button>
              </td>
            </tr>
          ))}
          {unavailableQuotes.map((quote) => (
            <tr key={`unavailable-${quote.ticker}`}>
              <td className="ticker-cell">{quote.ticker}</td>
              <td className="quote-unavailable-detail" colSpan={6}>
                {quote.message}
              </td>
              <td>
                <div className="quote-labels">
                  <span className="label-warning">
                    {t('common.unavailable')}
                  </span>
                </div>
              </td>
              <td>—</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function providerError(
  error: ApiProblemError,
  t: ReturnType<typeof useI18n>['t'],
) {
  const reason = error.problem.errors?.provider
  if (reason === 'provider timeout') {
    return t('market.timeout')
  }
  if (reason === 'provider rate limit') {
    return t('market.rateLimit')
  }
  if (reason === 'DEMO outage enabled') {
    return t('market.demoNoCache')
  }
  return error.problem.detail ?? t('market.currentlyUnavailable')
}

function marketError(
  reason: unknown,
  t: ReturnType<typeof useI18n>['t'],
) {
  if (reason instanceof ApiProblemError) {
    return reason.problem.status === 503
      ? providerError(reason, t)
      : reason.problem.detail ?? reason.message
  }
  return t('market.loadFailed')
}

function upsertQuote(
  quotes: MarketQuote[],
  quote: MarketQuote,
) {
  const existing = quotes.findIndex((item) => item.ticker === quote.ticker)
  if (existing < 0) return [...quotes, quote]
  return quotes.map((item, index) => index === existing ? quote : item)
}

function withoutKey(
  values: Record<string, string>,
  key: string,
) {
  if (!(key in values)) return values
  const updated = { ...values }
  delete updated[key]
  return updated
}

function loadSearchedTickers() {
  try {
    const stored = window.localStorage.getItem(SEARCHED_TICKERS_KEY)
    if (!stored) return []
    const parsed: unknown = JSON.parse(stored)
    if (!Array.isArray(parsed)) return []
    return Array.from(
      new Set(
        parsed.filter(
          (ticker): ticker is string =>
            typeof ticker === 'string' && TICKER_PATTERN.test(ticker),
        ),
      ),
    )
  } catch {
    return []
  }
}

function rememberSearchedTicker(ticker: string) {
  try {
    const tickers = loadSearchedTickers()
    if (!tickers.includes(ticker)) {
      window.localStorage.setItem(
        SEARCHED_TICKERS_KEY,
        JSON.stringify([...tickers, ticker]),
      )
    }
  } catch {
    // Search remains usable when browser storage is unavailable.
  }
}

function friendlyCategory(category: string) {
  return category.toLowerCase().replaceAll('_', ' ')
}

function isAbort(reason: unknown) {
  return reason instanceof DOMException && reason.name === 'AbortError'
}

export default MarketData
