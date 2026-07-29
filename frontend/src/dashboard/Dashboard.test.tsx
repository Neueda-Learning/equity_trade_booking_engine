import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { PositionPnl } from '../api'
import Dashboard from './Dashboard'

const accounts = [
  account('primary', 'Primary Account'),
  account('second', 'Second Account'),
]

describe('P&L Dashboard', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows KPIs and explicit gain and loss text', async () => {
    vi.stubGlobal(
      'fetch',
      routedFetch(
        dashboard({
          positions: [
            position('AAPL', 100, 120, 20, 20),
            position('MSFT', 100, 90, -10, -10),
          ],
        }),
        history([]),
      ),
    )

    render(<Dashboard />)

    expect(await screen.findByText('Total Market Value')).toBeInTheDocument()
    expect(screen.getByText('$210.00')).toBeInTheDocument()
    expect(screen.getByText('+$10.00 Gain')).toBeInTheDocument()
    expect(screen.getByText('+5.00% Gain')).toBeInTheDocument()
    expect(screen.getByText('+$20.00 Gain')).toBeInTheDocument()
    expect(screen.getByText('−$10.00 Loss')).toBeInTheDocument()
  })

  it('filters by account and refreshes the selected dashboard', async () => {
    const fetchMock = routedFetch(dashboard(), history([]))
    vi.stubGlobal('fetch', fetchMock)
    render(<Dashboard />)
    await screen.findByText('Total Market Value')

    fireEvent.change(screen.getByLabelText('Account'), {
      target: { value: 'second' },
    })
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/dashboard?accountId=second',
        expect.objectContaining({ signal: expect.any(AbortSignal) }),
      ),
    )

    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/dashboard/refresh?accountId=second',
        expect.objectContaining({ method: 'POST' }),
      ),
    )
    expect(
      await screen.findByText('No booked trades are available for valuation.'),
    ).toBeInTheDocument()
  })

  it('shows mock cached stale and incomplete status without fake zero prices', async () => {
    vi.stubGlobal(
      'fetch',
      routedFetch(
        dashboard({
          positions: [
            {
              ...position('MISS', 100, null, null, null),
              available: false,
              source: null,
              mock: false,
              cached: false,
              stale: false,
            },
          ],
          complete: false,
          stale: true,
          unpriced: 1,
        }),
        history([]),
      ),
    )
    render(<Dashboard />)

    await screen.findByText('MISS')
    expect(screen.getByText('INCOMPLETE')).toBeInTheDocument()
    expect(screen.getByText('STALE')).toBeInTheDocument()
    expect(screen.getByText('UNPRICED')).toBeInTheDocument()
    expect(screen.getAllByText('Unavailable').length).toBeGreaterThan(1)
    expect(
      screen.getByText('Cached, stale quotes are being used. Values are not live.'),
    ).toBeInTheDocument()
  })

  it('shows FINNHUB live without MOCK and never calls stale data live', async () => {
    const live = {
      ...position('AAPL', 100, 120, 20, 20),
      source: 'FINNHUB',
      mock: false,
      cached: false,
      stale: false,
    }
    const stale = {
      ...position('MSFT', 100, 110, 10, 10),
      source: 'FINNHUB',
      mock: false,
      cached: true,
      stale: true,
    }
    vi.stubGlobal(
      'fetch',
      routedFetch(
        dashboard({ positions: [live, stale], stale: true }),
        history([]),
      ),
    )

    render(<Dashboard />)

    await screen.findByText('AAPL')
    expect(screen.getAllByText('FINNHUB')).toHaveLength(2)
    expect(screen.getByText('LIVE')).toBeInTheDocument()
    expect(screen.queryByText('MOCK')).not.toBeInTheDocument()
    expect(screen.getAllByText('STALE').length).toBeGreaterThan(0)
  })

  it('switches history range and renders empty and single-point states', async () => {
    const single = history([
      snapshot('one', '2026-07-28T09:00:00Z', 120, 20),
    ])
    const fetchMock = routedFetch(dashboard(), single)
    vi.stubGlobal('fetch', fetchMock)
    render(<Dashboard />)

    expect(
      await screen.findByRole('img', {
        name: 'Valuation history chart with 1 point',
      }),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '7D' }))
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/dashboard/history?range=7D',
        expect.objectContaining({ signal: expect.any(AbortSignal) }),
      ),
    )
  })

  it('renders an interactive trading chart with switchable metrics', async () => {
    vi.stubGlobal(
      'fetch',
      routedFetch(
        dashboard(),
        history([
          snapshot('one', '2026-07-28T09:00:00Z', 120.123456, 20.123456),
          snapshot('two', '2026-07-28T10:00:00Z', 125.654321, 25.654321),
        ]),
      ),
    )
    render(<Dashboard />)

    expect(
      await screen.findByRole('img', {
        name: 'Valuation history chart with 2 points',
      }),
    ).toBeInTheDocument()
    expect(
      screen.getAllByText(/Market Value: 120.123456/).length,
    ).toBeGreaterThan(0)
    expect(screen.getAllByText('$125.654321').length).toBeGreaterThan(0)
    const metricSelector = screen.getByLabelText('Chart metric')
    const pnlButton = metricSelector.querySelector(
      'button[aria-pressed="false"]',
    )
    expect(pnlButton).not.toBeNull()
    fireEvent.click(pnlButton!)
    expect(screen.getAllByText('$25.654321').length).toBeGreaterThan(0)

    const pnlPoint = screen.getByLabelText(
      /Unrealized P&L, .*, \$20\.123456/,
    )
    fireEvent.pointerEnter(pnlPoint)
    expect(screen.getAllByText('$20.123456').length).toBeGreaterThan(0)
  })

  it('shows a history server error without hiding dashboard data', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) => {
        const url = String(input)
        if (url === '/api/accounts') return response(accounts)
        if (url === '/api/dashboard') return response(dashboard())
        if (url.startsWith('/api/dashboard/history')) {
          return Promise.reject(new Error('offline'))
        }
        return Promise.reject(new Error('offline'))
      }),
    )
    render(<Dashboard />)

    expect(
      await screen.findByText('Valuation history is unavailable.'),
    ).toBeInTheDocument()
  })

  it('shows a dashboard server error', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) => {
        const url = String(input)
        if (url === '/api/accounts') return response(accounts)
        if (url.startsWith('/api/dashboard/history')) {
          return response(history([]))
        }
        return Promise.reject(new Error('offline'))
      }),
    )
    render(<Dashboard />)

    expect(
      await screen.findByText('Dashboard data is unavailable.'),
    ).toBeInTheDocument()
  })

  it('renders recent activity as an audit-aware timeline', async () => {
    const onViewActivity = vi.fn()
    vi.stubGlobal(
      'fetch',
      routedFetch(
        dashboard({
          recentActivity: [
            {
              id: 'trade-1',
              accountId: 'primary',
              accountName: 'Primary Account',
              ticker: 'AAPL',
              side: 'SELL',
              quantity: 2,
              tradePrice: 125.5,
              status: 'CANCELLED',
              executedAt: '2026-07-28T08:30:00Z',
              createdAt: '2026-07-28T08:35:00Z',
              cancelledAt: '2026-07-28T09:00:00Z',
              cancellationReason: 'DELETED',
            },
          ],
        }),
        history([]),
      ),
    )

    render(<Dashboard onViewActivity={onViewActivity} />)

    expect(await screen.findByText('AAPL')).toBeInTheDocument()
    expect(screen.getByText('SELL')).toBeInTheDocument()
    expect(screen.getByText('CANCELLED')).toBeInTheDocument()
    expect(screen.getByText('2 shares at $125.50')).toBeInTheDocument()
    expect(screen.getByText(/Operation recorded/)).toBeInTheDocument()
    expect(screen.getByText(/DELETED/)).toBeInTheDocument()
    fireEvent.click(
      screen.getByRole('button', { name: 'View all activity' }),
    )
    expect(onViewActivity).toHaveBeenCalledOnce()
  })
})

