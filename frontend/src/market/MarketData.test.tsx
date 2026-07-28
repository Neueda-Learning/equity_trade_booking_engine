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

const aapl = quote('AAPL', { cached: true, stale: true })

describe('Market Data', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows mock, cached and stale labels and filters by account', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response(accounts))
      .mockResolvedValueOnce(response({ items: [aapl] }))
      .mockResolvedValueOnce(response({ items: [aapl] }))
    vi.stubGlobal('fetch', fetchMock)

    render(<MarketData />)
    expect(await screen.findByText('AAPL')).toBeInTheDocument()
    expect(screen.getAllByText('MOCK').length).toBeGreaterThan(0)
    expect(screen.getByText('CACHED')).toBeInTheDocument()
    expect(screen.getByText('STALE')).toBeInTheDocument()

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

  it('searches for a ticker and refreshes the result', async () => {
    const fresh = quote('MSFT', { cached: false, stale: false })
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response(accounts))
      .mockResolvedValueOnce(response({ items: [] }))
      .mockResolvedValueOnce(response(fresh))
      .mockResolvedValueOnce(response({ ...fresh, price: 426 }))
    vi.stubGlobal('fetch', fetchMock)

    render(<MarketData />)
    await screen.findByText('No open positions to quote.')
    fireEvent.change(screen.getByLabelText('Ticker search'), {
      target: { value: 'msft' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Search' }))

    expect(await screen.findByText('MSFT')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/market-data/quotes/msft',
      expect.objectContaining({ signal: undefined }),
    )
    fireEvent.click(screen.getByRole('button', { name: 'Refresh MSFT' }))
    expect(
      await screen.findByText('MSFT mock quote refreshed.'),
    ).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/market-data/quotes/MSFT/refresh',
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('shows the empty state', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(response([]))
        .mockResolvedValueOnce(response({ items: [] })),
    )

    render(<MarketData />)
    expect(
      await screen.findByText('No open positions to quote.'),
    ).toBeInTheDocument()
  })

  it('shows a general API error', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(response(accounts))
        .mockRejectedValueOnce(new Error('offline')),
    )

    render(<MarketData />)
    expect(
      await screen.findByText('Market data could not be loaded.'),
    ).toBeInTheDocument()
  })

  it('shows a distinct 503 unavailable state', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(response(accounts))
        .mockResolvedValueOnce(
          response(
            {
              status: 503,
              detail: 'No market quote is currently available.',
            },
            false,
            503,
          ),
        ),
    )

    render(<MarketData />)
    expect(
      await screen.findByText('Market data is currently unavailable.'),
    ).toBeInTheDocument()
  })
})

function quote(
  ticker: string,
  state: { cached: boolean; stale: boolean },
) {
  return {
    ticker,
    price: 195.25,
    previousClose: 193.8,
    change: 1.45,
    changePercent: 0.748194,
    marketTimestamp: '2026-07-28T08:30:00Z',
    fetchedAt: '2026-07-28T08:30:45Z',
    source: 'MOCK',
    mock: true,
    ...state,
  }
}

function response(payload: unknown, ok = true, status = 200) {
  return {
    ok,
    status,
    json: () => Promise.resolve(payload),
  }
}
