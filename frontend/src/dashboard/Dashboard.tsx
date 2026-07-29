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
import { localizedStatus, useI18n } from '../i18n'
import './Dashboard.css'

const ranges: HistoryRange[] = ['1D', '7D', '30D', 'ALL']

function Dashboard({
  onViewActivity,
}: {
  onViewActivity?: () => void
}) {
  const { t } = useI18n()
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
        if (!isAbort(reason)) setError(t('dashboard.unavailable'))
      })
    return () => controller.abort()
  }, [t])

  useEffect(() => {
    const controller = new AbortController()
    getDashboard(accountId || undefined, controller.signal)
      .then(setDashboard)
      .catch((reason: unknown) => {
        if (!isAbort(reason)) setError(t('dashboard.unavailable'))
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [accountId, t])

  useEffect(() => {
    const controller = new AbortController()
    getDashboardHistory(range, accountId || undefined, controller.signal)
      .then(setHistory)
      .catch((reason: unknown) => {
        if (!isAbort(reason)) {
          setHistoryError(t('dashboard.historyUnavailable'))
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setHistoryLoading(false)
      })
    return () => controller.abort()
  }, [accountId, range, t])

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
      setError(t('dashboard.refreshFailed'))
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
          <p className="section-kicker">{t('dashboard.kicker')}</p>
          <h2 id="dashboard-heading">{t('nav.dashboard')}</h2>
        </div>
        <div className="dashboard-controls">
          <label>
            {t('common.account')}
            <select
              value={accountId}
              onChange={(event) => selectAccount(event.target.value)}
            >
              <option value="">{t('common.allAccounts')}</option>
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
            {refreshing ? t('common.refreshing') : t('common.refresh')}
          </button>
        </div>
      </div>

      {loading && <p className="table-state">{t('dashboard.loading')}</p>}
      {error && <p className="table-state table-state--error">{error}</p>}
      {!loading && dashboard && (
        <>
          <DashboardStatus dashboard={dashboard} />
          {dashboard.totals.stale && (
            <p className="notice dashboard-stale-banner" role="status">
              {t('dashboard.staleNotice')}
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
  const { locale, t } = useI18n()
  const sources = Array.from(
    new Set(
      dashboard.positions
        .filter((position) => position.available && position.source)
        .map((position) => position.source),
    ),
  )
  const source = sources.length > 0 ? sources.join(' + ') : t('status.noQuotes')
  return (
    <div className="dashboard-status">
      <span>
        {t('dashboard.lastUpdated')}{' '}
        <time dateTime={dashboard.capturedAt}>
          {formatDateTime(dashboard.capturedAt, locale)}
        </time>
      </span>
      <div className="quote-flags" aria-label={t('dashboard.quoteStatusLabel')}>
        <span className="flag">{t('dashboard.source', { source })}</span>
        {dashboard.totals.mock && <span className="flag">{t('status.mock')}</span>}
        {dashboard.quoteStatus.cached > 0 && (
          <span className="flag">{t('status.cached')}</span>
        )}
        {dashboard.totals.stale && (
          <span className="flag flag--warning">{t('status.stale')}</span>
        )}
        {!dashboard.totals.stale && <span className="flag">{t('status.fresh')}</span>}
        {!dashboard.totals.complete && (
          <span className="flag flag--warning">{t('status.incomplete')}</span>
        )}
        {dashboard.totals.complete && <span className="flag">{t('status.complete')}</span>}
      </div>
    </div>
  )
}

function Kpis({ dashboard }: { dashboard: DashboardResponse }) {
  const { locale, t } = useI18n()
  const totals = dashboard.totals
  const cards = [
    [t('dashboard.totalMarketValue'), formatMoney(totals.totalMarketValue, locale), false],
    [t('dashboard.totalCostBasis'), formatMoney(totals.totalCostBasis, locale), false],
    [t('common.unrealizedPnl'), formatSignedMoney(totals.totalUnrealizedPnl, locale), true],
    [t('dashboard.returnPercent'), formatSignedPercent(totals.totalPnlPercent, locale), true],
    [t('dashboard.openPositions'), String(totals.positionCount), false],
    [t('dashboard.unpricedPositions'), String(totals.unpricedPositionCount), false],
  ]
  return (
    <div className="metrics dashboard-metrics">
      {cards.map(([label, value, tracksPnl]) => (
        <article key={String(label)}>
          <span>{label}</span>
          <strong className={tracksPnl ? pnlClass(String(value)) : ''}>{value}</strong>
        </article>
      ))}
    </div>
  )
}

function PositionTable({ items }: { items: PositionPnl[] }) {
  const { locale, t } = useI18n()
  return (
    <div className="panel dashboard-panel">
      <h3>{t('dashboard.positionPnl')}</h3>
      {items.length === 0 ? (
        <p className="table-state">{t('dashboard.noPositions')}</p>
      ) : (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>{t('common.ticker')}</th>
                <th>{t('common.quantity')}</th>
                <th>{t('common.averageCost')}</th>
                <th>{t('common.marketPrice')}</th>
                <th>{t('common.marketValue')}</th>
                <th>{t('common.unrealizedPnl')}</th>
                <th>{t('common.return')}</th>
                <th>{t('common.quoteStatus')}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.ticker}>
                  <td><strong>{item.ticker}</strong></td>
                  <td>{formatDecimal(item.quantity, locale)}</td>
                  <td>{formatMoney(item.averageCost, locale)}</td>
                  <td>{formatNullableMoney(item.marketPrice, locale)}</td>
                  <td>{formatNullableMoney(item.marketValue, locale)}</td>
                  <td className={valueClass(item.unrealizedPnl)}>
                    {item.available
                      ? formatSignedMoney(item.unrealizedPnl, locale)
                      : t('common.unavailable')}
                  </td>
                  <td className={valueClass(item.pnlPercent)}>
                    {item.available
                      ? formatSignedPercent(item.pnlPercent, locale)
                      : t('common.unavailable')}
                  </td>
                  <td>
                    {!item.available ? (
                      <span className="flag flag--warning">{t('status.unpriced')}</span>
                    ) : (
                      <div className="quote-flags">
                        {item.mock && <span className="flag">{t('status.mock')}</span>}
                        {item.source === 'FINNHUB' && (
                          <span className="flag">FINNHUB</span>
                        )}
                        {item.source === 'FINNHUB' &&
                          !item.cached &&
                          !item.stale && (
                            <span className="flag">{t('status.live')}</span>
                          )}
                        {item.cached && <span className="flag">{t('status.cached')}</span>}
                        {item.stale && (
                          <span className="flag flag--warning">{t('status.stale')}</span>
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
  const { t } = useI18n()
  return (
    <div className="panel dashboard-panel">
      <div className="history-heading">
        <div>
          <h3>{t('dashboard.history')}</h3>
          <p>{t('dashboard.historyDescription')}</p>
        </div>
        <div className="range-tabs" aria-label={t('dashboard.historyRange')}>
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
      {loading && <p className="table-state">{t('dashboard.loadingHistory')}</p>}
      {error && <p className="table-state table-state--error">{error}</p>}
      {!loading && !error && history?.items.length === 0 && (
        <p className="table-state">
          {t('dashboard.noSnapshots')}
        </p>
      )}
      {!loading && !error && history && history.items.length > 0 && (
        <ValuationChart items={history.items} />
      )}
    </div>
  )
}

function ValuationChart({ items }: { items: ValuationSnapshot[] }) {
  const { locale, t } = useI18n()
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
        <span><i className="legend-market" />{t('dashboard.chartMarketValue')}</span>
        <span><i className="legend-pnl" />{t('common.unrealizedPnl')}</span>
      </div>
      <svg
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label={t('dashboard.chartAria', {
          count: items.length,
          points:
            items.length === 1 ? t('dashboard.point') : t('dashboard.points'),
        })}
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
              <title>{tooltip(item, locale, t)}</title>
            </circle>
            <circle
              cx={pnl[index].x}
              cy={pnl[index].y}
              r="5"
              className="chart-pnl-point"
            >
              <title>{tooltip(item, locale, t)}</title>
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
  const { locale, t } = useI18n()
  return (
    <div className="panel dashboard-panel dashboard-activity">
      <div className="recent-activity-heading">
        <div>
          <p className="section-kicker">{t('dashboard.latestEvents')}</p>
          <h3>{t('dashboard.recentActivity')}</h3>
        </div>
        {onViewActivity && (
          <button
            type="button"
            className="activity-link"
            onClick={onViewActivity}
          >
            {t('dashboard.viewAll')}
          </button>
        )}
      </div>
      {dashboard.recentActivity.length === 0 ? (
        <p className="table-state">{t('dashboard.noActivity')}</p>
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
                    {localizedStatus(trade.side, t)}
                  </span>
                  <span className="status-pill">{localizedStatus(trade.status, t)}</span>
                </div>
                <p>
                  {t('dashboard.sharesAt', {
                    quantity: formatDecimal(trade.quantity, locale),
                    price: formatMoney(trade.tradePrice, locale),
                  })}
                </p>
                <small>
                  {t('dashboard.executed', {
                    account: trade.accountName,
                    date: formatDateTime(trade.executedAt, locale),
                  })}
                </small>
                {trade.cancelledAt && (
                  <small className="activity-cancelled">
                    {trade.cancellationReason
                      ? localizedStatus(trade.cancellationReason, t)
                      : t('status.cancelled')} ·{' '}
                    {formatDateTime(trade.cancelledAt, locale)}
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

function tooltip(
  item: ValuationSnapshot,
  locale: string,
  t: ReturnType<typeof useI18n>['t'],
) {
  const date = new Date(item.capturedAt)
  return [
    `${t('dashboard.tooltipUtc')}: ${date.toISOString()}`,
    `${t('dashboard.tooltipLocal')}: ${date.toLocaleString(locale)}`,
    `${t('dashboard.chartMarketValue')}: ${item.totalMarketValue}`,
    `${t('common.unrealizedPnl')}: ${item.unrealizedPnl}`,
  ].join(' · ')
}

function valueClass(value: number | null) {
  if (value === null || value === 0) return 'value-neutral'
  return value > 0 ? 'value-positive' : 'value-negative'
}

function pnlClass(value: string) {
  if (value.startsWith('+')) return 'value-positive'
  if (value.startsWith('−')) return 'value-negative'
  return 'value-neutral'
}

function isAbort(reason: unknown) {
  return reason instanceof DOMException && reason.name === 'AbortError'
}

export default Dashboard
