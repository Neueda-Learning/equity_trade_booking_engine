import { useEffect, useState, type FormEvent } from 'react'
import './TradeBooking.css'

interface Trade {
  id: string
  ticker: string
  side: 'BUY'
  quantity: number
  tradePrice: number
  executedAt: string
  status: 'BOOKED'
  createdAt: string
}

interface TradePage {
  items: Trade[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

interface ProblemDetails {
  title?: string
  detail?: string
  errors?: Record<string, string>
}

const PAGE_SIZE = 10

function currentLocalDateTime() {
  const now = new Date()
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

function formatApiError(error: ProblemDetails) {
  return (
    error.detail ??
    error.title ??
    'The trade request could not be completed.'
  )
}

function TradeBooking() {
  const [ticker, setTicker] = useState('')
  const [quantity, setQuantity] = useState('')
  const [tradePrice, setTradePrice] = useState('')
  const [executedAt, setExecutedAt] = useState(currentLocalDateTime)
  const [page, setPage] = useState(0)
  const [refreshVersion, setRefreshVersion] = useState(0)
  const [tradePage, setTradePage] = useState<TradePage | null>(null)
  const [listError, setListError] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitMessage, setSubmitMessage] = useState('')
  const [submitError, setSubmitError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    const controller = new AbortController()

    async function loadTrades() {
      setIsLoading(true)
      setListError('')
      try {
        const response = await fetch(
          `/api/trades?page=${page}&size=${PAGE_SIZE}`,
          { signal: controller.signal },
        )
        if (!response.ok) {
          throw new Error('Trade history is unavailable.')
        }
        setTradePage((await response.json()) as TradePage)
      } catch (error) {
        if (!(error instanceof DOMException && error.name === 'AbortError')) {
          setListError(
            error instanceof Error
              ? error.message
              : 'Trade history is unavailable.',
          )
        }
      } finally {
        if (!controller.signal.aborted) {
          setIsLoading(false)
        }
      }
    }

    void loadTrades()
    return () => controller.abort()
  }, [page, refreshVersion])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsSubmitting(true)
    setSubmitError('')
    setSubmitMessage('')
    setFieldErrors({})

    try {
      const body = `{
        "ticker": ${JSON.stringify(ticker)},
        "side": "BUY",
        "quantity": ${quantity},
        "tradePrice": ${tradePrice},
        "executedAt": ${JSON.stringify(new Date(executedAt).toISOString())}
      }`
      const response = await fetch('/api/trades', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body,
      })
      const payload = (await response.json()) as Trade | ProblemDetails
      if (!response.ok) {
        const problem = payload as ProblemDetails
        const errors = problem.errors ?? {}
        setFieldErrors(errors)
        if (Object.keys(errors).length === 0) {
          setSubmitError(formatApiError(problem))
        }
        return
      }

      const booked = payload as Trade
      setSubmitMessage(`${booked.ticker} BUY trade booked successfully.`)
      setTicker('')
      setQuantity('')
      setTradePrice('')
      setExecutedAt(currentLocalDateTime())
      setPage(0)
      setRefreshVersion((version) => version + 1)
    } catch {
      setSubmitError('The trade request could not reach the backend.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className="trade-workspace" aria-labelledby="trade-heading">
      <div className="booking-panel">
        <p className="section-kicker">New booking</p>
        <h2 id="trade-heading">Book a BUY trade</h2>
        <p className="section-copy">
          Prices are recorded in USD. Tickers are normalized by the backend.
        </p>

        <form className="trade-form" onSubmit={handleSubmit}>
          <label>
            Ticker
            <input
              name="ticker"
              value={ticker}
              onChange={(event) => setTicker(event.target.value)}
              maxLength={10}
              pattern="[A-Za-z][A-Za-z0-9.-]{0,9}"
              placeholder="AAPL"
              required
              aria-invalid={Boolean(fieldErrors.ticker)}
              aria-describedby={
                fieldErrors.ticker ? 'ticker-error' : undefined
              }
            />
            {fieldErrors.ticker && (
              <span id="ticker-error" className="field-error" role="alert">
                {fieldErrors.ticker}
              </span>
            )}
          </label>

          <label>
            Side
            <input
              name="side"
              value="BUY"
              readOnly
              aria-invalid={Boolean(fieldErrors.side)}
              aria-describedby={fieldErrors.side ? 'side-error' : undefined}
            />
            {fieldErrors.side && (
              <span id="side-error" className="field-error" role="alert">
                {fieldErrors.side}
              </span>
            )}
          </label>

          <label>
            Quantity
            <input
              name="quantity"
              type="number"
              value={quantity}
              onChange={(event) => setQuantity(event.target.value)}
              min="0.000001"
              step="0.000001"
              placeholder="10.5"
              required
              aria-invalid={Boolean(fieldErrors.quantity)}
              aria-describedby={
                fieldErrors.quantity ? 'quantity-error' : undefined
              }
            />
            {fieldErrors.quantity && (
              <span id="quantity-error" className="field-error" role="alert">
                {fieldErrors.quantity}
              </span>
            )}
          </label>

          <label>
            Trade price (USD)
            <input
              name="tradePrice"
              type="number"
              value={tradePrice}
              onChange={(event) => setTradePrice(event.target.value)}
              min="0.000001"
              step="0.000001"
              placeholder="195.25"
              required
              aria-invalid={Boolean(fieldErrors.tradePrice)}
              aria-describedby={
                fieldErrors.tradePrice ? 'trade-price-error' : undefined
              }
            />
            {fieldErrors.tradePrice && (
              <span id="trade-price-error" className="field-error" role="alert">
                {fieldErrors.tradePrice}
              </span>
            )}
          </label>

          <label className="field-wide">
            Executed at
            <input
              name="executedAt"
              type="datetime-local"
              value={executedAt}
              onChange={(event) => setExecutedAt(event.target.value)}
              step="1"
              required
              aria-invalid={Boolean(fieldErrors.executedAt)}
              aria-describedby={
                fieldErrors.executedAt ? 'executed-at-error' : undefined
              }
            />
            {fieldErrors.executedAt && (
              <span id="executed-at-error" className="field-error" role="alert">
                {fieldErrors.executedAt}
              </span>
            )}
          </label>

          <button type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Booking…' : 'Book BUY trade'}
          </button>
        </form>

        {submitMessage && (
          <p className="form-message form-message--success" role="status">
            {submitMessage}
          </p>
        )}
        {submitError && (
          <p className="form-message form-message--error" role="alert">
            {submitError}
          </p>
        )}
      </div>

      <div className="ledger-panel">
        <div className="ledger-heading">
          <div>
            <p className="section-kicker">Booking ledger</p>
            <h2>Recent trades</h2>
          </div>
          <span className="trade-count">
            {tradePage?.totalElements ?? 0} booked
          </span>
        </div>

        {isLoading && <p className="table-state">Loading trades…</p>}
        {listError && (
          <p className="table-state table-state--error" role="alert">
            {listError}
          </p>
        )}
        {!isLoading && !listError && tradePage?.items.length === 0 && (
          <p className="table-state">No trades booked yet.</p>
        )}
        {!isLoading && !listError && tradePage && tradePage.items.length > 0 && (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Ticker</th>
                  <th>Side</th>
                  <th>Quantity</th>
                  <th>Price (USD)</th>
                  <th>Executed</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {tradePage.items.map((trade) => (
                  <tr key={trade.id}>
                    <td className="ticker-cell">{trade.ticker}</td>
                    <td>{trade.side}</td>
                    <td>{trade.quantity}</td>
                    <td>{trade.tradePrice}</td>
                    <td>{new Date(trade.executedAt).toLocaleString()}</td>
                    <td>
                      <span className="status-pill">{trade.status}</span>
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
              onClick={() => setPage((current) => current - 1)}
              disabled={page === 0}
            >
              Previous
            </button>
            <span>
              Page {page + 1} of {tradePage.totalPages}
            </span>
            <button
              type="button"
              onClick={() => setPage((current) => current + 1)}
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

export default TradeBooking
