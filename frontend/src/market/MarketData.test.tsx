import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import MarketData from './MarketData'

const accounts = [
  {
    id: 'primary',
    name: 'Primary Account',
    broker: 'Broker',
    accountNumberLast4: '1234',
    baseCurrency: 'USD',
    status: 'ACTIVE',
    createdAt: '2026-07-28T08:00:00Z',
    updatedAt: '2026-07-28T08:00:00Z',
  },
]

describe('Market Data', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    window.localStorage.clear()
  })

  it('shows the MOCK provider without calling it live', async () => {
    vi.stubGlobal(
      'fetch',
      routedFetch({
        status: providerStatus('MOCK'),
        quotes: [quote('AAPL', { source: 'MOCK', mock: true })],
      }),
    )

    render(<MarketData />)

    expect(await screen.findByText('AAPL')).toBeInTheDocument()
    expect(screen.getAllByText('MOCK').length).toBeGreaterThan(0)
    expect(screen.queryByText('LIVE')).not.toBeInTheDocument()
    expect(screen.getByText(/not live market data/)).toBeInTheDocument()
  })

  it('shows FINNHUB live, cached and stale states distinctly', async () => {
    vi.stubGlobal(
      'fetch',
      routedFetch({
        status: providerStatus('FINNHUB'),
        quotes: [
          quote('AAPL'),
          quote('MSFT', { cached: true }),
          quote('NVDA', { cached: true, stale: true }),
        ],
      }),
    )

    render(<MarketData />)

    expect(await screen.findByText('AAPL')).toBeInTheDocument()
    expect(screen.getAllByText('FINNHUB').length).toBeGreaterThan(0)
    expect(screen.getByText('LIVE')).toBeInTheDocument()
    expect(screen.getAllByText('CACHED')).toHaveLength(2)
    expect(screen.getByText('STALE')).toBeInTheDocument()
    expect(
      screen.getAllByText(/Last successful update/).length,
    ).toBeGreaterThan(0)
  })

  it('filters by account and keeps partial successful quote rows', async () => {
    const fetchMock = routedFetch({
      status: providerStatus('FINNHUB'),
      quotes: [
        quote('AAPL'),
        quote('MSFT', { cached: true, stale: true }),
      ],
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<MarketData />)
    expect(await screen.findByText('AAPL')).toBeInTheDocument()
    expect(screen.getByText('MSFT')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Account'), {
      target: { value: 'primary' },
    })
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/market-data/quotes?accountId=primary',
        expect.objectContaining({ signal: expect.any(AbortSignal) }),
      ),
    )
  })

  it('refreshes every displayed position quote', async () => {
    const fetchMock = routedFetch({
      status: providerStatus('FINNHUB'),
      quotes: [quote('AAPL'), quote('MSFT', { cached: true })],
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<MarketData />)
    await screen.findByText('AAPL')
    fireEvent.click(
      screen.getByRole('button', { name: 'Refresh all quotes' }),
    )

    expect(
      await screen.findByText('Refreshed 2 quotes.'),
    ).toBeInTheDocument()
    const refreshedUrls = fetchMock.mock.calls
      .filter(([, init]) => init?.method === 'POST')
      .map(([url]) => String(url))
    expect(refreshedUrls).toEqual([
      '/api/market-data/quotes/AAPL/refresh',
      '/api/market-data/quotes/MSFT/refresh',
    ])
  })

  it('keeps the table visible when one quote fails to refresh', async () => {
    vi.stubGlobal(
      'fetch',
      routedFetch({
        status: providerStatus('FINNHUB'),
        quotes: [quote('AAPL'), quote('MSFT', { cached: true })],
        refreshFailures: ['MSFT'],
      }),
    )

    render(<MarketData />)
    await screen.findByText('AAPL')
    fireEvent.click(
      screen.getByRole('button', { name: 'Refresh all quotes' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Refreshed 1 quotes; 1 failed and kept their previous values.',
    )
    expect(screen.getByText('AAPL')).toBeInTheDocument()
    expect(screen.getByText('MSFT')).toBeInTheDocument()
  })

  it('searches for a ticker and refreshes a live quote', async () => {
    const fetchMock = routedFetch({
      status: providerStatus('FINNHUB'),
      quotes: [],
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<MarketData />)
    await screen.findByText('No open positions to quote.')

    fireEvent.change(screen.getByLabelText('Ticker search'), {
      target: { value: 'msft' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Search' }))
    expect(await screen.findByText('MSFT')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Refresh MSFT' }))
    expect(
      await screen.findByText('MSFT FINNHUB quote refreshed.'),
    ).toBeInTheDocument()
  })

  it('keeps all searched tickers across Market Data remounts', async () => {
    const fetchMock = routedFetch({
      status: providerStatus('FINNHUB'),
      quotes: [],
    })
    vi.stubGlobal('fetch', fetchMock)
    const firstRender = render(<MarketData />)
    await screen.findByText('No open positions to quote.')

    fireEvent.change(screen.getByLabelText('Ticker search'), {
      target: { value: 'msft' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Search' }))
    expect(await screen.findByText('MSFT')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Ticker search'), {
      target: { value: 'nvda' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Search' }))
    expect(await screen.findByText('NVDA')).toBeInTheDocument()
    expect(screen.getByText('MSFT')).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: 'Searched quotes' }),
    ).toBeInTheDocument()

    firstRender.unmount()
    render(<MarketData />)
    expect(await screen.findByText('MSFT')).toBeInTheDocument()
    expect(await screen.findByText('NVDA')).toBeInTheDocument()
  })

  it('keeps existing tables when one searched ticker is unavailable', async () => {
    vi.stubGlobal(
      'fetch',
      routedFetch({
        status: providerStatus('FINNHUB'),
        quotes: [quote('AAPL')],
        unavailableTickers: ['ZZZZ'],
      }),
    )
    render(<MarketData />)
    expect(await screen.findByText('AAPL')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Ticker search'), {
      target: { value: 'zzzz' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Search' }))

    expect(await screen.findByText('ZZZZ')).toBeInTheDocument()
    expect(
      screen.getByText(
        'Live market data timed out and no cached quote is available.',
      ),
    ).toBeInTheDocument()
    expect(screen.getByText('AAPL')).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: 'Position quotes' }),
    ).toBeInTheDocument()
  })

  it('removes an unavailable searched ticker and its saved history', async () => {
    const fetchMock = routedFetch({
      status: providerStatus('FINNHUB'),
      quotes: [],
      unavailableTickers: ['ZZZZ'],
    })
    vi.stubGlobal('fetch', fetchMock)
    const firstRender = render(<MarketData />)
    await screen.findByText('No open positions to quote.')

    fireEvent.change(screen.getByLabelText('Ticker search'), {
      target: { value: 'zzzz' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Search' }))
    expect(await screen.findByText('ZZZZ')).toBeInTheDocument()
    expect(
      window.localStorage.getItem('equity-market-searched-tickers'),
    ).toBe('["ZZZZ"]')

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Remove ZZZZ from searched quotes',
      }),
    )
    expect(screen.queryByText('ZZZZ')).not.toBeInTheDocument()
    expect(
      window.localStorage.getItem('equity-market-searched-tickers'),
    ).toBeNull()

    firstRender.unmount()
    render(<MarketData />)
    await screen.findByText('No open positions to quote.')
    expect(
      fetchMock.mock.calls.filter(
        ([url]) => String(url) === '/api/market-data/quotes/ZZZZ',
      ),
    ).toHaveLength(1)
  })

  it('enables demo outage, shows stale data, then restores live provider', async () => {
    let outage = false
    const fetchMock = routedFetch({
      status: providerStatus('FINNHUB', true),
      quotes: [quote('AAPL')],
      demo: () => outage,
      setDemo: (enabled) => {
        outage = enabled
      },
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<MarketData />)

    expect(await screen.findByText('Provider outage: OFF')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Simulate outage' }))
    expect(
      await screen.findByText('Provider outage: SIMULATED'),
    ).toBeInTheDocument()
    expect(await screen.findByText('STALE')).toBeInTheDocument()
    expect(screen.getByText('CACHED')).toBeInTheDocument()
    expect(screen.queryByText('LIVE')).not.toBeInTheDocument()
    expect(
      screen.getByText(
        'Demo outage enabled. Visible quotes now use the Redis fallback and are marked STALE.',
      ),
    ).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Restore provider' }))
    expect(await screen.findByText('Provider outage: OFF')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('LIVE')).toBeInTheDocument())
  })

  it('shows a readable 503 when no cached quote exists', async () => {
    vi.stubGlobal(
      'fetch',
      routedFetch({
        status: providerStatus('FINNHUB'),
        quoteFailure: {
          status: 503,
          detail:
            'The market data provider timed out and no cached quote is available.',
          errors: { provider: 'provider timeout' },
        },
      }),
    )

    render(<MarketData />)
    expect(
      await screen.findByText(
        'Live market data timed out and no cached quote is available.',
      ),
    ).toBeInTheDocument()
  })

  it('shows a general API error', async () => {
    vi.stubGlobal(
      'fetch',
      routedFetch({
        status: providerStatus('FINNHUB'),
        rejectQuotes: true,
      }),
    )

    render(<MarketData />)
    expect(
      await screen.findByText('Market data could not be loaded.'),
    ).toBeInTheDocument()
  })
})

function routedFetch(options: {
  status: ReturnType<typeof providerStatus>
  quotes?: ReturnType<typeof quote>[]
  quoteFailure?: {
    status: number
    detail: string
    errors: Record<string, string>
  }
  rejectQuotes?: boolean
  refreshFailures?: string[]
  unavailableTickers?: string[]
  demo?: () => boolean
  setDemo?: (enabled: boolean) => void
}) {
  return vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (url === '/api/accounts') return response(accounts)
    if (url === '/api/market-data/provider/status') {
      return response({
        ...options.status,
        demoOutageEnabled: options.demo?.() ?? false,
      })
    }
    if (url === '/api/demo/market-data/outage') {
      return response(demoResponse(options.demo?.() ?? false))
    }
    if (url.endsWith('/outage/enable')) {
      options.setDemo?.(true)
      return response(demoResponse(true))
    }
    if (url.endsWith('/outage/disable')) {
      options.setDemo?.(false)
      return response(demoResponse(false))
    }
    if (url.startsWith('/api/market-data/quotes/')) {
      const ticker = decodeURIComponent(
        url.split('/').at(-1) === 'refresh'
          ? url.split('/').at(-2) ?? 'AAPL'
          : url.split('/').at(-1) ?? 'AAPL',
      ).toUpperCase()
      if (
        url.endsWith('/refresh')
        && options.refreshFailures?.includes(ticker)
      ) {
        return response(
          {
            status: 503,
            detail: 'The market data provider is unavailable.',
            errors: { provider: 'provider unavailable' },
          },
          false,
          503,
        )
      }
      if (options.quoteFailure) {
        return response(options.quoteFailure, false, options.quoteFailure.status)
      }
      if (options.unavailableTickers?.includes(ticker)) {
        return response(
          {
            status: 503,
            detail:
              'The market data provider timed out and no cached quote is available.',
            errors: { provider: 'provider timeout' },
          },
          false,
          503,
        )
      }
      return response(
        quote(ticker, {
          cached: options.demo?.() ?? false,
          stale: options.demo?.() ?? false,
        }),
      )
    }
    if (url.startsWith('/api/market-data/quotes')) {
      if (options.rejectQuotes) return Promise.reject(new Error('offline'))
      if (options.quoteFailure) {
        return response(options.quoteFailure, false, options.quoteFailure.status)
      }
      return response({ items: options.quotes ?? [] })
    }
    return Promise.reject(
      new Error(`Unexpected ${init?.method ?? 'GET'} ${url}`),
    )
  })
}

function providerStatus(
  provider: 'MOCK' | 'FINNHUB',
  demoControlsEnabled = false,
) {
  return {
    provider,
    configured: true,
    demoControlsEnabled,
    demoOutageEnabled: false,
    lastSuccessAt:
      provider === 'FINNHUB' ? '2026-07-28T08:30:45Z' : null,
    lastFailureAt: null,
    lastFailureCategory: null,
  }
}

function demoResponse(enabled: boolean) {
  return {
    enabled,
    demoOnly: true,
    message: enabled
      ? 'DEMO outage enabled; external provider calls will fail.'
      : 'DEMO outage disabled; external provider calls are available.',
  }
}

function quote(
  ticker: string,
  state: {
    source?: 'MOCK' | 'FINNHUB'
    mock?: boolean
    cached?: boolean
    stale?: boolean
  } = {},
) {
  return {
    ticker,
    price: 195.25,
    previousClose: 193.8,
    change: 1.45,
    changePercent: 0.748194,
    marketTimestamp: '2026-07-28T08:30:00Z',
    fetchedAt: '2026-07-28T08:30:45Z',
    source: state.source ?? 'FINNHUB',
    mock: state.mock ?? false,
    cached: state.cached ?? false,
    stale: state.stale ?? false,
  }
}

function response(payload: unknown, ok = true, status = 200) {
  return Promise.resolve({
    ok,
    status,
    json: () => Promise.resolve(payload),
  })
}
