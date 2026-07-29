import type { Account, TradeInput } from '../api'

export const MAX_TRADE_CSV_ROWS = 200
export const MAX_TRADE_CSV_FILE_BYTES = 1_000_000

const REQUIRED_HEADERS = [
  'account',
  'ticker',
  'side',
  'quantity',
  'tradePrice',
  'executedAt',
] as const

export type TradeCsvIssueCode =
  | 'emptyFile'
  | 'malformedCsv'
  | 'missingHeaders'
  | 'duplicateHeaders'
  | 'noRows'
  | 'tooManyRows'
  | 'columnCount'
  | 'account'
  | 'ticker'
  | 'side'
  | 'quantity'
  | 'tradePrice'
  | 'executedAt'
  | 'futureExecution'

export interface TradeCsvIssue {
  code: TradeCsvIssueCode
  rowNumber?: number
  value?: string
}

export interface PreparedTradeCsvRow {
  rowNumber: number
  accountName: string
  input: TradeInput
}

export interface TradeCsvParseResult {
  rows: PreparedTradeCsvRow[]
  issues: TradeCsvIssue[]
}

export function parseTradeCsv(
  source: string,
  activeAccounts: Account[],
  now = new Date(),
): TradeCsvParseResult {
  const parsed = parseCsvRecords(source.replace(/^\uFEFF/, ''))
  if (parsed.error) {
    return { rows: [], issues: [{ code: 'malformedCsv' }] }
  }

  const records = parsed.records.filter(
    (record) => record.some((field) => field.trim() !== ''),
  )
  if (records.length === 0) {
    return { rows: [], issues: [{ code: 'emptyFile' }] }
  }

  const headers = records[0].map((header) => header.trim())
  const normalizedHeaders = headers.map((header) => header.toLowerCase())
  const duplicateHeaders = normalizedHeaders.filter(
    (header, index) => normalizedHeaders.indexOf(header) !== index,
  )
  if (duplicateHeaders.length > 0) {
    return {
      rows: [],
      issues: [{
        code: 'duplicateHeaders',
        value: [...new Set(duplicateHeaders)].join(', '),
      }],
    }
  }

  const missingHeaders = REQUIRED_HEADERS.filter(
    (header) => !normalizedHeaders.includes(header.toLowerCase()),
  )
  if (missingHeaders.length > 0) {
    return {
      rows: [],
      issues: [{
        code: 'missingHeaders',
        value: missingHeaders.join(', '),
      }],
    }
  }

  const dataRecords = records.slice(1)
  if (dataRecords.length === 0) {
    return { rows: [], issues: [{ code: 'noRows' }] }
  }
  if (dataRecords.length > MAX_TRADE_CSV_ROWS) {
    return {
      rows: [],
      issues: [{
        code: 'tooManyRows',
        value: String(MAX_TRADE_CSV_ROWS),
      }],
    }
  }

  const headerIndexes = Object.fromEntries(
    REQUIRED_HEADERS.map((header) => [
      header,
      normalizedHeaders.indexOf(header.toLowerCase()),
    ]),
  ) as Record<(typeof REQUIRED_HEADERS)[number], number>
  const issues: TradeCsvIssue[] = []
  const rows: PreparedTradeCsvRow[] = []

  dataRecords.forEach((record, index) => {
    const rowNumber = index + 2
    if (record.length !== headers.length) {
      issues.push({ code: 'columnCount', rowNumber })
      return
    }

    const accountReference = record[headerIndexes.account].trim()
    const account = activeAccounts.find(
      (candidate) =>
        candidate.id.toLowerCase() === accountReference.toLowerCase()
        || candidate.name.toLowerCase() === accountReference.toLowerCase(),
    )
    const ticker = record[headerIndexes.ticker].trim().toUpperCase()
    const side = record[headerIndexes.side].trim().toUpperCase()
    const quantityText = record[headerIndexes.quantity].trim()
    const priceText = record[headerIndexes.tradePrice].trim()
    const executedAtText = record[headerIndexes.executedAt].trim()

    if (!account) issues.push({ code: 'account', rowNumber })
    if (!/^[A-Z][A-Z0-9.-]{0,9}$/.test(ticker)) {
      issues.push({ code: 'ticker', rowNumber })
    }
    if (side !== 'BUY' && side !== 'SELL') {
      issues.push({ code: 'side', rowNumber })
    }
    if (!isValidAmount(quantityText)) {
      issues.push({ code: 'quantity', rowNumber })
    }
    if (!isValidAmount(priceText)) {
      issues.push({ code: 'tradePrice', rowNumber })
    }

    const executedAt = parseExecutedAt(executedAtText)
    if (!executedAt) {
      issues.push({ code: 'executedAt', rowNumber })
    } else if (executedAt.getTime() > now.getTime() + 60_000) {
      issues.push({ code: 'futureExecution', rowNumber })
    }

    if (
      !account
      || !/^[A-Z][A-Z0-9.-]{0,9}$/.test(ticker)
      || (side !== 'BUY' && side !== 'SELL')
      || !isValidAmount(quantityText)
      || !isValidAmount(priceText)
      || !executedAt
      || executedAt.getTime() > now.getTime() + 60_000
    ) {
      return
    }

    rows.push({
      rowNumber,
      accountName: account.name,
      input: {
        accountId: account.id,
        ticker,
        side,
        quantity: Number(quantityText),
        tradePrice: Number(priceText),
        executedAt: executedAtText,
      },
    })
  })

  if (issues.length > 0) return { rows: [], issues }

  rows.sort(
    (left, right) =>
      Date.parse(left.input.executedAt) - Date.parse(right.input.executedAt)
      || left.rowNumber - right.rowNumber,
  )
  return { rows, issues: [] }
}

