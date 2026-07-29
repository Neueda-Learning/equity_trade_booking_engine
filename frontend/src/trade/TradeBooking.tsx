import {
  useEffect,
  useMemo,
  useState,
  type FormEvent,
  type KeyboardEvent,
  type ReactNode,
} from 'react'
import {
  ApiProblemError,
  amendTrade,
  createTrade,
  deleteTrade,
  getAccounts,
  getTrades,
  searchInstruments,
  type Account,
  type Instrument,
  type Trade,
  type TradeInput,
  type TradePage,
} from '../api'
import { formatDateTime, formatDecimal, formatMoney } from '../format'
import { localizeApiErrors, localizedStatus, useI18n } from '../i18n'
import './TradeBooking.css'

const PAGE_SIZE = 20

function currentLocalDateTime() {
  const now = new Date()
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

function asLocalDateTime(value: string) {
  const date = new Date(value)
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

function TradeBooking() {
  const { language, locale, t } = useI18n()
  const [accounts, setAccounts] = useState<Account[]>([])
  const [accountId, setAccountId] = useState('')
  const [side, setSide] = useState<'BUY' | 'SELL'>('BUY')
  const [filterAccountId, setFilterAccountId] = useState('')
  const [ticker, setTicker] = useState('')
  const [selectedInstrument, setSelectedInstrument] =
    useState<Instrument | null>(null)
  const [instrumentResults, setInstrumentResults] = useState<Instrument[]>([])
  const [instrumentLoading, setInstrumentLoading] = useState(false)
  const [instrumentError, setInstrumentError] = useState('')
  const [resultsOpen, setResultsOpen] = useState(false)
  const [highlightedResult, setHighlightedResult] = useState(0)
  const [quantity, setQuantity] = useState('')
  const [tradePrice, setTradePrice] = useState('')
  const [executedAt, setExecutedAt] = useState(currentLocalDateTime)
  const [editingTrade, setEditingTrade] = useState<Trade | null>(null)
  const [page, setPage] = useState(0)
  const [refreshVersion, setRefreshVersion] = useState(0)
  const [tradePage, setTradePage] = useState<TradePage | null>(null)
  const [loading, setLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [serverError, setServerError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [activityError, setActivityError] = useState('')
  const [deletingId, setDeletingId] = useState<string | null>(null)

  const activeAccounts = useMemo(
    () => accounts.filter((account) => account.status === 'ACTIVE'),
    [accounts],
  )
  const accountNames = useMemo(
    () => new Map(accounts.map((account) => [account.id, account.name])),
    [accounts],
  )

  useEffect(() => {
    const controller = new AbortController()
    getAccounts(controller.signal)
      .then((loaded) => {
        setAccounts(loaded)
        const firstActive = loaded.find((account) => account.status === 'ACTIVE')
        setAccountId((current) => current || firstActive?.id || '')
      })
      .catch((error: unknown) => {
        if (!isAbort(error)) setServerError(t('trade.accountsUnavailable'))
      })
    return () => controller.abort()
  }, [refreshVersion, t])

  useEffect(() => {
    const controller = new AbortController()
    getTrades(
      page,
      PAGE_SIZE,
      filterAccountId || undefined,
      controller.signal,
    )
      .then(setTradePage)
      .catch((error: unknown) => {
        if (!isAbort(error)) setListError(t('trade.historyUnavailable'))
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [page, filterAccountId, refreshVersion, t])

  useEffect(() => {
    if (
      ticker.trim() === '' ||
      selectedInstrument?.ticker === ticker.trim().toUpperCase()
    ) {
      return
    }
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      setInstrumentLoading(true)
      setInstrumentError('')
      searchInstruments(ticker, controller.signal)
        .then((response) => {
          setInstrumentResults(response.items)
          setHighlightedResult(0)
          setResultsOpen(true)
        })
        .catch((error: unknown) => {
          if (!isAbort(error)) {
            setInstrumentResults([])
            setResultsOpen(true)
            setInstrumentError(
              error instanceof ApiProblemError
                ? error.problem.detail ?? error.message
                : t('trade.searchUnavailable'),
            )
          }
        })
        .finally(() => {
          if (!controller.signal.aborted) setInstrumentLoading(false)
        })
    }, 300)
    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [ticker, selectedInstrument, t])

  async function submit(event: FormEvent) {
    event.preventDefault()
    setMessage('')
    setServerError('')
    setFieldErrors({})
    if (
      !selectedInstrument ||
      selectedInstrument.ticker !== ticker.trim().toUpperCase()
    ) {
      setFieldErrors({
        ticker: t('trade.selectVerified'),
      })
      return
    }
    setSaving(true)
    try {
      const input: TradeInput = {
        accountId,
        ticker: selectedInstrument.ticker,
        side,
        quantity: Number(quantity),
        tradePrice: Number(tradePrice),
        executedAt: new Date(executedAt).toISOString(),
      }
      if (editingTrade) {
        const result = await amendTrade(editingTrade.id, input)
        setMessage(
          t('trade.amended', {
            ticker: result.cancelledTrade.ticker,
            replacement: result.replacementTrade.ticker,
          }),
        )
      } else {
        const trade = await createTrade(input)
        setMessage(
          t('trade.booked', {
            ticker: trade.ticker,
            side: localizedStatus(trade.side, t),
          }),
        )
      }
      resetForm()
      refreshLedger()
    } catch (error) {
      if (error instanceof ApiProblemError) {
        setFieldErrors(
          localizeApiErrors(error.problem.errors ?? {}, language),
        )
        if (!error.problem.errors) setServerError(error.message)
      } else {
        setServerError(t('trade.requestFailed'))
      }
    } finally {
      setSaving(false)
    }
  }

  async function remove(trade: Trade) {
    const confirmed = window.confirm(
      t('trade.deleteConfirm', {
        ticker: trade.ticker,
        side: localizedStatus(trade.side, t),
      }),
    )
    if (!confirmed) return
    setDeletingId(trade.id)
    setActivityError('')
    setMessage('')
    try {
      await deleteTrade(trade.id)
      setMessage(t('trade.deleted'))
      refreshLedger()
    } catch (error) {
      setActivityError(problemMessage(
        error,
        t('trade.deleteFailed'),
      ))
    } finally {
      setDeletingId(null)
    }
  }

  function edit(trade: Trade) {
    setEditingTrade(trade)
    setAccountId(trade.accountId)
    setSide(trade.side)
    setTicker(trade.ticker)
    setSelectedInstrument(null)
    setInstrumentResults([])
    setInstrumentLoading(false)
    setResultsOpen(false)
    setInstrumentError('')
    setQuantity(String(trade.quantity))
    setTradePrice(String(trade.tradePrice))
    setExecutedAt(asLocalDateTime(trade.executedAt))
    setMessage('')
    setServerError('')
    setFieldErrors({})
  }

  function selectInstrument(instrument: Instrument) {
    setSelectedInstrument(instrument)
    setTicker(instrument.ticker)
    setInstrumentResults([])
    setInstrumentError('')
    setResultsOpen(false)
    setFieldErrors((current) => {
      const next = { ...current }
      delete next.ticker
      return next
    })
  }

  function onTickerChange(value: string) {
    setTicker(value)
    setSelectedInstrument(null)
    setInstrumentResults([])
    setInstrumentLoading(false)
    setResultsOpen(false)
    setInstrumentError('')
  }

  function onTickerKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (!resultsOpen || instrumentResults.length === 0) return
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setHighlightedResult((current) =>
        Math.min(current + 1, instrumentResults.length - 1),
      )
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setHighlightedResult((current) => Math.max(current - 1, 0))
    } else if (event.key === 'Enter') {
      event.preventDefault()
      selectInstrument(instrumentResults[highlightedResult])
    } else if (event.key === 'Escape') {
      setResultsOpen(false)
    }
  }

  function resetForm() {
    const firstActive = activeAccounts[0]
    setEditingTrade(null)
    setAccountId(firstActive?.id ?? '')
    setSide('BUY')
    setTicker('')
    setSelectedInstrument(null)
    setInstrumentResults([])
    setQuantity('')
    setTradePrice('')
    setExecutedAt(currentLocalDateTime())
  }

  function refreshLedger() {
    setPage(0)
    setLoading(true)
    setRefreshVersion((version) => version + 1)
  }

  return (
    <section className="trade-workspace" aria-labelledby="activity-heading">
      <div className="booking-panel">
        <p className="section-kicker">
          {editingTrade ? t('trade.amendActivity') : t('trade.newActivity')}
        </p>
        <h2 id="activity-heading">
          {editingTrade
            ? t('trade.editTicker', { ticker: editingTrade.ticker })
            : t('trade.bookTitle')}
        </h2>
        {editingTrade && (
          <p className="audit-note">
            {t('trade.auditNote')}
          </p>
        )}
        {activeAccounts.length === 0 ? (
          <p className="table-state">
            {t('trade.noActiveAccounts')}
          </p>
        ) : (
          <form className="trade-form" onSubmit={submit}>
            <TradeSelect
              label={t('common.account')}
              name="accountId"
              value={accountId}
              error={fieldErrors.accountId}
              onChange={setAccountId}
            >
              {activeAccounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.name}
                </option>
              ))}
            </TradeSelect>
            <TradeSelect
              label={t('trade.side')}
              name="side"
              value={side}
              error={fieldErrors.side}
              onChange={(value) => setSide(value as 'BUY' | 'SELL')}
            >
              <option value="BUY">{t('status.buy')}</option>
              <option value="SELL">{t('status.sell')}</option>
            </TradeSelect>
            <TickerCombobox
              value={ticker}
              selected={selectedInstrument}
              results={instrumentResults}
              loading={instrumentLoading}
              searchError={instrumentError}
              fieldError={fieldErrors.ticker}
              open={resultsOpen}
              highlighted={highlightedResult}
              onChange={onTickerChange}
              onKeyDown={onTickerKeyDown}
              onSelect={selectInstrument}
            />
            <TradeInput
              label={t('common.quantity')}
              name="quantity"
              type="number"
              value={quantity}
              error={fieldErrors.quantity}
              onChange={setQuantity}
              min="0.000001"
              step="0.000001"
              required
            />
            <TradeInput
              label={t('trade.priceUsd')}
              name="tradePrice"
              type="number"
              value={tradePrice}
              error={fieldErrors.tradePrice}
              onChange={setTradePrice}
              min="0.000001"
              step="0.000001"
              required
            />
            <TradeInput
              label={t('trade.executedAt')}
              name="executedAt"
              type="datetime-local"
              value={executedAt}
              error={fieldErrors.executedAt}
              onChange={setExecutedAt}
              step="1"
              required
              className="field-wide"
            />
            <div className="trade-form-actions field-wide">
              <button type="submit" disabled={saving || instrumentLoading}>
                {saving
                  ? t('common.saving')
                  : editingTrade
                    ? t('trade.saveAmendment')
                    : t('trade.bookSide', {
                        side: localizedStatus(side, t),
                      })}
              </button>
              {editingTrade && (
                <button
                  type="button"
                  className="button-secondary"
                  onClick={resetForm}
                  disabled={saving}
                >
                  {t('trade.cancelEdit')}
                </button>
              )}
            </div>
          </form>
        )}
        {message && (
          <p className="form-message form-message--success">{message}</p>
        )}
        {serverError && (
          <p className="form-message form-message--error" role="alert">
            {serverError}
          </p>
        )}
      </div>

      <div className="ledger-panel">
        <div className="ledger-heading">
          <div>
            <p className="section-kicker">{t('trade.ledger')}</p>
            <h2>{t('nav.activity')}</h2>
          </div>
          <label>
            {t('trade.accountFilter')}
            <select
              className="activity-select"
              value={filterAccountId}
              onChange={(event) => {
                setFilterAccountId(event.target.value)
                setPage(0)
                setLoading(true)
                setListError('')
              }}
            >
              <option value="">{t('trade.allAccounts')}</option>
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.name}
                </option>
              ))}
            </select>
          </label>
        </div>
        {loading && <p className="table-state">{t('trade.loading')}</p>}
        {listError && (
          <p className="table-state table-state--error">{listError}</p>
        )}
        {activityError && (
          <p className="table-state table-state--error" role="alert">
            {activityError}
          </p>
        )}
        {!loading && !listError && tradePage?.items.length === 0 && (
          <p className="table-state">{t('trade.empty')}</p>
        )}
        {!loading && !listError && tradePage && tradePage.items.length > 0 && (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>{t('common.account')}</th>
                  <th>{t('common.ticker')}</th>
                  <th>{t('trade.side')}</th>
                  <th>{t('common.quantity')}</th>
                  <th>{t('trade.price')}</th>
                  <th>{t('trade.executed')}</th>
                  <th>{t('common.status')}</th>
                  <th>{t('trade.audit')}</th>
                  <th>{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {tradePage.items.map((trade) => (
                  <tr key={trade.id}>
                    <td>
                      {accountNames.get(trade.accountId)
                        ?? t('common.unknownAccount')}
                    </td>
                    <td className="ticker-cell">{trade.ticker}</td>
                    <td>
                      <span className={`side-pill side-pill--${trade.side.toLowerCase()}`}>
                        {localizedStatus(trade.side, t)}
                      </span>
                    </td>
                    <td>{formatDecimal(trade.quantity, locale)}</td>
                    <td>{formatMoney(trade.tradePrice, locale)}</td>
                    <td>{formatDateTime(trade.executedAt, locale)}</td>
                    <td>
                      <span className="status-pill">
                        {localizedStatus(trade.status, t)}
                      </span>
                    </td>
                    <td>
                      {trade.cancelledAt ? (
                        <span className="audit-detail">
                          {localizedStatus(
                            trade.cancellationReason ?? 'CANCELLED',
                            t,
                          )}
                          <small>
                            {formatDateTime(trade.cancelledAt, locale)}
                          </small>
                        </span>
                      ) : trade.supersedesTradeId ? (
                        t('trade.replacement')
                      ) : (
                        '—'
                      )}
                    </td>
                    <td>
                      {trade.status === 'BOOKED' ? (
                        <div className="row-actions">
                          <button
                            type="button"
                            className="button-secondary"
                            onClick={() => edit(trade)}
                            disabled={deletingId === trade.id}
                          >
                            {t('common.edit')}
                          </button>
                          <button
                            type="button"
                            className="button-danger"
                            disabled={deletingId === trade.id}
                            onClick={() => void remove(trade)}
                          >
                            {deletingId === trade.id
                              ? t('trade.deleting')
                              : t('common.delete')}
                          </button>
                        </div>
                      ) : (
                        '—'
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {tradePage && tradePage.totalPages > 1 && (
          <nav className="pagination" aria-label={t('trade.pages')}>
            <button
              type="button"
              onClick={() => {
                setLoading(true)
                setPage((current) => current - 1)
              }}
              disabled={page === 0}
            >
              {t('common.previous')}
            </button>
            <span>
              {t('common.pageOf', {
                page: page + 1,
                total: tradePage.totalPages,
              })}
            </span>
            <button
              type="button"
              onClick={() => {
                setLoading(true)
                setPage((current) => current + 1)
              }}
              disabled={page + 1 >= tradePage.totalPages}
            >
              {t('common.next')}
            </button>
          </nav>
        )}
      </div>
    </section>
  )
}

function TickerCombobox({
  value,
  selected,
  results,
  loading,
  searchError,
  fieldError,
  open,
  highlighted,
  onChange,
  onKeyDown,
  onSelect,
}: {
  value: string
  selected: Instrument | null
  results: Instrument[]
  loading: boolean
  searchError: string
  fieldError?: string
  open: boolean
  highlighted: number
  onChange: (value: string) => void
  onKeyDown: (event: KeyboardEvent<HTMLInputElement>) => void
  onSelect: (instrument: Instrument) => void
}) {
  const { t } = useI18n()
  const describedBy = fieldError
    ? 'ticker-error'
    : selected
      ? 'ticker-selection'
      : undefined
  return (
    <label className="ticker-combobox">
      {t('trade.tickerCompany')}
      <input
        name="ticker"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onKeyDown={onKeyDown}
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={open}
        aria-controls="ticker-results"
        aria-activedescendant={
          open && results[highlighted]
            ? `ticker-result-${results[highlighted].ticker}`
            : undefined
        }
        aria-invalid={Boolean(fieldError)}
        aria-describedby={describedBy}
        autoComplete="off"
        maxLength={64}
        required
      />
      {loading && (
        <span className="field-hint">{t('trade.searchingSecurities')}</span>
      )}
      {selected && (
        <span id="ticker-selection" className="instrument-selected">
          {t('trade.verified', {
            ticker: selected.ticker,
            name: selected.name,
          })}
        </span>
      )}
      {fieldError && (
        <span id="ticker-error" className="field-error" role="alert">
          {fieldError}
        </span>
      )}
      {open && (
        <div id="ticker-results" className="ticker-results" role="listbox">
          {searchError ? (
            <p role="alert">{searchError}</p>
          ) : results.length === 0 && !loading ? (
            <p>{t('trade.noSecurities')}</p>
          ) : (
            results.map((instrument, index) => (
              <button
                id={`ticker-result-${instrument.ticker}`}
                key={instrument.ticker}
                type="button"
                role="option"
                aria-selected={index === highlighted}
                className={index === highlighted ? 'is-highlighted' : ''}
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => onSelect(instrument)}
              >
                <strong>{instrument.ticker}</strong>
                <span>{instrument.name}</span>
                <small>{instrument.type}</small>
              </button>
            ))
          )}
        </div>
      )}
    </label>
  )
}

function TradeInput({
  label,
  name,
  value,
  error,
  onChange,
  className,
  ...props
}: {
  label: string
  name: string
  value: string
  error?: string
  onChange: (value: string) => void
  className?: string
  type?: string
  min?: string
  step?: string
  required?: boolean
}) {
  return (
    <label className={className}>
      {label}
      <input
        {...props}
        name={name}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${name}-error` : undefined}
      />
      {error && (
        <span id={`${name}-error`} className="field-error">
          {error}
        </span>
      )}
    </label>
  )
}

function TradeSelect({
  label,
  name,
  value,
  error,
  onChange,
  children,
}: {
  label: string
  name: string
  value: string
  error?: string
  onChange: (value: string) => void
  children: ReactNode
}) {
  return (
    <label>
      {label}
      <select
        name={name}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={Boolean(error)}
      >
        {children}
      </select>
      {error && <span className="field-error">{error}</span>}
    </label>
  )
}

function problemMessage(error: unknown, fallback: string) {
  if (error instanceof ApiProblemError) {
    return error.problem.errors?.quantity
      ?? error.problem.errors?.id
      ?? error.problem.detail
      ?? error.message
  }
  return fallback
}

function isAbort(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError'
}

export default TradeBooking
