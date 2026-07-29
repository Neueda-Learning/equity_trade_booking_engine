import { useEffect, useId, useState } from 'react'
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
            positions={dashboard.positions}
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
  positions,
  loading,
  error,
  range,
  onRange,
}: {
  history: ValuationHistory | null
  positions: PositionPnl[]
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
      {loading && !history && (
        <p className="table-state">{t('dashboard.loadingHistory')}</p>
      )}
      {error && <p className="table-state table-state--error">{error}</p>}
      {!loading && !error && history?.items.length === 0 && (
        <p className="table-state">
          {t('dashboard.noSnapshots')}
        </p>
      )}
      {!loading
        && !error
        && history?.fallback && (
          <p className="history-warning">
            {t('dashboard.historyFallback')}
          </p>
        )}
      {!loading
        && !error
        && history?.items.some((item) => !item.complete) && (
          <p className="history-warning">
            {t('dashboard.historyIncomplete')}
          </p>
        )}
      {!error && history && history.items.length > 0 && (
        <ValuationChart
          items={history.items}
          positions={positions}
          range={range}
          updating={loading}
        />
      )}
    </div>
  )
}

type ChartSeries = 'market' | 'pnl'

function ValuationChart({
  items,
  positions,
  range,
  updating,
}: {
  items: ValuationSnapshot[]
  positions: PositionPnl[]
  range: HistoryRange
  updating: boolean
}) {
  const { locale, t } = useI18n()
  const gradientId = useId().replaceAll(':', '')
  const [series, setSeries] = useState<ChartSeries>('market')
  const [activeIndex, setActiveIndex] = useState<number | null>(null)
  const width = 720
  const height = 400
  const plot = { top: 36, right: 24, bottom: 48, left: 80 }
  const values = items.map((item) =>
    series === 'market' ? item.totalMarketValue : item.unrealizedPnl,
  )
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
    const y = plot.top + ((domainMax - value) / spread) * plotHeight
    return { x, y }
  }
  const chartPoints = values.map(point)
  const linePath = smoothChartPath(chartPoints)
  const areaPath = `${linePath} L ${
    chartPoints.at(-1)?.x ?? plot.left
  },${height - plot.bottom} L ${
    chartPoints[0]?.x ?? plot.left
  },${height - plot.bottom} Z`
  const yTicks = scale.ticks
    .toReversed()
    .map((value) => ({ value, y: point(value, 0).y }))
  const xTickIndexes = chartTickIndexes(items.length, 5)
  const zeroY =
    domainMin <= 0 && domainMax >= 0 ? point(0, 0).y : null
  const resolvedIndex = Math.min(
    activeIndex ?? items.length - 1,
    items.length - 1,
  )
  const selectedItem = items[resolvedIndex]
  const selectedPosition = chartPoints[resolvedIndex]
  const selectedValue = values[resolvedIndex]
  const rangeChange = selectedValue - values[0]
  const rangeChangePercent =
    values[0] === 0 ? null : (rangeChange / Math.abs(values[0])) * 100
  const tone =
    rangeChange > 0 ? 'positive' : rangeChange < 0 ? 'negative' : 'neutral'
  const seriesLabel =
    series === 'market'
      ? t('dashboard.chartMarketValue')
      : t('common.unrealizedPnl')

  const moveCrosshair = (clientX: number, svg: SVGSVGElement) => {
    const rect = svg.getBoundingClientRect()
    const viewBoxX = ((clientX - rect.left) / rect.width) * width
    const ratio = Math.min(
      Math.max((viewBoxX - plot.left) / plotWidth, 0),
      1,
    )
    setActiveIndex(
      items.length === 1 ? 0 : Math.round(ratio * (items.length - 1)),
    )
  }

  return (
    <div className={`valuation-chart chart-tone--${tone}`}>
      <div className="chart-terminal-header">
        <div className="chart-quote">
          <span>{seriesLabel}</span>
          <strong>{formatMoney(selectedValue, locale)}</strong>
          <div className={`chart-change value-${tone}`}>
            <span>{formatSignedMoney(rangeChange, locale)}</span>
            {rangeChangePercent !== null && (
              <span>{formatSignedPercent(rangeChangePercent, locale)}</span>
            )}
            <small>{range}</small>
          </div>
        </div>
        <div
          className="chart-series-tabs"
          aria-label={t('dashboard.chartSeries')}
        >
          <button
            type="button"
            aria-pressed={series === 'market'}
            onClick={() => {
              setSeries('market')
              setActiveIndex(null)
            }}
          >
            {t('dashboard.chartMarketValue')}
          </button>
          <button
            type="button"
            aria-pressed={series === 'pnl'}
            onClick={() => {
              setSeries('pnl')
              setActiveIndex(null)
            }}
          >
            {t('common.unrealizedPnl')}
          </button>
        </div>
      </div>
      <div className="chart-visual-grid">
        <div className="valuation-chart-scroll">
          <div
            className={`valuation-chart-canvas ${
              updating ? 'is-updating' : ''
            }`}
            aria-busy={updating}
          >
          {updating && (
            <span className="chart-updating" role="status">
              <i aria-hidden="true" />
              {t('dashboard.chartUpdating')}
            </span>
          )}
          <svg
            viewBox={`0 0 ${width} ${height}`}
            role="img"
            aria-label={t('dashboard.chartAria', {
              count: items.length,
              points:
                items.length === 1 ? t('dashboard.point') : t('dashboard.points'),
            })}
            onPointerMove={(event) =>
              moveCrosshair(event.clientX, event.currentTarget)}
            onPointerLeave={() => setActiveIndex(null)}
          >
            <defs>
              <linearGradient
                id={`${gradientId}-area`}
                x1="0"
                y1="0"
                x2="0"
                y2="1"
              >
                <stop offset="0%" stopColor="currentColor" stopOpacity="0.32" />
                <stop offset="78%" stopColor="currentColor" stopOpacity="0.04" />
                <stop offset="100%" stopColor="currentColor" stopOpacity="0" />
              </linearGradient>
            </defs>
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
                  x1={chartPoints[index].x}
                  y1={plot.top}
                  x2={chartPoints[index].x}
                  y2={height - plot.bottom}
                  className="chart-grid chart-grid--vertical"
                />
                <text
                  x={chartPoints[index].x}
                  y={height - plot.bottom + 26}
                  className="chart-tick"
                  textAnchor="middle"
                >
                  {formatAxisDate(items[index], range, locale)}
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
            <path
              key={`${series}-${items[0].id}-${items.at(-1)?.id}`}
              d={areaPath}
              className="chart-area"
              fill={`url(#${gradientId}-area)`}
            />
            <path
              key={`line-${series}-${items[0].id}-${items.at(-1)?.id}`}
              d={linePath}
              className="chart-line"
            />
            <line
              x1={selectedPosition.x}
              y1={plot.top}
              x2={selectedPosition.x}
              y2={height - plot.bottom}
              className="chart-crosshair"
            />
            <line
              x1={plot.left}
              y1={selectedPosition.y}
              x2={width - plot.right}
              y2={selectedPosition.y}
              className="chart-crosshair chart-crosshair--horizontal"
            />
            {items.map((item, index) => (
              <circle
                key={`${series}-${item.id}`}
                cx={chartPoints[index].x}
                cy={chartPoints[index].y}
                r={resolvedIndex === index ? 6 : 12}
                tabIndex={0}
                aria-label={t('dashboard.chartPointAria', {
                  series: seriesLabel,
                  date: formatDate(item.valuationDate, locale),
                  value: formatMoney(values[index], locale),
                })}
                className={`chart-data-point ${
                  resolvedIndex === index ? 'is-active' : ''
                }`}
                onPointerEnter={() => setActiveIndex(index)}
                onFocus={() => setActiveIndex(index)}
                onBlur={() => setActiveIndex(null)}
              >
                <title>
                  {formatDateTime(item.capturedAt, locale)} · {seriesLabel}:{' '}
                  {values[index]}
                </title>
              </circle>
            ))}
          </svg>
            <div className="chart-cursor-readout">
              <span>{formatDateTime(selectedItem.capturedAt, locale)}</span>
              <strong>{formatMoney(selectedValue, locale)}</strong>
            </div>
          </div>
        </div>
        <AllocationPie positions={positions} />
      </div>
    </div>
  )
}

