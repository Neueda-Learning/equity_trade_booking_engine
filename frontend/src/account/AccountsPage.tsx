import { useEffect, useState, type FormEvent } from 'react'
import {
  ApiProblemError,
  createAccount,
  deactivateAccount,
  getAccounts,
  getPositions,
  updateAccount,
  type Account,
  type Position,
} from '../api'
import './AccountsPage.css'

const emptyForm = { name: '', broker: '', accountNumberLast4: '' }

function AccountsPage() {
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

  async function load(signal?: AbortSignal) {
    setLoading(true)
    setServerError('')
    try {
      setAccounts(await getAccounts(signal))
    } catch (error) {
      if (!(error instanceof DOMException && error.name === 'AbortError')) {
        setServerError('Accounts are unavailable.')
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
          setServerError('Accounts are unavailable.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!selectedAccountId) return
    const controller = new AbortController()
    getPositions(selectedAccountId, controller.signal)
      .then(setPositions)
      .catch((error: unknown) => {
        if (!(error instanceof DOMException && error.name === 'AbortError')) {
          setPositionsError('Positions are unavailable.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setPositionsLoading(false)
      })
    return () => controller.abort()
  }, [selectedAccountId])

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
          ? `${account.name} updated.`
          : `${account.name} created.`,
      )
      setForm(emptyForm)
      setEditingId(null)
      await load()
    } catch (error) {
      if (error instanceof ApiProblemError) {
        setFieldErrors(error.problem.errors ?? {})
        if (!error.problem.errors) setServerError(error.message)
      } else {
        setServerError('The account request could not reach the backend.')
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

  async function deactivate(account: Account) {
    setMessage('')
    setServerError('')
    try {
      await deactivateAccount(account.id)
      setMessage(`${account.name} deactivated.`)
      await load()
    } catch (error) {
      setServerError(
        error instanceof ApiProblemError
          ? error.message
          : 'The account request could not reach the backend.',
      )
    }
  }

  return (
    <section className="accounts-layout" aria-labelledby="accounts-heading">
      <div className="panel">
        <p className="section-kicker">Account setup</p>
        <h2 id="accounts-heading">
          {editingId ? 'Edit account' : 'Create account'}
        </h2>
        <form className="account-form" onSubmit={submit}>
          <AccountField
            label="Account name"
            name="name"
            value={form.name}
            error={fieldErrors.name}
            onChange={(name) => setForm({ ...form, name })}
          />
          <AccountField
            label="Broker"
            name="broker"
            value={form.broker}
            error={fieldErrors.broker}
            onChange={(broker) => setForm({ ...form, broker })}
          />
          <AccountField
            label="Account number last 4"
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
              {saving ? 'Saving…' : editingId ? 'Save changes' : 'Create account'}
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
                Cancel
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
        <p className="section-kicker">Securities accounts</p>
        <h2>Accounts</h2>
        {loading && <p className="table-state">Loading accounts…</p>}
        {!loading && !serverError && accounts.length === 0 && (
          <p className="table-state">No accounts yet.</p>
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
                  {account.status}
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
                    View positions
                  </button>
                  <button type="button" onClick={() => edit(account)}>
                    Edit
                  </button>
                  <button
                    type="button"
                    onClick={() => void deactivate(account)}
                    disabled={account.status === 'INACTIVE'}
                  >
                    Deactivate
                  </button>
                </div>
              </article>
            ))}
          </div>
        )}
        {selectedAccountId && (
          <div className="positions-panel">
            <h3>
              Positions ·{' '}
              {accounts.find((account) => account.id === selectedAccountId)
                ?.name ?? 'Account'}
            </h3>
            {positionsLoading && (
              <p className="table-state">Loading positions…</p>
            )}
            {positionsError && (
              <p className="table-state table-state--error" role="alert">
                {positionsError}
              </p>
            )}
            {!positionsLoading &&
              !positionsError &&
              positions.length === 0 && (
                <p className="table-state">No open positions.</p>
              )}
            {!positionsLoading &&
              !positionsError &&
              positions.length > 0 && (
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>Ticker</th>
                        <th>Quantity</th>
                        <th>Average cost</th>
                        <th>Cost basis</th>
                      </tr>
                    </thead>
                    <tbody>
                      {positions.map((position) => (
                        <tr key={position.ticker}>
                          <td>{position.ticker}</td>
                          <td>{position.quantity}</td>
                          <td>{position.averageCost}</td>
                          <td>{position.costBasis}</td>
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
