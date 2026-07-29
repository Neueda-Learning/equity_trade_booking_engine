import { useEffect, useRef, useState } from 'react'
import { getHealth } from './api'
import AccountsPage from './account/AccountsPage'
import Dashboard from './dashboard/Dashboard'
import MarketData from './market/MarketData'
import TradeBooking from './trade/TradeBooking'
import { useI18n, type Language } from './i18n'
import './App.css'

type Page = 'dashboard' | 'accounts' | 'activity' | 'marketData'
type ConnectionState = 'loading' | 'connected' | 'unavailable'

const pages: { id: Page; icon: IconName }[] = [
  { id: 'dashboard', icon: 'dashboard' },
  { id: 'accounts', icon: 'accounts' },
  { id: 'activity', icon: 'activity' },
  { id: 'marketData', icon: 'market' },
]

function App() {
  const { t } = useI18n()
  const [page, setPage] = useState<Page>('dashboard')
  const [connectionState, setConnectionState] =
    useState<ConnectionState>('loading')
  const [menuOpen, setMenuOpen] = useState(false)
  const menuButtonRef = useRef<HTMLButtonElement>(null)

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

  useEffect(() => {
    if (!menuOpen) return
    function closeOnEscape(event: globalThis.KeyboardEvent) {
      if (event.key === 'Escape') {
        setMenuOpen(false)
        menuButtonRef.current?.focus()
      }
    }
    document.addEventListener('keydown', closeOnEscape)
    return () => document.removeEventListener('keydown', closeOnEscape)
  }, [menuOpen])

  function navigate(nextPage: Page) {
    setPage(nextPage)
    setMenuOpen(false)
  }

  return (
    <div className="app-layout">
      <aside
        id="primary-sidebar"
        className={`app-sidebar ${menuOpen ? 'app-sidebar--open' : ''}`}
        aria-label={t('nav.sidebar')}
      >
        <div className="sidebar-brand">
          <span className="brand-mark" aria-hidden="true">E</span>
          <div className="brand-copy">
            <p>{t('brand.portfolio')}</p>
            <strong>{t('brand.console')}</strong>
          </div>
          <button
            type="button"
            className="sidebar-close"
            aria-label={t('nav.close')}
            onClick={() => {
              setMenuOpen(false)
              menuButtonRef.current?.focus()
            }}
          >
            ×
          </button>
        </div>

        <nav className="main-nav" aria-label={t('nav.primary')}>
          {pages.map((item) => (
            <button
              key={item.id}
              type="button"
              aria-current={page === item.id ? 'page' : undefined}
              aria-label={t(`nav.${item.id}`)}
              title={t(`nav.${item.id}`)}
              onClick={() => navigate(item.id)}
            >
              <NavIcon name={item.icon} />
              <span>{t(`nav.${item.id}`)}</span>
            </button>
          ))}
        </nav>

        <LanguageSwitcher />

        <div className={`sidebar-health health-chip--${connectionState}`}>
          <span className="health-dot" aria-hidden="true" />
          <div>
            <small>{t('backend.label')}</small>
            <strong>
              {t(`backend.${connectionState}`)}
            </strong>
          </div>
        </div>
      </aside>

      {menuOpen && (
        <button
          type="button"
          className="sidebar-backdrop"
          aria-label={t('nav.close')}
          onClick={() => {
            setMenuOpen(false)
            menuButtonRef.current?.focus()
          }}
        />
      )}

      <div className="app-main">
        <header className="mobile-header">
          <button
            ref={menuButtonRef}
            type="button"
            className="menu-button"
            aria-label={t('nav.open')}
            aria-expanded={menuOpen}
            aria-controls="primary-sidebar"
            onClick={() => setMenuOpen(true)}
          >
            <NavIcon name="menu" />
          </button>
          <div>
            <small>{t('brand.portfolio')}</small>
            <strong>{t(`nav.${page}`)}</strong>
          </div>
          <span
            className={`mobile-health health-chip--${connectionState}`}
            aria-label={`${t('backend.label')} ${t(`backend.${connectionState}`)}`}
          >
            <span className="health-dot" aria-hidden="true" />
          </span>
        </header>

        <main className="page-content">
          {page === 'dashboard' && (
            <Dashboard onViewActivity={() => navigate('activity')} />
          )}
          {page === 'accounts' && <AccountsPage />}
          {page === 'activity' && <TradeBooking />}
          {page === 'marketData' && <MarketData />}
        </main>
      </div>
    </div>
  )
}

function LanguageSwitcher() {
  const { language, setLanguage, t } = useI18n()
  const languages: Language[] = ['en', 'zh-CN', 'pt-BR']
  return (
    <label className="language-switcher">
      <span>{t('language.label')}</span>
      <select
        value={language}
        onChange={(event) => setLanguage(event.target.value as Language)}
        aria-label={t('language.label')}
      >
        {languages.map((item) => (
          <option key={item} value={item}>
            {t(`language.${item}`)}
          </option>
        ))}
      </select>
    </label>
  )
}

type IconName = 'dashboard' | 'accounts' | 'activity' | 'market' | 'menu'

function NavIcon({ name }: { name: IconName }) {
  const paths: Record<IconName, string> = {
    dashboard: 'M4 13h6V4H4v9Zm0 7h6v-4H4v4Zm10 0h6v-9h-6v9Zm0-16v4h6V4h-6Z',
    accounts: 'M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5s-3 1.34-3 3 1.34 3 3 3ZM8 11c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5 5 6.34 5 8s1.34 3 3 3Zm8 2c-2 0-6 1-6 3v3h12v-3c0-2-4-3-6-3ZM8 13c-2.33 0-6 1.17-6 3.5V19h6v-3c0-.85.33-1.58.91-2.2A8.8 8.8 0 0 0 8 13Z',
    activity: 'M3 12h4l3-7 4 14 3-7h4',
    market: 'M4 19V9m5 10V5m5 14v-7m5 7V3',
    menu: 'M4 6h16M4 12h16M4 18h16',
  }
  return (
    <svg
      viewBox="0 0 24 24"
      width="22"
      height="22"
      aria-hidden="true"
    >
      {name === 'activity' || name === 'market' || name === 'menu' ? (
        <path
          d={paths[name]}
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      ) : (
        <path d={paths[name]} fill="currentColor" />
      )}
    </svg>
  )
}

export default App