const allocationColors = [
  '#60a5fa',
  '#f59e0b',
  '#a78bfa',
  '#22d3ee',
  '#f472b6',
  '#94a3b8',
]

function AllocationPie({ positions }: { positions: PositionPnl[] }) {
  const { locale, t } = useI18n()
  const holdings = positions
    .filter(
      (position) =>
        position.available
        && position.marketValue !== null
        && position.marketValue > 0,
    )
    .map((position) => ({
      ticker: position.ticker,
      value: position.marketValue as number,
    }))
    .sort((left, right) => right.value - left.value)
  const total = holdings.reduce((sum, holding) => sum + holding.value, 0)
  const leading = holdings.slice(0, 5)
  const otherValue = holdings
    .slice(5)
    .reduce((sum, holding) => sum + holding.value, 0)
  const slices = [
    ...leading,
    ...(otherValue > 0
      ? [{ ticker: t('dashboard.allocationOther'), value: otherValue }]
      : []),
  ].map((slice, index) => ({
    ...slice,
    color: allocationColors[index],
  }))
  const radius = 76
  const circumference = 2 * Math.PI * radius
  let accumulated = 0
  const segments = slices.map((slice) => {
    const fraction = slice.value / total
    const segment = {
      ...slice,
      fraction,
      offset: accumulated,
    }
    accumulated += fraction
    return segment
  })

  return (
    <section className="allocation-panel">
      <div className="allocation-heading">
        <h4>{t('dashboard.allocationTitle')}</h4>
        <p>{t('dashboard.allocationDescription')}</p>
      </div>
      {segments.length === 0 ? (
        <div className="allocation-empty">
          <span aria-hidden="true" />
          <p>{t('dashboard.allocationEmpty')}</p>
        </div>
      ) : (
        <div className="allocation-content">
          <svg
            className="allocation-pie"
            viewBox="0 0 220 220"
            role="img"
            aria-label={t('dashboard.allocationAria', {
              count: holdings.length,
            })}
          >
            <circle
              cx="110"
              cy="110"
              r={radius}
              className="allocation-track"
            />
            {segments.map((segment) => (
              <circle
                key={segment.ticker}
                cx="110"
                cy="110"
                r={radius}
                fill="none"
                stroke={segment.color}
                strokeWidth="36"
                strokeDasharray={`${
                  segment.fraction * circumference
                } ${circumference}`}
                strokeDashoffset={-segment.offset * circumference}
                transform="rotate(-90 110 110)"
                className="allocation-segment"
                tabIndex={0}
                aria-label={`${segment.ticker}, ${formatMoney(
                  segment.value,
                  locale,
                )}, ${formatAllocationPercent(
                  segment.fraction,
                  locale,
                )}`}
              >
                <title>
                  {segment.ticker} · {formatMoney(segment.value, locale)} ·{' '}
                  {formatAllocationPercent(segment.fraction, locale)}
                </title>
              </circle>
            ))}
            <text
              x="110"
              y="100"
              textAnchor="middle"
              className="allocation-center-label"
            >
              {t('dashboard.allocationTotal')}
            </text>
            <text
              x="110"
              y="126"
              textAnchor="middle"
              className="allocation-center-value"
            >
              {formatAxisMoney(total, locale)}
            </text>
          </svg>
          <ol className="allocation-legend">
            {segments.map((segment) => (
              <li key={segment.ticker}>
                <i
                  aria-hidden="true"
                  style={{ backgroundColor: segment.color }}
                />
                <div>
                  <strong>{segment.ticker}</strong>
                  <span>{formatMoney(segment.value, locale)}</span>
                </div>
                <b>
                  {formatAllocationPercent(segment.fraction, locale)}
                </b>
              </li>
            ))}
          </ol>
        </div>
      )}
    </section>
  )
}

