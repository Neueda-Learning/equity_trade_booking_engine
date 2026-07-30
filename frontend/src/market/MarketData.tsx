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
  const [searchResult, setSearchResult] = useState<MarketQuote | null>(null)
  const [refreshingTicker, setRefreshingTicker] = useState<string | null>(null)
  const [refreshingAll, setRefreshingAll] = useState(false)
  const [refreshAllError, setRefreshAllError] = useState('')
  const [demoChanging, setDemoChanging] = useState(false)
  const [message, setMessage] = useState('')

  const handleError = useCallback((reason: unknown) => {
    if (isAbort(reason)) return
    if (reason instanceof ApiProblemError && reason.problem.status === 503) {
      setUnavailable(true)
      setError(providerError(reason, t))
      return
    }
    setError(
      reason instanceof ApiProblemError
        ? reason.problem.detail ?? reason.message
        : t('market.loadFailed'),
    )
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
      if (searchResult?.ticker === refreshed.ticker) {
        setSearchResult(refreshed)
      }
      setMessage(
        t('market.refreshed', {
          ticker: refreshed.ticker,
          source: refreshed.source,
        }),
      )
      await reloadProviderStatus()
    } catch (reason) {
      handleError(reason)
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
      setSearchResult((current) =>
        current ? refreshedByTicker.get(current.ticker) ?? current : current,
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
      setMessage(
        t(status.enabled ? 'market.outageEnabled' : 'market.outageDisabled'),
      )
    } catch (reason) {
      handleError(reason)
    } finally {
      setDemoChanging(false)
    }
  }

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
              setSearchResult(null)
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
      {searchResult && (
        <div className="panel market-search-result">
          <h3>{t('market.searchResult')}</h3>
          <QuoteTable
            quotes={[searchResult]}
            refreshingTicker={refreshingTicker}
            refreshingAll={refreshingAll}
            onRefresh={refresh}
          />
        </div>
      )}
      {!loading && !error && !unavailable && quotes.length === 0 && (
        <p className="table-state">{t('market.noPositions')}</p>
      )}
      {!loading && !error && !unavailable && quotes.length > 0 && (
        <div className="panel">
          <div className="market-quotes-heading">
            <h3>{t('market.positionQuotes')}</h3>
            <button
              type="button"
              className="market-refresh-all"
              disabled={refreshingAll || refreshingTicker !== null}
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
  refreshingTicker,
  refreshingAll,
  onRefresh,
}: {
  quotes: MarketQuote[]
  refreshingTicker: string | null
  refreshingAll: boolean
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
                  disabled={refreshingAll || refreshingTicker === quote.ticker}
                  onClick={() => void onRefresh(quote)}
                >
                  {refreshingAll || refreshingTicker === quote.ticker
                    ? t('common.refreshing')
                    : t('market.refreshTicker', { ticker: quote.ticker })}
                </button>
              </td>
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

function friendlyCategory(category: string) {
  return category.toLowerCase().replaceAll('_', ' ')
}

function isAbort(reason: unknown) {
  return reason instanceof DOMException && reason.name === 'AbortError'
}

export default MarketData
