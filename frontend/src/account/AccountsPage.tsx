import { useEffect, useState, type FormEvent } from 'react'
import {
  ApiProblemError,
  activateAccount,
  createAccount,
  deactivateAccount,
  getAccounts,
  getPositions,
  updateAccount,
  type Account,
  type Position,
} from '../api'
import { formatDecimal, formatMoney } from '../format'
import { localizeApiErrors, localizedStatus, useI18n } from '../i18n'
import './AccountsPage.css'

const emptyForm = { name: '', broker: '', accountNumberLast4: '' }

function AccountsPage() {
  const { language, locale, t } = useI18n()
  const [accounts, setAccounts] = useState<Account[]>([])
  const [form, setForm] = useState(emptyForm)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [serverError, setServerError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [selectedAccountId, setSelectedAccountId] = useState<string | null>(null)
  const [positions, setPositions] = useState<Position[]>([])
  const [positionsLoading, setPositionsLoading] = useState(false)
  const [positionsError, setPositionsError] = useState('')
  const [changingStatusId, setChangingStatusId] = useState<string | null>(null)

  async function load(signal?: AbortSignal) {
    setLoading(true)
    setServerError('')
    try {
      setAccounts(await getAccounts(signal))
    } catch (error) {
      if (!(error instanceof DOMException && error.name === 'AbortError')) {
        setServerError(t('accounts.unavailable'))
      }
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }

  useEffect(() => {
    const controller = new AbortController()
    getAccounts(controller.signal)
      .then(setAccounts)
      .catch((error: unknown) => {
        if (!(error instanceof DOMException && error.name === 'AbortError')) {
          setServerError(t('accounts.unavailable'))
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [t])

  useEffect(() => {
    if (!selectedAccountId) return
    const controller = new AbortController()
    getPositions(selectedAccountId, controller.signal)
      .then(setPositions)
      .catch((error: unknown) => {
        if (!(error instanceof DOMException && error.name === 'AbortError')) {
          setPositionsError(t('accounts.positionsUnavailable'))
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setPositionsLoading(false)
      })
    return () => controller.abort()
  }, [selectedAccountId, t])

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setMessage('')
    setServerError('')
    setFieldErrors({})
    try {
      const account = editingId
        ? await updateAccount(editingId, form)
        : await createAccount(form)
      setMessage(
        editingId
          ? t('accounts.updated', { name: account.name })
          : t('accounts.created', { name: account.name }),
      )
      setForm(emptyForm)
      setEditingId(null)
      await load()
    } catch (error) {
      if (error instanceof ApiProblemError) {
        setFieldErrors(
          localizeApiErrors(error.problem.errors ?? {}, language),
        )
        if (!error.problem.errors) setServerError(error.message)
      } else {
        setServerError(t('accounts.requestFailed'))
      }
    } finally {
      setSaving(false)
    }
  }

  function edit(account: Account) {
    setEditingId(account.id)
    setForm({
      name: account.name,
      broker: account.broker,
      accountNumberLast4: account.accountNumberLast4 ?? '',
    })
    setMessage('')
    setServerError('')
    setFieldErrors({})
  }

  async function changeStatus(account: Account) {
    const activating = account.status === 'INACTIVE'
    setChangingStatusId(account.id)
    setMessage('')
    setServerError('')
    try {
      if (activating) {
        await activateAccount(account.id)
      } else {
        await deactivateAccount(account.id)
      }
      setMessage(t(
        activating ? 'accounts.activated' : 'accounts.deactivated',
        { name: account.name },
      ))
      await load()
    } catch (error) {
      setServerError(
        error instanceof ApiProblemError
          ? error.message
          : t('accounts.requestFailed'),
      )
    } finally {
      setChangingStatusId(null)
    }
  }

  return (
    <section className="accounts-layout" aria-labelledby="accounts-heading">
      <div className="panel">
        <p className="section-kicker">{t('accounts.kickerSetup')}</p>
        <h2 id="accounts-heading">
          {editingId ? t('accounts.editTitle') : t('accounts.createTitle')}
        </h2>
        <form className="account-form" onSubmit={submit}>
          <AccountField
            label={t('accounts.name')}
            name="name"
            value={form.name}
            error={fieldErrors.name}
            onChange={(name) => setForm({ ...form, name })}
          />
          <AccountField
            label={t('accounts.broker')}
            name="broker"
            value={form.broker}
            error={fieldErrors.broker}
            onChange={(broker) => setForm({ ...form, broker })}
          />
          <AccountField
            label={t('accounts.last4')}
            name="accountNumberLast4"
            value={form.accountNumberLast4}
            error={fieldErrors.accountNumberLast4}
            onChange={(accountNumberLast4) =>
              setForm({ ...form, accountNumberLast4 })
            }
            inputMode="numeric"
            maxLength={4}
          />
          <div className="form-actions">
            <button type="submit" disabled={saving}>
              {saving
                ? t('common.saving')
                : editingId
                  ? t('accounts.saveChanges')
                  : t('accounts.createTitle')}
            </button>
            {editingId && (
              <button
                type="button"
                className="button-secondary"
                onClick={() => {
                  setEditingId(null)
                  setForm(emptyForm)
                }}
              >
                {t('common.cancel')}
              </button>
            )}
          </div>
        </form>
        {message && <p className="notice notice--success">{message}</p>}
        {serverError && (
          <p className="notice notice--error" role="alert">
            {serverError}
          </p>
        )}
      </div>

      <div className="panel">
        <p className="section-kicker">{t('accounts.kickerList')}</p>
        <h2>{t('nav.accounts')}</h2>
        {loading && <p className="table-state">{t('accounts.loading')}</p>}
        {!loading && !serverError && accounts.length === 0 && (
          <p className="table-state">{t('accounts.empty')}</p>
        )}
        {!loading && accounts.length > 0 && (
          <div className="account-list">
            {accounts.map((account) => (
              <article className="account-card" key={account.id}>
                <div>
                  <h3>{account.name}</h3>
                  <p>
                    {account.broker}
                    {account.accountNumberLast4
                      ? ` · •••• ${account.accountNumberLast4}`
                      : ''}
                    {' · USD'}
                  </p>
                </div>
                <span className={`status-pill status-pill--${account.status.toLowerCase()}`}>
                  {localizedStatus(account.status, t)}
                </span>
                <div className="account-actions">
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedAccountId(account.id)
                      setPositions([])
                      setPositionsError('')
                      setPositionsLoading(true)
                    }}
                  >
                    {t('accounts.viewPositions')}
                  </button>
                  <button type="button" onClick={() => edit(account)}>
                    {t('common.edit')}
                  </button>
                  <button
                    type="button"
                    onClick={() => void changeStatus(account)}
                    disabled={changingStatusId === account.id}
                  >
                    {changingStatusId === account.id
                      ? t(
                        account.status === 'INACTIVE'
                          ? 'accounts.activating'
                          : 'accounts.deactivating',
                      )
                      : t(
                        account.status === 'INACTIVE'
                          ? 'accounts.activate'
                          : 'accounts.deactivate',
                      )}
                  </button>
                </div>
              </article>
            ))}
          </div>
        )}
        {selectedAccountId && (
          <div className="positions-panel">
            <h3>
              {t('accounts.positions', {
                name:
                  accounts.find((account) => account.id === selectedAccountId)
                    ?.name ?? t('common.account'),
              })}
            </h3>
            {positionsLoading && (
              <p className="table-state">{t('accounts.loadingPositions')}</p>
            )}
            {positionsError && (
              <p className="table-state table-state--error" role="alert">
                {positionsError}
              </p>
            )}
            {!positionsLoading &&
              !positionsError &&
              positions.length === 0 && (
                <p className="table-state">{t('dashboard.noPositions')}</p>
              )}
            {!positionsLoading &&
              !positionsError &&
              positions.length > 0 && (
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>{t('common.ticker')}</th>
                        <th>{t('common.quantity')}</th>
                        <th>{t('common.averageCost')}</th>
                        <th>{t('accounts.costBasis')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {positions.map((position) => (
                        <tr key={position.ticker}>
                          <td>{position.ticker}</td>
                          <td>{formatDecimal(position.quantity, locale)}</td>
                          <td>{formatMoney(position.averageCost, locale)}</td>
                          <td>{formatMoney(position.costBasis, locale)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
          </div>
        )}
      </div>
    </section>
  )
}

function AccountField({
  label,
  name,
  value,
  error,
  onChange,
  ...inputProps
}: {
  label: string
  name: string
  value: string
  error?: string
  onChange: (value: string) => void
  inputMode?: 'numeric'
  maxLength?: number
}) {
  return (
    <label>
      {label}
      <input
        {...inputProps}
        name={name}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${name}-error` : undefined}
      />
      {error && (
        <span id={`${name}-error`} className="field-error" role="alert">
          {error}
        </span>
      )}
    </label>
  )
}

export default AccountsPage
