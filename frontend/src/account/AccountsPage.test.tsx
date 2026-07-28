import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AccountsPage from './AccountsPage'

const primary = account('primary', 'Primary Account', 'ACTIVE')
const taxable = account('taxable', 'Taxable', 'ACTIVE')

describe('Accounts page', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('creates and deactivates an account', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response([primary]))
      .mockResolvedValueOnce(response(taxable, true, 201))
      .mockResolvedValueOnce(response([primary, taxable]))
      .mockResolvedValueOnce(response({ ...taxable, status: 'INACTIVE' }))
      .mockResolvedValueOnce(
        response([primary, { ...taxable, status: 'INACTIVE' }]),
      )
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
      taxableCard.querySelector<HTMLButtonElement>('button:last-child')!,
    )
    expect(await screen.findByText('Taxable deactivated.')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/accounts/taxable/deactivate',
      expect.objectContaining({ method: 'POST' }),
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
