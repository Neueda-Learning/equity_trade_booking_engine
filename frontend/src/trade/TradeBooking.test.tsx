import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TradeBooking from './TradeBooking'

const primary = {
  id: '00000000-0000-0000-0000-000000000001',
  name: 'Primary Account',
  broker: 'Legacy',
  accountNumberLast4: null,
  baseCurrency: 'USD',
  status: 'ACTIVE',
  createdAt: '2026-07-28T06:30:00Z',
  updatedAt: '2026-07-28T06:30:00Z',
}

const retirement = {
  ...primary,
  id: '20000000-0000-0000-0000-000000000002',
  name: 'Retirement',
}

const emptyPage = {
  items: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
}

const trade = {
  id: '6c014ad6-b2b8-43d5-a761-3f32513f42f8',
  accountId: retirement.id,
  ticker: 'AAPL',
  side: 'BUY',
  quantity: 10.5,
  tradePrice: 195.25,
  executedAt: '2026-07-28T06:30:00Z',
  status: 'BOOKED',
  createdAt: '2026-07-28T06:30:30Z',
  cancelledAt: null,
}

describe('multi-account activity', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('switches to SELL, selects an account and submits the side', async () => {
    const sellTrade = { ...trade, side: 'SELL' }
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response([primary, retirement]))
      .mockResolvedValueOnce(response(emptyPage))
      .mockResolvedValueOnce(response(sellTrade, true, 201))
      .mockResolvedValueOnce(response([primary, retirement]))
      .mockResolvedValueOnce(
        response({ ...emptyPage, items: [sellTrade], totalElements: 1, totalPages: 1 }),
      )
    vi.stubGlobal('fetch', fetchMock)

    render(<TradeBooking />)
    await screen.findByText('No trades booked yet.')
    fireEvent.change(screen.getByLabelText('Account'), {
      target: { value: retirement.id },
    })
    fireEvent.change(screen.getByLabelText('Side'), {
      target: { value: 'SELL' },
    })
    fillTrade()
    fireEvent.click(screen.getByRole('button', { name: 'Book SELL trade' }))

    expect(
      await screen.findByText('AAPL SELL trade booked successfully.'),
    ).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('AAPL')).toBeInTheDocument())
    expect(screen.getAllByText('Retirement').length).toBeGreaterThan(0)
    const request = JSON.parse(fetchMock.mock.calls[2][1].body)
    expect(request.accountId).toBe(retirement.id)
    expect(request.side).toBe('SELL')
  })

  it('filters activity by account', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response([primary, retirement]))
      .mockResolvedValueOnce(response(emptyPage))
      .mockResolvedValueOnce(
        response({ ...emptyPage, items: [trade], totalElements: 1, totalPages: 1 }),
      )
    vi.stubGlobal('fetch', fetchMock)
    render(<TradeBooking />)
    await screen.findByText('No trades booked yet.')
    fireEvent.change(screen.getByLabelText('Account filter'), {
      target: { value: retirement.id },
    })
    expect(await screen.findByText('AAPL')).toBeInTheDocument()
    expect(fetchMock.mock.calls[2][0]).toContain(
      `accountId=${encodeURIComponent(retirement.id)}`,
    )
  })

  it('prompts for account creation when no account is active', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(
          response([{ ...primary, status: 'INACTIVE' }]),
        )
        .mockResolvedValueOnce(response(emptyPage)),
    )
    render(<TradeBooking />)
    expect(
      await screen.findByText(
        'No active accounts. Create an account before booking a trade.',
      ),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Book BUY trade' }),
    ).not.toBeInTheDocument()
  })

  it('shows trade API field errors', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(response([primary]))
        .mockResolvedValueOnce(response(emptyPage))
        .mockResolvedValueOnce(
          response(
            {
              detail: 'One or more fields are invalid.',
              errors: { ticker: 'must match ticker format' },
            },
            false,
            400,
          ),
        ),
    )
    render(<TradeBooking />)
    await screen.findByText('No trades booked yet.')
    fillTrade()
    fireEvent.click(screen.getByRole('button', { name: 'Book BUY trade' }))
    expect(
      await screen.findByText('must match ticker format'),
    ).toBeInTheDocument()
  })

  it('shows an oversell 409 next to quantity', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(response([primary]))
        .mockResolvedValueOnce(response(emptyPage))
        .mockResolvedValueOnce(
          response(
            {
              detail: 'The request conflicts with the current position.',
              errors: {
                quantity:
                  'insufficient position; available at execution time: 2',
              },
            },
            false,
            409,
          ),
        ),
    )
    render(<TradeBooking />)
    await screen.findByText('No trades booked yet.')
    fireEvent.change(screen.getByLabelText('Side'), {
      target: { value: 'SELL' },
    })
    fillTrade()
    fireEvent.click(screen.getByRole('button', { name: 'Book SELL trade' }))
    expect(
      await screen.findByText(
        'insufficient position; available at execution time: 2',
      ),
    ).toBeInTheDocument()
  })

  it('confirms and cancels a BOOKED trade, then shows cancelledAt', async () => {
    const cancelled = {
      ...trade,
      status: 'CANCELLED',
      cancelledAt: '2026-07-28T06:35:00Z',
    }
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response([primary, retirement]))
      .mockResolvedValueOnce(
        response({ ...emptyPage, items: [trade], totalElements: 1, totalPages: 1 }),
      )
      .mockResolvedValueOnce(response(cancelled))
      .mockResolvedValueOnce(response([primary, retirement]))
      .mockResolvedValueOnce(
        response({ ...emptyPage, items: [cancelled], totalElements: 1, totalPages: 1 }),
      )
    const confirm = vi.fn(() => true)
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('confirm', confirm)

    render(<TradeBooking />)
    fireEvent.click(await screen.findByRole('button', { name: 'Cancel' }))

    expect(confirm).toHaveBeenCalledWith('Cancel this trade?')
    expect(
      await screen.findByText('Trade cancelled successfully.'),
    ).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Cancel' }))
        .not.toBeInTheDocument(),
    )
    expect(screen.getByText('CANCELLED')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      `/api/trades/${trade.id}/cancel`,
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('shows a clear 409 position conflict from cancellation', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(response([primary]))
        .mockResolvedValueOnce(
          response({ ...emptyPage, items: [trade], totalElements: 1, totalPages: 1 }),
        )
        .mockResolvedValueOnce(
          response(
            {
              detail: 'The request conflicts with the current position.',
              errors: {
                quantity:
                  'insufficient position; available at execution time: 0',
              },
            },
            false,
            409,
          ),
        ),
    )
    vi.stubGlobal('confirm', vi.fn(() => true))

    render(<TradeBooking />)
    fireEvent.click(await screen.findByRole('button', { name: 'Cancel' }))
    expect(
      await screen.findByText(
        'insufficient position; available at execution time: 0',
      ),
    ).toBeInTheDocument()
  })
})

function fillTrade() {
  fireEvent.change(screen.getByLabelText('Ticker'), {
    target: { value: 'aapl' },
  })
  fireEvent.change(screen.getByLabelText('Quantity'), {
    target: { value: '10.5' },
  })
  fireEvent.change(screen.getByLabelText('Trade price (USD)'), {
    target: { value: '195.25' },
  })
  fireEvent.change(screen.getByLabelText('Executed at'), {
    target: { value: '2026-07-28T14:30' },
  })
}

function response(payload: unknown, ok = true, status = 200) {
  return {
    ok,
    status,
    json: () => Promise.resolve(payload),
  }
}
