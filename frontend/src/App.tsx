import { useEffect, useState } from 'react'
import { getHealth } from './api'
import AccountsPage from './account/AccountsPage'
import Dashboard from './dashboard/Dashboard'
import MarketData from './market/MarketData'
import TradeBooking from './trade/TradeBooking'
import './App.css'

type Page = 'Dashboard' | 'Accounts' | 'Activity' | 'Market Data'
type ConnectionState = 'loading' | 'connected' | 'unavailable'

const pages: Page[] = ['Dashboard', 'Accounts', 'Activity', 'Market Data']

function App() {
  const [page, setPage] = useState<Page>('Dashboard')
  const [connectionState, setConnectionState] =
    useState<ConnectionState>('loading')

  useEffect(() => {
    const controller = new AbortController()
    getHealth(controller.signal)
      .then((health) =>
        setConnectionState(
          health.status === 'UP' ? 'connected' : 'unavailable',
        ),
      )
      .catch((error: unknown) => {
        if (!(error instanceof DOMException && error.name === 'AbortError')) {
          setConnectionState('unavailable')
        }
      })
    return () => controller.abort()
  }, [])

  return (
    <div className="app-shell">
      <header className="app-header">
        <div>
          <p className="eyebrow">Equity operations</p>
          <h1>Trade Booking Engine</h1>
        </div>
        <div className={`health-chip health-chip--${connectionState}`}>
          <span aria-hidden="true" />
          {connectionState === 'loading'
            ? 'Loading'
            : connectionState === 'connected'
              ? 'Connected'
              : 'Unavailable'}
        </div>
      </header>

      <nav className="main-nav" aria-label="Primary">
        {pages.map((item) => (
          <button
            key={item}
            type="button"
            aria-current={page === item ? 'page' : undefined}
            onClick={() => setPage(item)}
          >
            {item}
          </button>
        ))}
      </nav>

      <main className="page-content">
        {page === 'Dashboard' && <Dashboard />}
        {page === 'Accounts' && <AccountsPage />}
        {page === 'Activity' && <TradeBooking />}
        {page === 'Market Data' && <MarketData />}
      </main>
    </div>
  )
}

export default App
