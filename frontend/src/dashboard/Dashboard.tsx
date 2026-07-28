import { useEffect, useState } from 'react'
import { getAccounts, getTrades, type Account, type Trade } from '../api'

function Dashboard() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [trades, setTrades] = useState<Trade[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([
      getAccounts(controller.signal),
      getTrades(0, 5, undefined, controller.signal),
    ])
      .then(([loadedAccounts, page]) => {
        setAccounts(loadedAccounts)
        setTrades(page.items)
      })
      .catch((reason: unknown) => {
        if (!(reason instanceof DOMException && reason.name === 'AbortError')) {
          setError('Dashboard data is unavailable.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [])

  const names = new Map(accounts.map((account) => [account.id, account.name]))

  return (
    <section aria-labelledby="dashboard-heading">
      <p className="section-kicker">Overview</p>
      <h2 id="dashboard-heading">Dashboard</h2>
      {loading && <p className="table-state">Loading dashboard…</p>}
      {error && <p className="table-state table-state--error">{error}</p>}
      {!loading && !error && (
        <>
          <div className="metrics">
            <article>
              <span>Total accounts</span>
              <strong>{accounts.length}</strong>
            </article>
            <article>
              <span>Active accounts</span>
              <strong>
                {accounts.filter((account) => account.status === 'ACTIVE').length}
              </strong>
            </article>
            <article>
              <span>Recent activity</span>
              <strong>{trades.length}</strong>
            </article>
          </div>
          <div className="panel dashboard-activity">
            <h3>Recent Activity</h3>
            {trades.length === 0 ? (
              <p className="table-state">No activity yet.</p>
            ) : (
              <ul>
                {trades.map((trade) => (
                  <li key={trade.id}>
                    <strong>{trade.ticker}</strong> BUY ·{' '}
                    {names.get(trade.accountId) ?? 'Unknown account'}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      )}
    </section>
  )
}

export default Dashboard
