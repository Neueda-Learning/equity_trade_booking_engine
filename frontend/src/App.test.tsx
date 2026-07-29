import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { I18nProvider } from './i18n'

describe('application navigation', () => {
  beforeEach(() => window.localStorage.clear())
  afterEach(() => {
    vi.unstubAllGlobals()
    window.localStorage.clear()
    document.documentElement.lang = ''
  })

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

  it('switches language immediately and persists the selection', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    render(
      <I18nProvider>
        <App />
      </I18nProvider>,
    )

    fireEvent.change(screen.getByRole('combobox', { name: 'Language' }), {
      target: { value: 'zh-CN' },
    })

    expect(
      screen.getByRole('heading', { name: '仪表盘' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: '语言' })).toHaveValue('zh-CN')
    expect(document.documentElement.lang).toBe('zh-CN')
    expect(window.localStorage.getItem('equity-console-language')).toBe('zh-CN')

    fireEvent.change(screen.getByRole('combobox', { name: '语言' }), {
      target: { value: 'pt-BR' },
    })

    expect(screen.getByRole('heading', { name: 'Painel' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Idioma' })).toHaveValue('pt-BR')
    expect(document.documentElement.lang).toBe('pt-BR')
    expect(window.localStorage.getItem('equity-console-language')).toBe('pt-BR')
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
