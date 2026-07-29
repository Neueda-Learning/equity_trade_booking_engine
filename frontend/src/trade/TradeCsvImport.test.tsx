import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Account } from '../api'
import TradeCsvImport from './TradeCsvImport'

const primary: Account = {
  id: '00000000-0000-0000-0000-000000000001',
  name: 'Primary Account',
  broker: 'Legacy',
  accountNumberLast4: null,
  baseCurrency: 'USD',
  status: 'ACTIVE',
  createdAt: '2026-07-28T06:30:00Z',
  updatedAt: '2026-07-28T06:30:00Z',
}

describe('trade CSV import', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('previews valid rows and books them oldest first', async () => {
    const onImported = vi.fn()
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response({ id: 'trade-1' }))
      .mockResolvedValueOnce(response({ id: 'trade-2' }))
    vi.stubGlobal('fetch', fetchMock)
    render(
      <TradeCsvImport accounts={[primary]} onImported={onImported} />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Open importer' }))
    uploadCsv([
      'account,ticker,side,quantity,tradePrice,executedAt',
      'Primary Account,MSFT,BUY,5,425.40,2026-07-28T03:00:00Z',
      'Primary Account,AAPL,BUY,10,195.25,2026-07-28T01:30:00Z',
    ].join('\n'))

    expect(
      await screen.findByText('trades.csv · 2 rows ready'),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Import 2 trades' }))

    expect(
      await screen.findByText('Imported 2 trades successfully.'),
    ).toBeInTheDocument()
    expect(onImported).toHaveBeenCalledOnce()
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(requestBody(fetchMock, 0)).toMatchObject({
      accountId: primary.id,
      ticker: 'AAPL',
      side: 'BUY',
    })
    expect(requestBody(fetchMock, 1)).toMatchObject({
      ticker: 'MSFT',
      side: 'BUY',
    })
  })

  it('shows CSV validation errors without sending requests', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    render(
      <TradeCsvImport accounts={[primary]} onImported={vi.fn()} />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Open importer' }))
    uploadCsv([
      'account,ticker,side,quantity,tradePrice,executedAt',
      'Unknown Account,AAPL,HOLD,0,195.25,not-a-time',
    ].join('\n'))

    expect(
      await screen.findByText(
        'Row 2: account must match an active account name or ID.',
      ),
    ).toBeInTheDocument()
    expect(
      screen.getByText('Row 2: side must be BUY or SELL.'),
    ).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
    expect(
      screen.queryByRole('button', { name: /Import \d+ trades/ }),
    ).not.toBeInTheDocument()
  })

  it('continues after a server failure and reports the affected CSV row', async () => {
    const onImported = vi.fn()
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(
          response(
            {
              detail: 'The request conflicts with the current position.',
              errors: { quantity: 'insufficient position' },
            },
            false,
            409,
          ),
        )
        .mockResolvedValueOnce(response({ id: 'trade-2' })),
    )
    render(
      <TradeCsvImport accounts={[primary]} onImported={onImported} />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Open importer' }))
    uploadCsv([
      'account,ticker,side,quantity,tradePrice,executedAt',
      'Primary Account,AAPL,SELL,10,195.25,2026-07-28T01:30:00Z',
      'Primary Account,MSFT,BUY,5,425.40,2026-07-28T03:00:00Z',
    ].join('\n'))
    fireEvent.click(
      await screen.findByRole('button', { name: 'Import 2 trades' }),
    )

    expect(
      await screen.findByText('Imported 1; 1 failed.'),
    ).toBeInTheDocument()
    expect(
      screen.getByText('CSV row 2: insufficient position'),
    ).toBeInTheDocument()
    expect(onImported).toHaveBeenCalledOnce()
  })
})

function uploadCsv(contents: string) {
  const file = new File([contents], 'trades.csv', { type: 'text/csv' })
  Object.defineProperty(file, 'text', {
    value: () => Promise.resolve(contents),
  })
  fireEvent.change(screen.getByLabelText('CSV file'), {
    target: { files: [file] },
  })
}

function requestBody(fetchMock: ReturnType<typeof vi.fn>, index: number) {
  return JSON.parse(fetchMock.mock.calls[index][1].body)
}

function response(payload: unknown, ok = true, status = 200) {
  return {
    ok,
    status,
    json: () => Promise.resolve(payload),
  }
}
