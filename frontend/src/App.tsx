import { useEffect, useState } from 'react'
import './App.css'
import TradeBooking from './trade/TradeBooking'

type ConnectionState = 'loading' | 'connected' | 'unavailable'

interface HealthResponse {
  status?: string
}

function App() {
  const [connectionState, setConnectionState] =
    useState<ConnectionState>('loading')

  useEffect(() => {
    const controller = new AbortController()

    async function loadHealth() {
      try {
        const response = await fetch('/api/health', {
          signal: controller.signal,
        })
        const health = (await response.json()) as HealthResponse

        setConnectionState(
          response.ok && health.status === 'UP' ? 'connected' : 'unavailable',
        )
      } catch (error) {
        if (!(error instanceof DOMException && error.name === 'AbortError')) {
          setConnectionState('unavailable')
        }
      }
    }

    void loadHealth()
    return () => controller.abort()
  }, [])

  const status = {
    loading: {
      label: 'Loading',
      message: 'Checking backend and database connectivity…',
    },
    connected: {
      label: 'Connected',
      message: 'Backend and database are available.',
    },
    unavailable: {
      label: 'Unavailable',
      message: 'The system health check could not be completed.',
    },
  }[connectionState]

  return (
    <main className="app-shell">
      <section className="status-card" aria-labelledby="page-title">
        <p className="eyebrow">Walking Skeleton</p>
        <h1 id="page-title">Equity Trade Booking Engine</h1>
        <p className="summary">
          Book BUY equity trades and review the immutable booking ledger.
        </p>

        <div className={`health health--${connectionState}`} aria-live="polite">
          <span className="health__dot" aria-hidden="true" />
          <div>
            <p className="health__label">{status.label}</p>
            <p className="health__message">{status.message}</p>
          </div>
        </div>
      </section>

      <TradeBooking />
    </main>
  )
}

export default App
