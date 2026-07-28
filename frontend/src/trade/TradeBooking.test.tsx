import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TradeBooking from './TradeBooking'

const emptyPage = {
  items: [],
  page: 0,
  size: 10,
  totalElements: 0,
  totalPages: 0,
}

const bookedTrade = {
  id: '6c014ad6-b2b8-43d5-a761-3f32513f42f8',
  ticker: 'AAPL',
  side: 'BUY',
  quantity: 10.5,
  tradePrice: 195.25,
  executedAt: '2026-07-28T06:30:00Z',
  status: 'BOOKED',
  createdAt: '2026-07-28T06:30:30Z',
}

describe('BUY trade booking', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('submits a BUY trade in UTC and refreshes the ledger', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response(emptyPage))
      .mockResolvedValueOnce(response(bookedTrade, true, 201))
      .mockResolvedValueOnce(
        response({
          ...emptyPage,
          items: [bookedTrade],
          totalElements: 1,
          totalPages: 1,
        }),
      )
    vi.stubGlobal('fetch', fetchMock)

    render(<TradeBooking />)
    expect(await screen.findByText('No trades booked yet.')).toBeInTheDocument()

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
    fireEvent.submit(
      screen.getByRole('button', { name: 'Book BUY trade' }).closest('form')!,
    )

    expect(
      await screen.findByText('AAPL BUY trade booked successfully.'),
    ).toBeInTheDocument()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3))

    const [url, options] = fetchMock.mock.calls[1]
    expect(url).toBe('/api/trades')
    const submitted = JSON.parse(options.body as string)
    expect(submitted).toMatchObject({
      ticker: 'aapl',
      side: 'BUY',
      quantity: 10.5,
      tradePrice: 195.25,
    })
    expect(submitted.executedAt).toMatch(/Z$/)
    expect(screen.getAllByText('AAPL')).toHaveLength(1)
    expect(screen.getByText('BOOKED')).toBeInTheDocument()
  })

  it('shows backend field errors without adding a trade', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response(emptyPage))
      .mockResolvedValueOnce(
        response(
          {
            message: 'Trade validation failed',
            fieldErrors: [
              {
                field: 'executedAt',
                message: 'must not be more than 60 seconds in the future',
              },
            ],
          },
          false,
          400,
        ),
      )
    vi.stubGlobal('fetch', fetchMock)

    render(<TradeBooking />)
    await screen.findByText('No trades booked yet.')
    fillRequiredFields()
    fireEvent.submit(
      screen.getByRole('button', { name: 'Book BUY trade' }).closest('form')!,
    )

    expect(
      await screen.findByText(
        'executedAt: must not be more than 60 seconds in the future',
      ),
    ).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('loads the next page of the booking ledger', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        response({
          items: [{ ...bookedTrade, ticker: 'MSFT' }],
          page: 0,
          size: 10,
          totalElements: 11,
          totalPages: 2,
        }),
      )
      .mockResolvedValueOnce(
        response({
          items: [bookedTrade],
          page: 1,
          size: 10,
          totalElements: 11,
          totalPages: 2,
        }),
      )
    vi.stubGlobal('fetch', fetchMock)

    render(<TradeBooking />)
    expect(await screen.findByText('MSFT')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Next' }))

    expect(await screen.findByText('AAPL')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/trades?page=1&size=10',
      expect.any(Object),
    )
  })
})

function response(payload: unknown, ok = true, status = 200) {
  return {
    ok,
    status,
    json: () => Promise.resolve(payload),
  }
}

function fillRequiredFields() {
  fireEvent.change(screen.getByLabelText('Ticker'), {
    target: { value: 'AAPL' },
  })
  fireEvent.change(screen.getByLabelText('Quantity'), {
    target: { value: '1' },
  })
  fireEvent.change(screen.getByLabelText('Trade price (USD)'), {
    target: { value: '10' },
  })
  fireEvent.change(screen.getByLabelText('Executed at'), {
    target: { value: '2026-07-28T14:30' },
  })
}
