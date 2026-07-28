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
}

describe('multi-account activity', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('selects an active account when booking and shows its name', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response([primary, retirement]))
      .mockResolvedValueOnce(response(emptyPage))
      .mockResolvedValueOnce(response(trade, true, 201))
      .mockResolvedValueOnce(response([primary, retirement]))
      .mockResolvedValueOnce(
        response({ ...emptyPage, items: [trade], totalElements: 1, totalPages: 1 }),
      )
    vi.stubGlobal('fetch', fetchMock)

    render(<TradeBooking />)
    await screen.findByText('No trades booked yet.')
    fireEvent.change(screen.getByLabelText('Account'), {
      target: { value: retirement.id },
    })
    fillTrade()
    fireEvent.click(screen.getByRole('button', { name: 'Book BUY trade' }))

    expect(
      await screen.findByText('AAPL BUY trade booked successfully.'),
    ).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('AAPL')).toBeInTheDocument())
    expect(screen.getAllByText('Retirement').length).toBeGreaterThan(0)
    const request = JSON.parse(fetchMock.mock.calls[2][1].body)
    expect(request.accountId).toBe(retirement.id)
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
