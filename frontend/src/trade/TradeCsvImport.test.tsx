import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Account } from '../api'
import { I18nProvider } from '../i18n'
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
      .mockResolvedValueOnce(response(registration()))
      .mockResolvedValueOnce(response({ id: 'trade-1' }))
      .mockResolvedValueOnce(response({ id: 'trade-2' }))
      .mockResolvedValueOnce(response(registration('COMPLETED')))
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
    expect(fetchMock).toHaveBeenCalledTimes(4)
    expect(fetchMock.mock.calls[0][0]).toBe(
      '/api/trade-imports/registrations',
    )
    expect(requestBody(fetchMock, 1)).toMatchObject({
      accountId: primary.id,
      ticker: 'AAPL',
      side: 'BUY',
    })
    expect(requestBody(fetchMock, 2)).toMatchObject({
      ticker: 'MSFT',
      side: 'BUY',
    })
    expect(fetchMock.mock.calls[3][0]).toContain('/result')
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
        .mockResolvedValueOnce(response(registration()))
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
        .mockResolvedValueOnce(response({ id: 'trade-2' }))
        .mockResolvedValueOnce(response(registration('PARTIAL'))),
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

  it('does not submit trades when a duplicate import is cancelled', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(response(
      {
        type: 'urn:equity-trade:problem:conflict',
        title: 'CSV table already imported',
        status: 409,
        detail: 'This CSV table was imported previously.',
        errors: { contentHash: 'has already been imported' },
        duplicateImport: registration('COMPLETED'),
      },
      false,
      409,
    ))
    vi.stubGlobal('fetch', fetchMock)
    render(
      <TradeCsvImport accounts={[primary]} onImported={vi.fn()} />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Open importer' }))
    uploadCsv(validCsv())
    fireEvent.click(
      await screen.findByRole('button', { name: 'Import 1 trades' }),
    )

    expect(
      await screen.findByRole('alertdialog', {
        name: 'CSV table already imported',
      }),
    ).toHaveTextContent('This will create another set of trades.')
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('imports the complete table again after duplicate confirmation', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response(
        {
          status: 409,
          detail: 'This CSV table was imported previously.',
          errors: { contentHash: 'has already been imported' },
          duplicateImport: registration('COMPLETED'),
        },
        false,
        409,
      ))
      .mockResolvedValueOnce(response({
        ...registration(),
        importCount: 2,
      }))
      .mockResolvedValueOnce(response({ id: 'trade-repeat' }))
      .mockResolvedValueOnce(response({
        ...registration('COMPLETED'),
        importCount: 2,
      }))
    vi.stubGlobal('fetch', fetchMock)
    render(
      <TradeCsvImport accounts={[primary]} onImported={vi.fn()} />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Open importer' }))
    uploadCsv(validCsv())
    fireEvent.click(
      await screen.findByRole('button', { name: 'Import 1 trades' }),
    )
    fireEvent.click(
      await screen.findByRole('button', { name: 'Import again' }),
    )

    expect(
      await screen.findByText('Imported 1 trades successfully.'),
    ).toBeInTheDocument()
    expect(requestBody(fetchMock, 1)).toMatchObject({
      repeatConfirmed: true,
      rowCount: 1,
    })
    expect(fetchMock.mock.calls[2][0]).toBe('/api/trades')
  })

  it.each([
    ['zh-CN', '选择文件', '未选择文件'],
    ['pt-BR', 'Escolher arquivo', 'Nenhum arquivo selecionado'],
  ])('localizes the custom file control in %s', (
    language,
    chooseFile,
    noFile,
  ) => {
    window.localStorage.setItem('equity-console-language', language)
    render(
      <I18nProvider>
        <TradeCsvImport accounts={[primary]} onImported={vi.fn()} />
      </I18nProvider>,
    )
    fireEvent.click(screen.getByRole('button', {
      name: language === 'zh-CN' ? '打开导入工具' : 'Abrir importador',
    }))
    expect(screen.getByRole('button', { name: chooseFile }))
      .toBeInTheDocument()
    expect(screen.getByText(noFile)).toBeInTheDocument()
    window.localStorage.removeItem('equity-console-language')
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

function registration(
  status: 'IN_PROGRESS' | 'COMPLETED' | 'PARTIAL' | 'FAILED' =
    'IN_PROGRESS',
) {
  return {
    importId: '123e4567-e89b-82d3-a456-426614174000',
    firstFileName: 'trades.csv',
    rowCount: 1,
    firstImportedAt: '2026-07-28T08:00:00Z',
    lastImportedAt: '2026-07-28T08:00:00Z',
    importCount: 1,
    status,
    lastSuccessCount: status === 'COMPLETED' ? 1 : 0,
    lastFailureCount: 0,
  }
}

function validCsv() {
  return [
    'account,ticker,side,quantity,tradePrice,executedAt',
    'Primary Account,AAPL,BUY,10,195.25,2026-07-28T01:30:00Z',
  ].join('\n')
}
