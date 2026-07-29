import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

describe('application navigation', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('navigates between Dashboard, Accounts, Activity and Market Data', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) => {
        const url = String(input)
        if (url === '/api/health') return response({ status: 'UP' })
        if (url === '/api/accounts') return response([])
        if (url === '/api/market-data/provider/status') {
          return response({
            provider: 'MOCK',
            configured: true,
            demoControlsEnabled: false,
            demoOutageEnabled: false,
            lastSuccessAt: null,
            lastFailureAt: null,
            lastFailureCategory: null,
          })
        }
        if (url.startsWith('/api/dashboard/history')) {
          return response({ range: '30D', items: [] })
        }
        if (url === '/api/dashboard') {
          return response({
            totals: {
              totalCostBasis: 0,
              totalMarketValue: 0,
              totalUnrealizedPnl: 0,
              totalPnlPercent: null,
              positionCount: 0,
              pricedPositionCount: 0,
              unpricedPositionCount: 0,
              complete: true,
              mock: false,
              stale: false,
            },
            positions: [],
            accountCount: 0,
            activeAccountCount: 0,
            recentActivity: [],
            quoteStatus: {
              available: 0,
              unavailable: 0,
              cached: 0,
              stale: 0,
              mock: 0,
            },
            capturedAt: '2026-07-28T09:00:00Z',
          })
        }
        return response({
          items: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
        })
      }),
    )

    render(<App />)
    expect(await screen.findByText('Connected')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Accounts' }))
    expect(
      await screen.findByRole('heading', { name: 'Create account' }),
    ).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Activity' }))
    expect(
      await screen.findByRole('heading', { name: 'Book a trade' }),
    ).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Market Data' }))
    expect(
      screen.getByRole('heading', {
        name: 'Market Data',
      }),
    ).toBeInTheDocument()
  })

  it('shows Unavailable when the health request fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    render(<App />)
    expect(await screen.findByText('Unavailable')).toBeInTheDocument()
  })

  it('opens and closes the mobile sidebar with keyboard support', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) => {
        const url = String(input)
        if (url === '/api/health') return response({ status: 'UP' })
        if (url === '/api/accounts') return response([])
        if (url.startsWith('/api/dashboard/history')) {
          return response({ range: '30D', items: [] })
        }
        if (url === '/api/dashboard') {
          return response({
            totals: {
              totalCostBasis: 0,
              totalMarketValue: 0,
              totalUnrealizedPnl: 0,
              totalPnlPercent: null,
              positionCount: 0,
              pricedPositionCount: 0,
              unpricedPositionCount: 0,
              complete: true,
              mock: false,
              stale: false,
            },
            positions: [],
            accountCount: 0,
            activeAccountCount: 0,
            recentActivity: [],
            quoteStatus: {
              available: 0,
              unavailable: 0,
              cached: 0,
              stale: 0,
              mock: 0,
            },
            capturedAt: '2026-07-28T09:00:00Z',
          })
        }
        return response({ items: [] })
      }),
    )
    render(<App />)
    const menuButton = screen.getByRole('button', {
      name: 'Open navigation',
    })

    fireEvent.click(menuButton)
    expect(menuButton).toHaveAttribute('aria-expanded', 'true')
    expect(
      screen.getAllByRole('button', { name: 'Close navigation' }),
    ).toHaveLength(2)

    fireEvent.keyDown(document, { key: 'Escape' })
    await waitFor(() =>
      expect(menuButton).toHaveAttribute('aria-expanded', 'false'),
    )
    expect(menuButton).toHaveFocus()
  })
})

function response(payload: unknown, ok = true, status = 200) {
  return Promise.resolve({
    ok,
    status,
    json: () => Promise.resolve(payload),
  })
}
