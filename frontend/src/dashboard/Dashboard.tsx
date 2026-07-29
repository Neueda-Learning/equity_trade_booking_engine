import { useEffect, useState } from 'react'
import {
  getAccounts,
  getDashboard,
  getDashboardHistory,
  refreshDashboard,
  type Account,
  type DashboardResponse,
  type HistoryRange,
  type PositionPnl,
  type ValuationHistory,
  type ValuationSnapshot,
} from '../api'
import {
  formatDateTime,
  formatDecimal,
  formatMoney,
  formatNullableMoney,
  formatSignedMoney,
  formatSignedPercent,
} from '../format'
import './Dashboard.css'

const ranges: HistoryRange[] = ['1D', '7D', '30D', 'ALL']

function Dashboard({
  onViewActivity,
}: {
  onViewActivity?: () => void
}) {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [accountId, setAccountId] = useState('')
  const [range, setRange] = useState<HistoryRange>('30D')
  const [dashboard, setDashboard] = useState<DashboardResponse | null>(null)
  const [history, setHistory] = useState<ValuationHistory | null>(null)
  const [loading, setLoading] = useState(true)
  const [historyLoading, setHistoryLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState('')
  const [historyError, setHistoryError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    getAccounts(controller.signal)
      .then(setAccounts)
      .catch((reason: unknown) => {
        if (!isAbort(reason)) setError('Dashboard data is unavailable.')
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    getDashboard(accountId || undefined, controller.signal)
      .then(setDashboard)
      .catch((reason: unknown) => {
        if (!isAbort(reason)) setError('Dashboard data is unavailable.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [accountId])

  useEffect(() => {
    const controller = new AbortController()
    getDashboardHistory(range, accountId || undefined, controller.signal)
      .then(setHistory)
      .catch((reason: unknown) => {
        if (!isAbort(reason)) {
          setHistoryError('Valuation history is unavailable.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setHistoryLoading(false)
      })
    return () => controller.abort()
  }, [accountId, range])

  const refresh = async () => {
    setRefreshing(true)
    setError('')
    try {
      const updated = await refreshDashboard(accountId || undefined)
      setDashboard(updated)
      setHistory(
        await getDashboardHistory(range, accountId || undefined),
      )
    } catch {
      setError('Dashboard refresh failed.')
    } finally {
      setRefreshing(false)
    }
  }

  const selectAccount = (nextAccountId: string) => {
    setLoading(true)
    setHistoryLoading(true)
    setError('')
    setHistoryError('')
    setAccountId(nextAccountId)
  }

  const selectRange = (nextRange: HistoryRange) => {
    setHistoryLoading(true)
    setHistoryError('')
    setRange(nextRange)
  }

  return (
    <section aria-labelledby="dashboard-heading">
      <div className="dashboard-heading">
        <div>
          <p className="section-kicker">Portfolio valuation</p>
          <h2 id="dashboard-heading">Dashboard</h2>
        </div>
        <div className="dashboard-controls">
          <label>
            Account
            <select
              value={accountId}
              onChange={(event) => selectAccount(event.target.value)}
            >
              <option value="">All Accounts</option>
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.name}
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            onClick={refresh}
            disabled={refreshing || loading}
          >
            {refreshing ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>
      </div>

      {loading && <p className="table-state">Loading dashboard…</p>}
      {error && <p className="table-state table-state--error">{error}</p>}
      {!loading && dashboard && (
        <>
          <DashboardStatus dashboard={dashboard} />
          {dashboard.totals.stale && (
            <p className="notice dashboard-stale-banner" role="status">
              Cached, stale quotes are being used. Values are not live.
            </p>
          )}
          <Kpis dashboard={dashboard} />
          <PositionTable items={dashboard.positions} />
          <HistoryPanel
            history={history}
            loading={historyLoading}
            error={historyError}
            range={range}
            onRange={selectRange}
          />
          <RecentActivity
            dashboard={dashboard}
            onViewActivity={onViewActivity}
          />
        </>
      )}
    </section>
  )
}

function DashboardStatus({ dashboard }: { dashboard: DashboardResponse }) {
  const sources = Array.from(
    new Set(
      dashboard.positions
        .filter((position) => position.available && position.source)
        .map((position) => position.source),
    ),
  )
  const source = sources.length > 0 ? sources.join(' + ') : 'NO QUOTES'
  return (
    <div className="dashboard-status">
      <span>
        Last updated{' '}
        <time dateTime={dashboard.capturedAt}>
          {formatDateTime(dashboard.capturedAt)}
        </time>
      </span>
      <div className="quote-flags" aria-label="Quote status">
        <span className="flag">SOURCE: {source}</span>
        {dashboard.totals.mock && <span className="flag">MOCK</span>}
        {dashboard.quoteStatus.cached > 0 && (
          <span className="flag">CACHED</span>
        )}
        {dashboard.totals.stale && (
          <span className="flag flag--warning">STALE</span>
        )}
        {!dashboard.totals.stale && <span className="flag">FRESH</span>}
        {!dashboard.totals.complete && (
          <span className="flag flag--warning">INCOMPLETE</span>
        )}
        {dashboard.totals.complete && <span className="flag">COMPLETE</span>}
      </div>
    </div>
  )
}

function Kpis({ dashboard }: { dashboard: DashboardResponse }) {
  const totals = dashboard.totals
  const cards = [
    ['Total Market Value', formatMoney(totals.totalMarketValue)],
    ['Total Cost Basis', formatMoney(totals.totalCostBasis)],
    ['Unrealized P&L', formatSignedMoney(totals.totalUnrealizedPnl)],
    ['Return %', formatSignedPercent(totals.totalPnlPercent)],
    ['Open Positions', String(totals.positionCount)],
    ['Unpriced Positions', String(totals.unpricedPositionCount)],
  ]
  return (
    <div className="metrics dashboard-metrics">
      {cards.map(([label, value]) => (
        <article key={label}>
          <span>{label}</span>
          <strong className={pnlClass(label, value)}>{value}</strong>
        </article>
      ))}
    </div>
  )
}

function PositionTable({ items }: { items: PositionPnl[] }) {
  return (
    <div className="panel dashboard-panel">
      <h3>Position P&amp;L</h3>
      {items.length === 0 ? (
        <p className="table-state">No open positions.</p>
      ) : (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Ticker</th>
                <th>Quantity</th>
                <th>Average cost</th>
                <th>Market price</th>
                <th>Market value</th>
                <th>Unrealized P&amp;L</th>
                <th>Return</th>
                <th>Quote status</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.ticker}>
                  <td><strong>{item.ticker}</strong></td>
                  <td>{formatDecimal(item.quantity)}</td>
                  <td>{formatMoney(item.averageCost)}</td>
                  <td>{formatNullableMoney(item.marketPrice)}</td>
                  <td>{formatNullableMoney(item.marketValue)}</td>
                  <td className={valueClass(item.unrealizedPnl)}>
                    {item.available
                      ? formatSignedMoney(item.unrealizedPnl)
                      : 'Unavailable'}
                  </td>
                  <td className={valueClass(item.pnlPercent)}>
                    {item.available
                      ? formatSignedPercent(item.pnlPercent)
                      : 'Unavailable'}
                  </td>
                  <td>
                    {!item.available ? (
                      <span className="flag flag--warning">UNPRICED</span>
                    ) : (
                      <div className="quote-flags">
                        {item.mock && <span className="flag">MOCK</span>}
                        {item.source === 'FINNHUB' && (
                          <span className="flag">FINNHUB</span>
                        )}
                        {item.source === 'FINNHUB' &&
                          !item.cached &&
                          !item.stale && (
                            <span className="flag">LIVE</span>
                          )}
                        {item.cached && <span className="flag">CACHED</span>}
                        {item.stale && (
                          <span className="flag flag--warning">STALE</span>
                        )}
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function HistoryPanel({
  history,
  loading,
  error,
  range,
  onRange,
}: {
  history: ValuationHistory | null
  loading: boolean
  error: string
  range: HistoryRange
  onRange: (range: HistoryRange) => void
}) {
  return (
    <div className="panel dashboard-panel">
      <div className="history-heading">
        <div>
          <h3>Valuation history</h3>
          <p>Market value and unrealized P&amp;L snapshots</p>
        </div>
        <div className="range-tabs" aria-label="History range">
          {ranges.map((item) => (
            <button
              key={item}
              type="button"
              aria-pressed={range === item}
              onClick={() => onRange(item)}
            >
              {item}
            </button>
          ))}
        </div>
      </div>
      {loading && <p className="table-state">Loading valuation history…</p>}
      {error && <p className="table-state table-state--error">{error}</p>}
      {!loading && !error && history?.items.length === 0 && (
        <p className="table-state">
          No valuation snapshots yet. Refresh the dashboard to capture one.
        </p>
      )}
      {!loading && !error && history && history.items.length > 0 && (
        <ValuationChart items={history.items} />
      )}
    </div>
  )
}

function ValuationChart({ items }: { items: ValuationSnapshot[] }) {
  const width = 800
  const height = 260
  const padding = 34
  const values = items.flatMap((item) => [
    item.totalMarketValue,
    item.unrealizedPnl,
  ])
  const min = Math.min(...values)
  const max = Math.max(...values)
  const spread = max - min || 1
  const point = (value: number, index: number) => {
    const x =
      items.length === 1
        ? width / 2
        : padding +
          (index * (width - padding * 2)) / (items.length - 1)
    const y =
      height - padding -
      ((value - min) / spread) * (height - padding * 2)
    return { x, y }
  }
  const market = items.map((item, index) =>
    point(item.totalMarketValue, index),
  )
  const pnl = items.map((item, index) =>
    point(item.unrealizedPnl, index),
  )
  const points = (values: { x: number; y: number }[]) =>
    values.map(({ x, y }) => `${x},${y}`).join(' ')

  return (
    <div className="valuation-chart">
      <div className="chart-legend">
        <span><i className="legend-market" />Market Value</span>
        <span><i className="legend-pnl" />Unrealized P&amp;L</span>
      </div>
      <svg
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label={`Valuation history chart with ${items.length} ${
          items.length === 1 ? 'point' : 'points'
        }`}
      >
        <line
          x1={padding}
          y1={height - padding}
          x2={width - padding}
          y2={height - padding}
          className="chart-axis"
        />
        <polyline points={points(market)} className="chart-market" />
        <polyline points={points(pnl)} className="chart-pnl" />
        {items.map((item, index) => (
          <g key={item.id}>
            <circle
              cx={market[index].x}
              cy={market[index].y}
              r="6"
              className="chart-market-point"
            >
              <title>{tooltip(item)}</title>
            </circle>
            <circle
              cx={pnl[index].x}
              cy={pnl[index].y}
              r="5"
              className="chart-pnl-point"
            >
              <title>{tooltip(item)}</title>
            </circle>
          </g>
        ))}
      </svg>
    </div>
  )
}

function RecentActivity({
  dashboard,
  onViewActivity,
}: {
  dashboard: DashboardResponse
  onViewActivity?: () => void
}) {
  return (
    <div className="panel dashboard-panel dashboard-activity">
      <div className="recent-activity-heading">
        <div>
          <p className="section-kicker">Latest ledger events</p>
          <h3>Recent Activity</h3>
        </div>
        {onViewActivity && (
          <button
            type="button"
            className="activity-link"
            onClick={onViewActivity}
          >
            View all activity
          </button>
        )}
      </div>
      {dashboard.recentActivity.length === 0 ? (
        <p className="table-state">No activity yet.</p>
      ) : (
        <ol className="activity-timeline">
          {dashboard.recentActivity.map((trade) => (
            <li key={trade.id}>
              <span
                className={`activity-marker activity-marker--${trade.side.toLowerCase()}`}
                aria-hidden="true"
              >
                {trade.side === 'BUY' ? '↓' : '↑'}
              </span>
              <div className="activity-summary">
                <div>
                  <strong>{trade.ticker}</strong>
                  <span className={`side-pill side-pill--${trade.side.toLowerCase()}`}>
                    {trade.side}
                  </span>
                  <span className="status-pill">{trade.status}</span>
                </div>
                <p>
                  {formatDecimal(trade.quantity)} shares at{' '}
                  {formatMoney(trade.tradePrice)}
                </p>
                <small>
                  {trade.accountName} · Executed{' '}
                  {formatDateTime(trade.executedAt)}
                </small>
                {trade.cancelledAt && (
                  <small className="activity-cancelled">
                    {trade.cancellationReason ?? 'CANCELLED'} ·{' '}
                    {formatDateTime(trade.cancelledAt)}
                  </small>
                )}
              </div>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}

function tooltip(item: ValuationSnapshot) {
  const date = new Date(item.capturedAt)
  return [
    `UTC: ${date.toISOString()}`,
    `Local: ${date.toLocaleString()}`,
    `Market Value: ${item.totalMarketValue}`,
    `Unrealized P&L: ${item.unrealizedPnl}`,
  ].join(' · ')
}

function valueClass(value: number | null) {
  if (value === null || value === 0) return 'value-neutral'
  return value > 0 ? 'value-positive' : 'value-negative'
}

function pnlClass(label: string, value: string) {
  if (label !== 'Unrealized P&L' && label !== 'Return %') return ''
  if (value.startsWith('+')) return 'value-positive'
  if (value.startsWith('−')) return 'value-negative'
  return 'value-neutral'
}

function isAbort(reason: unknown) {
  return reason instanceof DOMException && reason.name === 'AbortError'
}

export default Dashboard
