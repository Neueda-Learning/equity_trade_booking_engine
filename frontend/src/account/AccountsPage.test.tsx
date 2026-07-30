import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AccountsPage from './AccountsPage'

const primary = account('primary', 'Primary Account', 'ACTIVE')
const taxable = account('taxable', 'Taxable', 'ACTIVE')

describe('Accounts page', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('creates, deactivates, and reactivates an account', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response([primary]))
      .mockResolvedValueOnce(response(taxable, true, 201))
      .mockResolvedValueOnce(response([primary, taxable]))
      .mockResolvedValueOnce(response({ ...taxable, status: 'INACTIVE' }))
      .mockResolvedValueOnce(
        response([primary, { ...taxable, status: 'INACTIVE' }]),
      )
      .mockResolvedValueOnce(response(taxable))
      .mockResolvedValueOnce(response([primary, taxable]))
    vi.stubGlobal('fetch', fetchMock)

    render(<AccountsPage />)
    await screen.findByText('Primary Account')
    fireEvent.change(screen.getByLabelText('Account name'), {
      target: { value: 'Taxable' },
    })
    fireEvent.change(screen.getByLabelText('Broker'), {
      target: { value: 'IBKR' },
    })
    fireEvent.change(screen.getByLabelText('Account number last 4'), {
      target: { value: '1234' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('Taxable created.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('Taxable')).toBeInTheDocument())
    const taxableCard = screen.getByText('Taxable').closest('article')!
    fireEvent.click(
      Array.from(taxableCard.querySelectorAll('button')).find(
        (button) => button.textContent === 'Deactivate',
      )!,
    )
    expect(await screen.findByText('Taxable deactivated.')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/accounts/taxable/deactivate',
      expect.objectContaining({ method: 'POST' }),
    )
    await waitFor(() =>
      expect(taxableCard).toHaveTextContent('INACTIVE'),
    )
    fireEvent.click(
      Array.from(taxableCard.querySelectorAll('button')).find(
        (button) => button.textContent === 'Activate',
      )!,
    )
    expect(await screen.findByText('Taxable activated.')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/accounts/taxable/activate',
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('hides an account after confirming retained-history deletion', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response([primary, taxable]))
      .mockResolvedValueOnce(response(undefined, true, 204))
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('confirm', vi.fn(() => true))

    render(<AccountsPage />)
    const taxableCard = (await screen.findByText('Taxable')).closest('article')!
    fireEvent.click(
      Array.from(taxableCard.querySelectorAll('button')).find(
        (button) => button.textContent === 'Delete',
      )!,
    )

    expect(await screen.findByText('Taxable deleted.')).toBeInTheDocument()
    expect(screen.queryByText('Taxable')).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/accounts/taxable',
      expect.objectContaining({ method: 'DELETE' }),
    )
    expect(vi.mocked(confirm)).toHaveBeenCalledWith(
      'Delete Taxable from the system? Its trade and valuation history will be retained and restored if you create an account with the same name later.',
    )
  })

  it('shows account field errors from Problem Details', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(response([]))
        .mockResolvedValueOnce(
          response(
            {
              detail: 'One or more fields are invalid.',
              errors: { accountNumberLast4: 'must be exactly 4 digits' },
            },
            false,
            400,
          ),
        ),
    )
    render(<AccountsPage />)
    await screen.findByText('No accounts yet.')
    fireEvent.change(screen.getByLabelText('Account number last 4'), {
      target: { value: '12x4' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }))
    expect(
      await screen.findByText('must be exactly 4 digits'),
    ).toBeInTheDocument()
  })

  it('loads positions for only the selected account', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response([primary, taxable]))
      .mockResolvedValueOnce(
        response([
          {
            accountId: taxable.id,
            ticker: 'AAPL',
            quantity: 6,
            averageCost: 15,
            costBasis: 90,
          },
        ]),
      )
    vi.stubGlobal('fetch', fetchMock)

    render(<AccountsPage />)
    const taxableCard = (await screen.findByText('Taxable')).closest('article')!
    fireEvent.click(
      Array.from(taxableCard.querySelectorAll('button')).find(
        (button) => button.textContent === 'View positions',
      )!,
    )

    expect(await screen.findByText('AAPL')).toBeInTheDocument()
    expect(screen.getByText('$90.00')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/positions?accountId=taxable',
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
  })

  it('shows position empty and error states', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response([primary, taxable]))
      .mockResolvedValueOnce(response([]))
      .mockRejectedValueOnce(new Error('offline'))
    vi.stubGlobal('fetch', fetchMock)

    render(<AccountsPage />)
    await screen.findByText('Taxable')
    const cards = screen.getAllByRole('article')
    const primaryButton = Array.from(cards[0].querySelectorAll('button')).find(
      (button) => button.textContent === 'View positions',
    )!
    fireEvent.click(primaryButton)
    expect(screen.getByText('Loading positions…')).toBeInTheDocument()
    expect(await screen.findByText('No open positions.')).toBeInTheDocument()

    const taxableButton = Array.from(cards[1].querySelectorAll('button')).find(
      (button) => button.textContent === 'View positions',
    )!
    fireEvent.click(taxableButton)
    expect(
      await screen.findByText('Positions are unavailable.'),
    ).toBeInTheDocument()
  })
})

function account(id: string, name: string, status: 'ACTIVE' | 'INACTIVE') {
  return {
    id,
    name,
    broker: 'Broker',
    accountNumberLast4: '1234',
    baseCurrency: 'USD',
    status,
    createdAt: '2026-07-28T06:30:00Z',
    updatedAt: '2026-07-28T06:30:00Z',
  }
}

function response(payload: unknown, ok = true, status = 200) {
  return {
    ok,
    status,
    json: () => Promise.resolve(payload),
  }
}
