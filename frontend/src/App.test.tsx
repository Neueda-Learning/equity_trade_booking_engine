import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

describe('system health page', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows Loading while the health request is pending', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => undefined)))

    render(<App />)

    expect(screen.getByText('Loading')).toBeInTheDocument()
  })

  it('shows Connected when the backend reports UP', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ status: 'UP' }),
      }),
    )

    render(<App />)

    expect(await screen.findByText('Connected')).toBeInTheDocument()
    expect(fetch).toHaveBeenCalledWith('/api/health', expect.any(Object))
  })

  it('shows Unavailable when the health request fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))

    render(<App />)

    expect(await screen.findByText('Unavailable')).toBeInTheDocument()
  })
})
