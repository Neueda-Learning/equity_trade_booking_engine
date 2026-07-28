import {
  useEffect,
  useMemo,
  useState,
  type FormEvent,
  type ReactNode,
} from 'react'
import {
  ApiProblemError,
  cancelTrade,
  createTrade,
  getAccounts,
  getTrades,
  type Account,
  type TradePage,
} from '../api'
import './TradeBooking.css'

const PAGE_SIZE = 20

function currentLocalDateTime() {
  const now = new Date()
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

function TradeBooking() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [accountId, setAccountId] = useState('')
  const [side, setSide] = useState<'BUY' | 'SELL'>('BUY')
  const [filterAccountId, setFilterAccountId] = useState('')
  const [ticker, setTicker] = useState('')
  const [quantity, setQuantity] = useState('')
  const [tradePrice, setTradePrice] = useState('')
  const [executedAt, setExecutedAt] = useState(currentLocalDateTime)
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
  const [cancellingId, setCancellingId] = useState<string | null>(null)

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
        if (!(error instanceof DOMException && error.name === 'AbortError')) {
          setServerError('Accounts are unavailable.')
        }
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
        if (!(error instanceof DOMException && error.name === 'AbortError')) {
          setListError('Trade history is unavailable.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [page, filterAccountId, refreshVersion])

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setMessage('')
    setServerError('')
    setFieldErrors({})
    try {
      const trade = await createTrade({
        accountId,
        ticker,
        side,
        quantity: Number(quantity),
        tradePrice: Number(tradePrice),
        executedAt: new Date(executedAt).toISOString(),
      })
      setMessage(`${trade.ticker} ${trade.side} trade booked successfully.`)
      setTicker('')
      setQuantity('')
      setTradePrice('')
      setExecutedAt(currentLocalDateTime())
      setPage(0)
      setLoading(true)
      setRefreshVersion((version) => version + 1)
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

  async function cancel(id: string) {
    if (!window.confirm('Cancel this trade?')) return
    setCancellingId(id)
    setActivityError('')
    try {
      await cancelTrade(id)
      setMessage('Trade cancelled successfully.')
      setLoading(true)
      setRefreshVersion((version) => version + 1)
    } catch (error) {
      if (error instanceof ApiProblemError) {
        setActivityError(
          error.problem.errors?.quantity ??
            error.problem.detail ??
            error.message,
        )
      } else {
        setActivityError('The cancellation request could not reach the backend.')
      }
    } finally {
      setCancellingId(null)
    }
  }

  return (
    <section className="trade-workspace" aria-labelledby="activity-heading">
      <div className="booking-panel">
        <p className="section-kicker">New activity</p>
        <h2 id="activity-heading">Book a trade</h2>
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
            <TradeInput
              label="Ticker"
              name="ticker"
              value={ticker}
              error={fieldErrors.ticker}
              onChange={setTicker}
              maxLength={10}
              required
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
            <button type="submit" disabled={saving}>
              {saving ? 'Booking…' : `Book ${side} trade`}
            </button>
          </form>
        )}
        {message && <p className="form-message form-message--success">{message}</p>}
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
        {listError && <p className="table-state table-state--error">{listError}</p>}
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
                  <th>Cancelled</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {tradePage.items.map((trade) => (
                  <tr key={trade.id}>
                    <td>{accountNames.get(trade.accountId) ?? 'Unknown account'}</td>
                    <td className="ticker-cell">{trade.ticker}</td>
                    <td>{trade.side}</td>
                    <td>{trade.quantity}</td>
                    <td>{trade.tradePrice}</td>
                    <td>{new Date(trade.executedAt).toLocaleString()}</td>
                    <td><span className="status-pill">{trade.status}</span></td>
                    <td>
                      {trade.cancelledAt
                        ? new Date(trade.cancelledAt).toLocaleString()
                        : '—'}
                    </td>
                    <td>
                      {trade.status === 'BOOKED' ? (
                        <button
                          type="button"
                          className="button-secondary"
                          disabled={cancellingId === trade.id}
                          onClick={() => void cancel(trade.id)}
                        >
                          {cancellingId === trade.id
                            ? 'Cancelling…'
                            : 'Cancel'}
                        </button>
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
  maxLength?: number
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
      {error && <span id={`${name}-error`} className="field-error">{error}</span>}
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

export default TradeBooking
