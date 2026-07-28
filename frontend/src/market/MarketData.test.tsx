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
  afterEach(() => vi.unstubAllGlobals())

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
    expect(screen.getByText(/Last successful update/)).toBeInTheDocument()
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

    fireEvent.click(screen.getByRole('button', { name: 'Refresh AAPL' }))
    expect(await screen.findByText('STALE')).toBeInTheDocument()
    expect(screen.queryByText('LIVE')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Restore provider' }))
    expect(await screen.findByText('Provider outage: OFF')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Refresh AAPL' }))
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
      if (options.quoteFailure) {
        return response(options.quoteFailure, false, options.quoteFailure.status)
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