function smoothChartPath(points: { x: number; y: number }[]) {
  if (points.length === 0) return ''
  if (points.length === 1) {
    return `M ${points[0].x},${points[0].y}`
  }
  return points.slice(1).reduce((path, point, index) => {
    const previous = points[index]
    const midpoint = (previous.x + point.x) / 2
    return `${path} C ${midpoint},${previous.y} ${midpoint},${point.y} ${point.x},${point.y}`
  }, `M ${points[0].x},${points[0].y}`)
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
  const valueMin = Math.min(...values)
  const valueMax = Math.max(...values)
  const valueRange = valueMax - valueMin
  const padding =
    valueRange === 0
      ? Math.max(Math.abs(valueMax) * 0.08, 1)
      : valueRange * 0.12
  const rawMin = valueMin - padding
  const rawMax = valueMax + padding
  const roughStep = (rawMax - rawMin) / Math.max(targetTicks - 1, 1)
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

function formatAxisDate(
  item: ValuationSnapshot,
  range: HistoryRange,
  locale: string,
) {
  return new Intl.DateTimeFormat(
    locale,
    range === '1D'
      ? {
          hour: '2-digit',
          minute: '2-digit',
        }
      : {
          month: 'short',
          day: 'numeric',
          timeZone: 'UTC',
        },
  ).format(
    range === '1D'
      ? new Date(item.capturedAt)
      : new Date(`${item.valuationDate}T00:00:00Z`),
  )
}

function formatAxisMoney(value: number, locale: string) {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: 'USD',
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value)
}

function formatAllocationPercent(value: number, locale: string) {
  return new Intl.NumberFormat(locale, {
    style: 'percent',
    minimumFractionDigits: 1,
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
