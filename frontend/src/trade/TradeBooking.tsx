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
        if (!isAbort(error)) setServerError('Accounts are unavailable.')
      })
    return () => controller.abort()
  }, [refreshVersion])

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
        if (!isAbort(error)) setListError('Trade history is unavailable.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [page, filterAccountId, refreshVersion])

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
                : 'Security search is unavailable.',
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
  }, [ticker, selectedInstrument])

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
        ticker: 'Select a verified security from the search results.',
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
          `${result.cancelledTrade.ticker} was amended; `
            + `${result.replacementTrade.ticker} replacement booked.`,
        )
      } else {
        const trade = await createTrade(input)
        setMessage(`${trade.ticker} ${trade.side} trade booked successfully.`)
      }
      resetForm()
      refreshLedger()
    } catch (error) {
      if (error instanceof ApiProblemError) {
        setFieldErrors(error.problem.errors ?? {})
        if (!error.problem.errors) setServerError(error.message)
      } else {
        setServerError('The trade request could not reach the backend.')
      }
    } finally {
      setSaving(false)
    }
  }

  async function remove(trade: Trade) {
    const confirmed = window.confirm(
      `Delete ${trade.ticker} ${trade.side} activity? `
        + 'It will be cancelled and retained for audit; '
        + 'the database record will not be physically removed.',
    )
    if (!confirmed) return
    setDeletingId(trade.id)
    setActivityError('')
    setMessage('')
    try {
      await deleteTrade(trade.id)
      setMessage('Activity deleted with its audit record preserved.')
      refreshLedger()
    } catch (error) {
      setActivityError(problemMessage(
        error,
        'The delete request could not reach the backend.',
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
          {editingTrade ? 'Amend activity' : 'New activity'}
        </p>
        <h2 id="activity-heading">
          {editingTrade ? `Edit ${editingTrade.ticker}` : 'Book a trade'}
        </h2>
        {editingTrade && (
          <p className="audit-note">
            Saving creates a replacement trade and preserves the original as
            CANCELLED for audit.
          </p>
        )}
        {activeAccounts.length === 0 ? (
          <p className="table-state">
            No active accounts. Create an account before booking a trade.
          </p>
        ) : (
          <form className="trade-form" onSubmit={submit}>
            <TradeSelect
              label="Account"
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
              label="Side"
              name="side"
              value={side}
              error={fieldErrors.side}
              onChange={(value) => setSide(value as 'BUY' | 'SELL')}
            >
              <option value="BUY">BUY</option>
              <option value="SELL">SELL</option>
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
              label="Quantity"
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
              label="Trade price (USD)"
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
              label="Executed at"
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
                  ? 'Saving…'
                  : editingTrade
                    ? 'Save amendment'
                    : `Book ${side} trade`}
              </button>
              {editingTrade && (
                <button
                  type="button"
                  className="button-secondary"
                  onClick={resetForm}
                  disabled={saving}
                >
                  Cancel edit
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
            <p className="section-kicker">Trade ledger</p>
            <h2>Activity</h2>
          </div>
          <label>
            Account filter
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
              <option value="">All accounts</option>
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.name}
                </option>
              ))}
            </select>
          </label>
        </div>
        {loading && <p className="table-state">Loading trades…</p>}
        {listError && (
          <p className="table-state table-state--error">{listError}</p>
        )}
        {activityError && (
          <p className="table-state table-state--error" role="alert">
            {activityError}
          </p>
        )}
        {!loading && !listError && tradePage?.items.length === 0 && (
          <p className="table-state">No trades booked yet.</p>
        )}
        {!loading && !listError && tradePage && tradePage.items.length > 0 && (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Account</th>
                  <th>Ticker</th>
                  <th>Side</th>
                  <th>Quantity</th>
                  <th>Price (USD)</th>
                  <th>Executed</th>
                  <th>Status</th>
                  <th>Audit</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {tradePage.items.map((trade) => (
                  <tr key={trade.id}>
                    <td>
                      {accountNames.get(trade.accountId) ?? 'Unknown account'}
                    </td>
                    <td className="ticker-cell">{trade.ticker}</td>
                    <td>
                      <span className={`side-pill side-pill--${trade.side.toLowerCase()}`}>
                        {trade.side}
                      </span>
                    </td>
                    <td>{formatDecimal(trade.quantity)}</td>
                    <td>{formatMoney(trade.tradePrice)}</td>
                    <td>{formatDateTime(trade.executedAt)}</td>
                    <td>
                      <span className="status-pill">{trade.status}</span>
                    </td>
                    <td>
                      {trade.cancelledAt ? (
                        <span className="audit-detail">
                          {trade.cancellationReason ?? 'CANCELLED'}
                          <small>{formatDateTime(trade.cancelledAt)}</small>
                        </span>
                      ) : trade.supersedesTradeId ? (
                        'Replacement'
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
                            Edit
                          </button>
                          <button
                            type="button"
                            className="button-danger"
                            disabled={deletingId === trade.id}
                            onClick={() => void remove(trade)}
                          >
                            {deletingId === trade.id ? 'Deleting…' : 'Delete'}
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
          <nav className="pagination" aria-label="Trade pages">
            <button
              type="button"
              onClick={() => {
                setLoading(true)
                setPage((current) => current - 1)
              }}
              disabled={page === 0}
            >
              Previous
            </button>
            <span>Page {page + 1} of {tradePage.totalPages}</span>
            <button
              type="button"
              onClick={() => {
                setLoading(true)
                setPage((current) => current + 1)
              }}
              disabled={page + 1 >= tradePage.totalPages}
            >
              Next
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
  const describedBy = fieldError
    ? 'ticker-error'
    : selected
      ? 'ticker-selection'
      : undefined
  return (
    <label className="ticker-combobox">
      Ticker or company
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
      {loading && <span className="field-hint">Searching securities…</span>}
      {selected && (
        <span id="ticker-selection" className="instrument-selected">
          Verified: {selected.ticker} · {selected.name}
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
            <p>No supported US securities found.</p>
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
