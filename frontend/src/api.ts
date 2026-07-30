export interface Account {
  id: string
  name: string
  broker: string
  accountNumberLast4: string | null
  baseCurrency: 'USD'
  status: 'ACTIVE' | 'INACTIVE'
  createdAt: string
  updatedAt: string
}

export interface AccountInput {
  name: string
  broker: string
  accountNumberLast4: string
}

export interface Trade {
  id: string
  accountId: string
  ticker: string
  side: 'BUY' | 'SELL'
  quantity: number
  tradePrice: number
  executedAt: string
  status: 'BOOKED' | 'CANCELLED'
  createdAt: string
  cancelledAt: string | null
  cancellationReason: 'CANCELLED' | 'DELETED' | 'AMENDED' | null
  supersedesTradeId: string | null
}

export interface TradePage {
  items: Trade[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface TradeInput {
  accountId: string
  ticker: string
  side: 'BUY' | 'SELL'
  quantity: number
  tradePrice: number
  executedAt: string
}

export interface AmendTradeResponse {
  cancelledTrade: Trade
  replacementTrade: Trade
}

export interface Instrument {
  ticker: string
  name: string
  exchange: string
  type: string
}

export interface InstrumentSearchResponse {
  items: Instrument[]
}

export interface Position {
  accountId: string | null
  ticker: string
  quantity: number
  averageCost: number
  costBasis: number
}

export interface MarketQuote {
  ticker: string
  price: number
  previousClose: number
  change: number
  changePercent: number
  marketTimestamp: string
  fetchedAt: string
  source: string
  mock: boolean
  cached: boolean
  stale: boolean
}

export interface MarketQuoteList {
  items: MarketQuote[]
}

export interface MarketDataProviderStatus {
  provider: 'MOCK' | 'FINNHUB'
  configured: boolean
  demoControlsEnabled: boolean
  demoOutageEnabled: boolean
  lastSuccessAt: string | null
  lastFailureAt: string | null
  lastFailureCategory: string | null
}

export interface DemoOutage {
  enabled: boolean
  demoOnly: true
  message: string
}

export interface PositionPnl {
  accountId: string | null
  ticker: string
  quantity: number
  averageCost: number
  costBasis: number
  marketPrice: number | null
  marketValue: number | null
  unrealizedPnl: number | null
  pnlPercent: number | null
  quoteAsOf: string | null
  source: string | null
  mock: boolean
  cached: boolean
  stale: boolean
  available: boolean
}

export interface PnlTotals {
  totalCostBasis: number
  totalMarketValue: number
  totalUnrealizedPnl: number
  totalPnlPercent: number | null
  positionCount: number
  pricedPositionCount: number
  unpricedPositionCount: number
  complete: boolean
  mock: boolean
  stale: boolean
}

export interface PnlResponse {
  items: PositionPnl[]
  totals: PnlTotals
}

export interface DashboardActivity {
  id: string
  accountId: string
  accountName: string
  ticker: string
  side: 'BUY' | 'SELL'
  quantity: number
  tradePrice: number
  status: 'BOOKED' | 'CANCELLED'
  executedAt: string
  createdAt: string
  cancelledAt: string | null
  cancellationReason: 'CANCELLED' | 'DELETED' | 'AMENDED' | null
}

export interface DashboardResponse {
  totals: PnlTotals
  positions: PositionPnl[]
  accountCount: number
  activeAccountCount: number
  recentActivity: DashboardActivity[]
  quoteStatus: {
    available: number
    unavailable: number
    cached: number
    stale: number
    mock: number
  }
  capturedAt: string
}

export type HistoryRange = '1D' | '7D' | '30D' | 'ALL'

export interface ValuationSnapshot {
  id: string
  scopeType: 'ALL' | 'ACCOUNT'
  accountId: string | null
  valuationDate: string
  totalCostBasis: number
  totalMarketValue: number
  unrealizedPnl: number
  positionCount: number
  pricedPositionCount: number
  complete: boolean
  mock: boolean
  stale: boolean
  capturedAt: string
}

export interface ValuationHistory {
  range: HistoryRange
  source: 'LOCAL' | 'PROVIDER' | 'HYBRID'
  fallback: boolean
  failureCategory: string | null
  items: ValuationSnapshot[]
}

export interface ProblemDetails {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  errors?: Record<string, string>
  duplicateImport?: TradeImportRegistration
}

export interface TradeImportRegistration {
  importId: string
  firstFileName: string
  rowCount: number
  firstImportedAt: string
  lastImportedAt: string
  importCount: number
  status: 'IN_PROGRESS' | 'COMPLETED' | 'PARTIAL' | 'FAILED'
  lastSuccessCount: number
  lastFailureCount: number
}

export interface TradeImportRegistrationInput {
  contentHash: string
  fileName: string
  rowCount: number
  repeatConfirmed: boolean
}

export class ApiProblemError extends Error {
  readonly problem: ProblemDetails

  constructor(problem: ProblemDetails) {
    super(
      problem.detail ??
        problem.title ??
        'The request could not be completed.',
    )
    this.problem = problem
  }
}

async function request<T>(
  url: string,
  init?: RequestInit,
): Promise<T> {
  const response = await fetch(url, init)
  if (response.ok && response.status === 204) {
    return undefined as T
  }
  const payload = (await response.json()) as T | ProblemDetails
  if (!response.ok) {
    throw new ApiProblemError(payload as ProblemDetails)
  }
  return payload as T
}

export function getHealth(signal?: AbortSignal) {
  return request<{ status?: string }>('/api/health', { signal })
}

export function getAccounts(signal?: AbortSignal) {
  return request<Account[]>('/api/accounts', { signal })
}

export function createAccount(input: AccountInput) {
  return request<Account>('/api/accounts', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function updateAccount(id: string, input: AccountInput) {
  return request<Account>(`/api/accounts/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function deactivateAccount(id: string) {
  return request<Account>(`/api/accounts/${id}/deactivate`, {
    method: 'POST',
  })
}

export function activateAccount(id: string) {
  return request<Account>(`/api/accounts/${id}/activate`, {
    method: 'POST',
  })
}

export function deleteAccount(id: string) {
  return request<void>(`/api/accounts/${id}`, {
    method: 'DELETE',
  })
}

export function getTrades(
  page: number,
  size: number,
  accountId?: string,
  signal?: AbortSignal,
) {
  const query = new URLSearchParams({
    page: String(page),
    size: String(size),
  })
  if (accountId) {
    query.set('accountId', accountId)
  }
  return request<TradePage>(`/api/trades?${query}`, { signal })
}

export function createTrade(input: TradeInput) {
  return request<Trade>('/api/trades', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function registerTradeImport(
  input: TradeImportRegistrationInput,
) {
  return request<TradeImportRegistration>(
    '/api/trade-imports/registrations',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    },
  )
}

export function completeTradeImport(
  importId: string,
  result: {
    importCount: number
    successCount: number
    failureCount: number
  },
) {
  return request<TradeImportRegistration>(
    `/api/trade-imports/${importId}/result`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(result),
    },
  )
}

export function cancelTrade(id: string) {
  return request<Trade>(`/api/trades/${id}/cancel`, {
    method: 'POST',
  })
}

export function deleteTrade(id: string) {
  return request<Trade>(`/api/trades/${id}`, {
    method: 'DELETE',
  })
}

export function amendTrade(id: string, input: TradeInput) {
  return request<AmendTradeResponse>(`/api/trades/${id}/amend`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function searchInstruments(
  query: string,
  signal?: AbortSignal,
) {
  const search = new URLSearchParams({ q: query, limit: '10' })
  return request<InstrumentSearchResponse>(
    `/api/market-data/instruments/search?${search}`,
    { signal },
  )
}

export function getPositions(accountId?: string, signal?: AbortSignal) {
  const query = accountId
    ? `?${new URLSearchParams({ accountId })}`
    : ''
  return request<Position[]>(`/api/positions${query}`, { signal })
}

export function getMarketQuotes(
  accountId?: string,
  signal?: AbortSignal,
) {
  const query = accountId
    ? `?${new URLSearchParams({ accountId })}`
    : ''
  return request<MarketQuoteList>(
    `/api/market-data/quotes${query}`,
    { signal },
  )
}

export function getMarketQuote(ticker: string, signal?: AbortSignal) {
  return request<MarketQuote>(
    `/api/market-data/quotes/${encodeURIComponent(ticker)}`,
    { signal },
  )
}

export function refreshMarketQuote(ticker: string) {
  return request<MarketQuote>(
    `/api/market-data/quotes/${encodeURIComponent(ticker)}/refresh`,
    { method: 'POST' },
  )
}

export function getMarketDataProviderStatus(signal?: AbortSignal) {
  return request<MarketDataProviderStatus>(
    '/api/market-data/provider/status',
    { signal },
  )
}

export function getDemoMarketDataOutage(signal?: AbortSignal) {
  return request<DemoOutage>('/api/demo/market-data/outage', {
    signal,
  })
}

export function enableDemoMarketDataOutage() {
  return request<DemoOutage>(
    '/api/demo/market-data/outage/enable',
    { method: 'POST' },
  )
}

export function disableDemoMarketDataOutage() {
  return request<DemoOutage>(
    '/api/demo/market-data/outage/disable',
    { method: 'POST' },
  )
}

export function getPnl(accountId?: string, signal?: AbortSignal) {
  return request<PnlResponse>(withAccount('/api/pnl', accountId), {
    signal,
  })
}

export function getDashboard(
  accountId?: string,
  signal?: AbortSignal,
) {
  return request<DashboardResponse>(
    withAccount('/api/dashboard', accountId),
    { signal },
  )
}

export function refreshDashboard(accountId?: string) {
  return request<DashboardResponse>(
    withAccount('/api/dashboard/refresh', accountId),
    { method: 'POST' },
  )
}

export function getDashboardHistory(
  range: HistoryRange,
  accountId?: string,
  signal?: AbortSignal,
) {
  const query = new URLSearchParams({ range })
  if (accountId) query.set('accountId', accountId)
  return request<ValuationHistory>(
    `/api/dashboard/history?${query}`,
    { signal },
  )
}

function withAccount(path: string, accountId?: string) {
  return accountId
    ? `${path}?${new URLSearchParams({ accountId })}`
    : path
}
