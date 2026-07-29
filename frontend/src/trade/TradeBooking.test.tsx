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
  cancellationReason: null,
  supersedesTradeId: null,
}

const aaplSearch = {
  items: [
    {
      ticker: 'AAPL',
      name: 'APPLE INC',
      exchange: 'US',
      type: 'Common Stock',
    },
  ],
}

describe('multi-account activity', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('switches to SELL, selects an account and submits the side', async () => {
    const sellTrade = { ...trade, side: 'SELL' }
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response([primary, retirement]))
      .mockResolvedValueOnce(response(emptyPage))
      .mockResolvedValueOnce(response(aaplSearch))
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
    await fillTrade()
    fireEvent.click(screen.getByRole('button', { name: 'Book SELL trade' }))

    expect(
      await screen.findByText('AAPL SELL trade booked successfully.'),
    ).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('AAPL')).toBeInTheDocument())
    expect(screen.getAllByText('Retirement').length).toBeGreaterThan(0)
    const request = JSON.parse(fetchMock.mock.calls[3][1].body)
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
    expect(
      screen.queryByRole('columnheader', { name: 'Operation time' }),
    ).not.toBeInTheDocument()
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
        .mockResolvedValueOnce(response(aaplSearch))
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
    await fillTrade()
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
        .mockResolvedValueOnce(response(aaplSearch))
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
    await fillTrade()
    fireEvent.click(screen.getByRole('button', { name: 'Book SELL trade' }))
    expect(
      await screen.findByText(
        'insufficient position; available at execution time: 2',
      ),
    ).toBeInTheDocument()
  })

  it('confirms and audit-deletes a BOOKED trade', async () => {
    const deleted = {
      ...trade,
      status: 'CANCELLED',
      cancelledAt: '2026-07-28T06:35:00Z',
      cancellationReason: 'DELETED',
    }
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response([primary, retirement]))
      .mockResolvedValueOnce(
        response({ ...emptyPage, items: [trade], totalElements: 1, totalPages: 1 }),
      )
      .mockResolvedValueOnce(response(deleted))
      .mockResolvedValueOnce(response([primary, retirement]))
      .mockResolvedValueOnce(
        response({ ...emptyPage, items: [deleted], totalElements: 1, totalPages: 1 }),
      )
    const confirm = vi.fn(() => true)
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('confirm', confirm)

    render(<TradeBooking />)
    fireEvent.click(await screen.findByRole('button', { name: 'Delete' }))

    expect(confirm).toHaveBeenCalledWith(expect.stringContaining(
      'retained for audit',
    ))
    expect(
      await screen.findByText(
        'Activity deleted with its audit record preserved.',
      ),
    ).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Delete' }))
        .not.toBeInTheDocument(),
    )
    expect(screen.getByText('DELETED')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      `/api/trades/${trade.id}`,
      expect.objectContaining({ method: 'DELETE' }),
    )
  })

  it('shows a clear 409 position conflict from deletion', async () => {
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
    fireEvent.click(await screen.findByRole('button', { name: 'Delete' }))
    expect(
      await screen.findByText(
        'insufficient position; available at execution time: 0',
      ),
    ).toBeInTheDocument()
  })

  it('edits by creating an audit-linked replacement', async () => {
    const replacement = {
      ...trade,
      id: '7c014ad6-b2b8-43d5-a761-3f32513f42f9',
      quantity: 12,
      supersedesTradeId: trade.id,
    }
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response([primary, retirement]))
      .mockResolvedValueOnce(
        response({ ...emptyPage, items: [trade], totalElements: 1, totalPages: 1 }),
      )
      .mockResolvedValueOnce(response(aaplSearch))
      .mockResolvedValueOnce(response({
        cancelledTrade: {
          ...trade,
          status: 'CANCELLED',
          cancelledAt: '2026-07-28T06:35:00Z',
          cancellationReason: 'AMENDED',
        },
        replacementTrade: replacement,
      }))
      .mockResolvedValueOnce(response([primary, retirement]))
      .mockResolvedValueOnce(response({
        ...emptyPage,
        items: [replacement],
        totalElements: 1,
        totalPages: 1,
      }))
    vi.stubGlobal('fetch', fetchMock)

    render(<TradeBooking />)
    fireEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    expect(
      screen.getByText(/preserves the original as CANCELLED/i),
    ).toBeInTheDocument()
    fireEvent.click(await screen.findByRole('option', { name: /AAPL/ }))
    fireEvent.change(screen.getByLabelText('Quantity'), {
      target: { value: '12' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Save amendment' }))

    expect(
      await screen.findByText(/replacement booked/i),
    ).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      `/api/trades/${trade.id}/amend`,
      expect.objectContaining({ method: 'POST' }),
    )
  })
})

async function fillTrade() {
  fireEvent.change(screen.getByLabelText('Ticker or company'), {
    target: { value: 'aapl' },
  })
  fireEvent.click(await screen.findByRole('option', { name: /AAPL/ }))
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
