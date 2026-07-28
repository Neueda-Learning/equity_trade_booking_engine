import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

describe('application navigation', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('navigates between Dashboard, Accounts, Activity and Market Data', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) => {
        const url = String(input)
        if (url === '/api/health') return response({ status: 'UP' })
        if (url === '/api/accounts') return response([])
        if (url === '/api/positions') return response([])
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
})

function response(payload: unknown, ok = true, status = 200) {
  return Promise.resolve({
    ok,
    status,
    json: () => Promise.resolve(payload),
  })
}