function routedFetch(
  dashboardPayload: ReturnType<typeof dashboard>,
  historyPayload: ReturnType<typeof history>,
) {
  return vi.fn((input: RequestInfo | URL) => {
    const url = String(input)
    if (url === '/api/accounts') return response(accounts)
    if (url.startsWith('/api/dashboard/history')) {
      return response(historyPayload)
    }
    if (url.startsWith('/api/dashboard')) {
      return response(dashboardPayload)
    }
    return Promise.reject(new Error(`Unexpected URL: ${url}`))
  })
}

function account(id: string, name: string) {
  return {
    id,
    name,
    broker: 'Broker',
    accountNumberLast4: '1234',
    baseCurrency: 'USD',
    status: 'ACTIVE',
    createdAt: '2026-07-28T08:00:00Z',
    updatedAt: '2026-07-28T08:00:00Z',
  }
}

function position(
  ticker: string,
  costBasis: number,
  marketValue: number | null,
  pnl: number | null,
  percent: number | null,
): PositionPnl {
  return {
    accountId: null,
    ticker,
    quantity: 10,
    averageCost: 10,
    costBasis,
    marketPrice: marketValue === null ? null : marketValue / 10,
    marketValue,
    unrealizedPnl: pnl,
    pnlPercent: percent,
    quoteAsOf: '2026-07-28T09:00:00Z',
    source: 'MOCK',
    mock: true,
    cached: true,
    stale: false,
    available: true,
  }
}

