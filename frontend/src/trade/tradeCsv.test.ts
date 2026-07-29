import { describe, expect, it } from 'vitest'
import type { Account } from '../api'
import {
  MAX_TRADE_CSV_ROWS,
  parseTradeCsv,
  tradeCsvContentHash,
} from './tradeCsv'
import sample from './samples/trade-import-demo.csv?raw'

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

const growth: Account = {
  ...primary,
  id: '00000000-0000-0000-0000-000000000002',
  name: 'Growth, Inc.',
}

describe('trade CSV parsing', () => {
  it('maps account names and IDs, normalizes values and sorts oldest first', () => {
    const result = parseTradeCsv(
      [
        '\uFEFFaccount,ticker,side,quantity,tradePrice,executedAt',
        `${primary.id},msft,buy,5,425.40,2026-07-28T03:00:00Z`,
        '"Growth, Inc.",aapl,sell,2.5,198.10,2026-07-28T02:00:00Z',
      ].join('\r\n'),
      [primary, growth],
      new Date('2026-07-29T00:00:00Z'),
    )

    expect(result.issues).toEqual([])
    expect(result.rows).toHaveLength(2)
    expect(result.rows.map((row) => row.input.ticker)).toEqual([
      'AAPL',
      'MSFT',
    ])
    expect(result.rows[0]).toMatchObject({
      rowNumber: 3,
      accountName: 'Growth, Inc.',
      input: {
        accountId: growth.id,
        side: 'SELL',
        quantity: 2.5,
        tradePrice: 198.1,
        executedAt: '2026-07-28T02:00:00Z',
      },
    })
  })

  it('returns all actionable row validation issues without preparing rows', () => {
    const result = parseTradeCsv(
      [
        'account,ticker,side,quantity,tradePrice,executedAt',
        'Missing Account,too-long-ticker,HOLD,-1,0,2026-07-30T09:30:00',
      ].join('\n'),
      [primary],
      new Date('2026-07-29T00:00:00Z'),
    )

    expect(result.rows).toEqual([])
    expect(result.issues.map((issue) => issue.code)).toEqual([
      'account',
      'ticker',
      'side',
      'quantity',
      'tradePrice',
      'executedAt',
    ])
    expect(result.issues.every((issue) => issue.rowNumber === 2)).toBe(true)
  })

  it('requires the documented headers and enforces the row limit', () => {
    const missing = parseTradeCsv(
      'account,ticker,side\nPrimary Account,AAPL,BUY',
      [primary],
    )
    expect(missing.issues).toEqual([
      {
        code: 'missingHeaders',
        value: 'quantity, tradePrice, executedAt',
      },
    ])

    const rows = Array.from(
      { length: MAX_TRADE_CSV_ROWS + 1 },
      () => 'Primary Account,AAPL,BUY,1,100,2026-07-28T01:00:00Z',
    )
    const tooMany = parseTradeCsv(
      [
        'account,ticker,side,quantity,tradePrice,executedAt',
        ...rows,
      ].join('\n'),
      [primary],
    )
    expect(tooMany.issues[0]).toEqual({
      code: 'tooManyRows',
      value: String(MAX_TRADE_CSV_ROWS),
    })
  })

  it('rejects malformed quoted CSV data', () => {
    const result = parseTradeCsv(
      [
        'account,ticker,side,quantity,tradePrice,executedAt',
        '"Primary Account,AAPL,BUY,1,100,2026-07-28T01:00:00Z',
      ].join('\n'),
      [primary],
    )
    expect(result.issues).toEqual([{ code: 'malformedCsv' }])
  })

  it('rejects calendar dates that JavaScript would otherwise normalize', () => {
    const result = parseTradeCsv(
      [
        'account,ticker,side,quantity,tradePrice,executedAt',
        'Primary Account,AAPL,BUY,1,100,2026-02-30T01:00:00Z',
      ].join('\n'),
      [primary],
      new Date('2026-07-29T00:00:00Z'),
    )
    expect(result.issues).toEqual([
      { code: 'executedAt', rowNumber: 2 },
    ])
  })

  it('keeps the downloadable demo file valid and import-ready', () => {
    const result = parseTradeCsv(
      sample,
      [primary],
      new Date('2026-07-29T00:00:00Z'),
    )

    expect(result.issues).toEqual([])
    expect(result.rows).toHaveLength(5)
    expect(result.rows.map((row) => row.input.side)).toEqual([
      'BUY',
      'SELL',
      'BUY',
      'BUY',
      'BUY',
    ])
  })

  it('creates the same identity for semantically equivalent tables', async () => {
    const first = parseTradeCsv(
      [
        'account,ticker,side,quantity,tradePrice,executedAt',
        'Primary Account,AAPL,BUY,10.0,195.2500,2026-07-28T01:30:00Z',
        `${growth.id},MSFT,SELL,2,210,2026-07-28T03:00:00+00:00`,
      ].join('\n'),
      [primary, growth],
      new Date('2026-07-29T00:00:00Z'),
    )
    const reordered = parseTradeCsv(
      [
        'TICKER,executedAt,quantity,ACCOUNT,tradePrice,SIDE',
        'msft,2026-07-28T03:00:00Z,2.000,"Growth, Inc.",210.0,sell',
        'aapl,2026-07-28T01:30:00+00:00,10,Primary Account,195.25,buy',
      ].join('\n'),
      [primary, growth],
      new Date('2026-07-29T00:00:00Z'),
    )

    expect(reordered.issues).toEqual([])
    expect(await tradeCsvContentHash(reordered.rows))
      .toBe(await tradeCsvContentHash(first.rows))
  })

  it('changes the table identity when a trade value changes', async () => {
    const first = parseTradeCsv(validIdentityCsv('10'), [primary])
    const changed = parseTradeCsv(validIdentityCsv('11'), [primary])

    expect(await tradeCsvContentHash(changed.rows))
      .not.toBe(await tradeCsvContentHash(first.rows))
  })
})

function validIdentityCsv(quantity: string) {
  return [
    'account,ticker,side,quantity,tradePrice,executedAt',
    `Primary Account,AAPL,BUY,${quantity},195.25,2026-07-28T01:30:00Z`,
  ].join('\n')
}
