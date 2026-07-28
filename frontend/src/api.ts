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
  side: 'BUY'
  quantity: number
  tradePrice: number
  executedAt: string
  status: 'BOOKED'
  createdAt: string
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
  side: 'BUY'
  quantity: number
  tradePrice: number
  executedAt: string
}

export interface ProblemDetails {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  errors?: Record<string, string>
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