function dashboard(overrides?: {
  positions?: ReturnType<typeof position>[]
  complete?: boolean
  stale?: boolean
  unpriced?: number
  recentActivity?: {
    id: string
    accountId: string
    accountName: string
    ticker: string
    side: 'BUY' | 'SELL'
    quantity: number
    tradePrice: number
    status: 'BOOKED' | 'CANCELLED'
    executedAt: string
    createdAt: string
    cancelledAt: string | null
    cancellationReason: 'CANCELLED' | 'DELETED' | 'AMENDED' | null
  }[]
}) {
  const positions = overrides?.positions ?? []
  const priced = positions.filter((item) => item.available)
  const totalCostBasis = priced.reduce(
    (total, item) => total + item.costBasis,
    0,
  )
  const totalMarketValue = priced.reduce(
    (total, item) => total + (item.marketValue ?? 0),
    0,
  )
  const totalPnl = priced.reduce(
    (total, item) => total + (item.unrealizedPnl ?? 0),
    0,
  )
  return {
    totals: {
      totalCostBasis,
      totalMarketValue,
      totalUnrealizedPnl: totalPnl,
      totalPnlPercent:
        totalCostBasis === 0 ? null : (totalPnl / totalCostBasis) * 100,
      positionCount: positions.length,
      pricedPositionCount: priced.length,
      unpricedPositionCount: overrides?.unpriced ?? 0,
      complete: overrides?.complete ?? true,
      mock: priced.some((item) => item.mock),
      stale: overrides?.stale ?? false,
    },
    positions,
    accountCount: 2,
    activeAccountCount: 2,
    recentActivity: overrides?.recentActivity ?? [],
    quoteStatus: {
      available: priced.length,
      unavailable: overrides?.unpriced ?? 0,
      cached: priced.filter((item) => item.cached).length,
      stale: overrides?.stale ? 1 : 0,
      mock: priced.filter((item) => item.mock).length,
    },
    capturedAt: '2026-07-28T09:00:00Z',
  }
}

function snapshot(
  id: string,
  capturedAt: string,
  marketValue: number,
  pnl: number,
) {
  return {
    id,
    scopeType: 'ALL',
    accountId: null,
    totalCostBasis: 100,
    totalMarketValue: marketValue,
    unrealizedPnl: pnl,
    positionCount: 1,
    pricedPositionCount: 1,
    complete: true,
    mock: true,
    stale: false,
    valuationDate: capturedAt.slice(0, 10),
    capturedAt,
  }
}

function history(items: ReturnType<typeof snapshot>[]) {
  return { range: '30D', items }
}

function response(payload: unknown, ok = true, status = 200) {
  return Promise.resolve({
    ok,
    status,
    json: () => Promise.resolve(payload),
  })
}
