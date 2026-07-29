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
  formatDate,
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
      {!loading
        && !error
        && history?.items.some((item) => !item.complete) && (
          <p className="history-warning">
            {t('dashboard.historyIncomplete')}
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
  const [activePoint, setActivePoint] = useState<{
    index: number
    series: 'market' | 'pnl'
  } | null>(null)
  const width = 900
  const height = 360
  const plot = { top: 24, right: 28, bottom: 62, left: 104 }
  const values = items.flatMap((item) => [
    item.totalMarketValue,
    item.unrealizedPnl,
  ])
  const scale = niceChartScale(values, 5)
  const { domainMin, domainMax } = scale
  const spread = domainMax - domainMin
  const plotWidth = width - plot.left - plot.right
  const plotHeight = height - plot.top - plot.bottom
  const point = (value: number, index: number) => {
    const x =
      items.length === 1
        ? plot.left + plotWidth / 2
        : plot.left + (index * plotWidth) / (items.length - 1)
    const y =
      plot.top + ((domainMax - value) / spread) * plotHeight
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
  const yTicks = scale.ticks
    .toReversed()
    .map((value) => ({ value, y: point(value, 0).y }))
  const xTickIndexes = chartTickIndexes(items.length, 5)
  const zeroY =
    domainMin <= 0 && domainMax >= 0 ? point(0, 0).y : null
  const selectedItem = activePoint ? items[activePoint.index] : null
  const selectedPosition = activePoint
    ? activePoint.series === 'market'
      ? market[activePoint.index]
      : pnl[activePoint.index]
    : null
  const tooltipPosition = selectedPosition
    ? {
        x: selectedPosition.x,
        y: Math.min(Math.max(selectedPosition.y, 74), height - 74),
      }
    : null

  function pointHandlers(index: number, series: 'market' | 'pnl') {
    return {
      onMouseEnter: () => setActivePoint({ index, series }),
      onMouseLeave: () => setActivePoint(null),
      onFocus: () => setActivePoint({ index, series }),
      onBlur: () => setActivePoint(null),
    }
  }

  return (
    <div className="valuation-chart">
      <div className="chart-legend">
        <span><i className="legend-market" />{t('dashboard.chartMarketValue')}</span>
        <span><i className="legend-pnl" />{t('common.unrealizedPnl')}</span>
      </div>
      <div className="valuation-chart-scroll">
        <div className="valuation-chart-canvas">
          <svg
            viewBox={`0 0 ${width} ${height}`}
            role="img"
            aria-label={t('dashboard.chartAria', {
              count: items.length,
              points:
                items.length === 1 ? t('dashboard.point') : t('dashboard.points'),
            })}
          >
            {yTicks.map((tick) => (
              <g key={tick.value}>
                <line
                  x1={plot.left}
                  y1={tick.y}
                  x2={width - plot.right}
                  y2={tick.y}
                  className="chart-grid"
                />
                <text
                  x={plot.left - 12}
                  y={tick.y}
                  className="chart-tick"
                  dominantBaseline="middle"
                  textAnchor="end"
                >
                  {formatAxisMoney(tick.value, locale)}
                </text>
              </g>
            ))}
            {xTickIndexes.map((index) => (
              <g key={items[index].id}>
                <line
                  x1={market[index].x}
                  y1={plot.top}
                  x2={market[index].x}
                  y2={height - plot.bottom}
                  className="chart-grid chart-grid--vertical"
                />
                <text
                  x={market[index].x}
                  y={height - plot.bottom + 24}
                  className="chart-tick"
                  textAnchor="middle"
                >
                  {formatAxisDate(items[index].valuationDate, locale)}
                </text>
              </g>
            ))}
            {zeroY !== null && (
              <line
                x1={plot.left}
                y1={zeroY}
                x2={width - plot.right}
                y2={zeroY}
                className="chart-zero"
              />
            )}
            <line
              x1={plot.left}
              y1={plot.top}
              x2={plot.left}
              y2={height - plot.bottom}
              className="chart-axis"
            />
            <line
              x1={plot.left}
              y1={height - plot.bottom}
              x2={width - plot.right}
              y2={height - plot.bottom}
              className="chart-axis"
            />
            <text
              x={18}
              y={plot.top + plotHeight / 2}
              className="chart-axis-label"
              textAnchor="middle"
              transform={`rotate(-90 18 ${plot.top + plotHeight / 2})`}
            >
              {t('dashboard.chartValueAxis')}
            </text>
            <text
              x={plot.left + plotWidth / 2}
              y={height - 12}
              className="chart-axis-label"
              textAnchor="middle"
            >
              {t('dashboard.chartTimeAxis')}
            </text>
            <polyline points={points(market)} className="chart-market" />
            <polyline points={points(pnl)} className="chart-pnl" />
            {items.map((item, index) => (
              <g key={item.id}>
                <circle
                  cx={market[index].x}
                  cy={market[index].y}
                  r="7"
                  tabIndex={0}
                  aria-label={t('dashboard.chartPointAria', {
                    series: t('dashboard.chartMarketValue'),
                    date: formatDate(item.valuationDate, locale),
                    value: formatMoney(item.totalMarketValue, locale),
                  })}
                  className={`chart-market-point ${
                    activePoint?.index === index
                      && activePoint.series === 'market'
                      ? 'is-active'
                      : ''
                  }`}
                  {...pointHandlers(index, 'market')}
                >
                  <title>{tooltip(item, locale, t)}</title>
                </circle>
                <circle
                  cx={pnl[index].x}
                  cy={pnl[index].y}
                  r="6"
                  tabIndex={0}
                  aria-label={t('dashboard.chartPointAria', {
                    series: t('common.unrealizedPnl'),
                    date: formatDate(item.valuationDate, locale),
                    value: formatMoney(item.unrealizedPnl, locale),
                  })}
                  className={`chart-pnl-point ${
                    activePoint?.index === index
                      && activePoint.series === 'pnl'
                      ? 'is-active'
                      : ''
                  }`}
                  {...pointHandlers(index, 'pnl')}
                >
                  <title>{tooltip(item, locale, t)}</title>
                </circle>
              </g>
            ))}
          </svg>
          {selectedItem && tooltipPosition && (
            <div
              className={`chart-tooltip ${
                tooltipPosition.x > width * 0.58
                  ? 'chart-tooltip--left'
                  : 'chart-tooltip--right'
              }`}
              role="status"
              style={{
                left: `${(tooltipPosition.x / width) * 100}%`,
                top: `${(tooltipPosition.y / height) * 100}%`,
              }}
            >
              <strong>{formatDate(selectedItem.valuationDate, locale)}</strong>
              <span>
                <i className="legend-market" />
                {t('dashboard.chartMarketValue')}
                <b>{formatMoney(selectedItem.totalMarketValue, locale)}</b>
              </span>
              <span>
                <i className="legend-pnl" />
                {t('common.unrealizedPnl')}
                <b>{formatMoney(selectedItem.unrealizedPnl, locale)}</b>
              </span>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function chartTickIndexes(length: number, maxTicks: number) {
  if (length <= maxTicks) return Array.from({ length }, (_, index) => index)
  return Array.from(
    new Set(
      Array.from({ length: maxTicks }, (_, index) =>
        Math.round((index * (length - 1)) / (maxTicks - 1)),
      ),
    ),
  )
}

function niceChartScale(values: number[], targetTicks: number) {
  const rawMin = Math.min(...values, 0)
  const rawMax = Math.max(...values, 0)
  const rawRange = rawMax - rawMin || Math.max(Math.abs(rawMax), 1)
  const roughStep = rawRange / Math.max(targetTicks - 1, 1)
  const magnitude = 10 ** Math.floor(Math.log10(roughStep))
  const normalized = roughStep / magnitude
  const factor =
    normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10
  const step = factor * magnitude
  let domainMin = Math.floor(rawMin / step) * step
  let domainMax = Math.ceil(rawMax / step) * step
  if (domainMin === domainMax) {
    domainMin -= step
    domainMax += step
  }
  const precision = Math.max(0, -Math.floor(Math.log10(step))) + 2
  const ticks: number[] = []
  for (
    let value = domainMin;
    value <= domainMax + step / 2;
    value += step
  ) {
    ticks.push(Number(value.toFixed(precision)))
  }
  return { domainMin, domainMax, ticks }
}

function formatAxisDate(value: string, locale: string) {
  return new Intl.DateTimeFormat(locale, {
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(`${value}T00:00:00Z`))
}

function formatAxisMoney(value: number, locale: string) {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: 'USD',
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value)
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
                <small>
                  {t('dashboard.operated', {
                    date: formatDateTime(trade.createdAt, locale),
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
  return [
    formatDate(item.valuationDate, locale),
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