function isValidAmount(value: string) {
  const match = /^(\d+)(?:\.(\d{1,6}))?$/.exec(value)
  if (!match || match[1].length > 13) return false
  const numeric = Number(value)
  return Number.isFinite(numeric) && numeric > 0
}

function parseExecutedAt(value: string) {
  const match =
    /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,6})?)?(Z|[+-]\d{2}:\d{2})$/
      .exec(value)
  if (!match) return null
  const [, yearText, monthText, dayText, hourText, minuteText, secondText, zone] =
    match
  const year = Number(yearText)
  const month = Number(monthText)
  const day = Number(dayText)
  const hour = Number(hourText)
  const minute = Number(minuteText)
  const second = Number(secondText ?? '0')
  const daysInMonth =
    month >= 1 && month <= 12
      ? new Date(Date.UTC(year, month, 0)).getUTCDate()
      : 0
  if (
    day < 1
    || day > daysInMonth
    || hour > 23
    || minute > 59
    || second > 59
  ) {
    return null
  }
  if (zone !== 'Z') {
    const [offsetHour, offsetMinute] = zone.slice(1).split(':').map(Number)
    if (offsetHour > 23 || offsetMinute > 59) return null
  }
  const timestamp = Date.parse(value)
  return Number.isNaN(timestamp) ? null : new Date(timestamp)
}

function parseCsvRecords(source: string) {
  const records: string[][] = []
  let record: string[] = []
  let field = ''
  let quoted = false
  let closedQuote = false

  for (let index = 0; index < source.length; index += 1) {
    const character = source[index]
    if (quoted) {
      if (character === '"') {
        if (source[index + 1] === '"') {
          field += '"'
          index += 1
        } else {
          quoted = false
          closedQuote = true
        }
      } else {
        field += character
      }
      continue
    }

    if (closedQuote && character !== ',' && character !== '\n' && character !== '\r') {
      return { records: [], error: true }
    }
    if (character === '"') {
      if (field !== '') return { records: [], error: true }
      quoted = true
    } else if (character === ',') {
      record.push(field)
      field = ''
      closedQuote = false
    } else if (character === '\n' || character === '\r') {
      record.push(field)
      records.push(record)
      record = []
      field = ''
      closedQuote = false
      if (character === '\r' && source[index + 1] === '\n') index += 1
    } else {
      field += character
    }
  }

  if (quoted) return { records: [], error: true }
  if (field !== '' || record.length > 0 || closedQuote) {
    record.push(field)
    records.push(record)
  }
  return { records, error: false }
}
