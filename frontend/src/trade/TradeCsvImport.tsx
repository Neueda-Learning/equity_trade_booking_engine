import { useId, useRef, useState, type ChangeEvent } from 'react'
import {
  ApiProblemError,
  completeTradeImport,
  createTrade,
  registerTradeImport,
  type Account,
  type TradeImportRegistration,
} from '../api'
import { formatDateTime, formatDecimal, formatMoney } from '../format'
import { useI18n } from '../i18n'
import {
  MAX_TRADE_CSV_FILE_BYTES,
  parseTradeCsv,
  tradeCsvContentHash,
  type PreparedTradeCsvRow,
  type TradeCsvIssue,
} from './tradeCsv'
import tradeCsvDemoUrl from './samples/trade-import-demo.csv?url'
import tradeCsvTemplateUrl from './samples/trade-import-template.csv?url'

interface ImportFailure {
  rowNumber: number
  message: string
}

interface ImportResult {
  successCount: number
  failures: ImportFailure[]
}

function TradeCsvImport({
  accounts,
  onImported,
}: {
  accounts: Account[]
  onImported: () => void
}) {
  const { locale, t } = useI18n()
  const inputId = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const [expanded, setExpanded] = useState(false)
  const [fileName, setFileName] = useState('')
  const [rows, setRows] = useState<PreparedTradeCsvRow[]>([])
  const [contentHash, setContentHash] = useState('')
  const [issues, setIssues] = useState<TradeCsvIssue[]>([])
  const [fileError, setFileError] = useState('')
  const [importing, setImporting] = useState(false)
  const [progress, setProgress] = useState(0)
  const [result, setResult] = useState<ImportResult | null>(null)
  const [duplicate, setDuplicate] =
    useState<TradeImportRegistration | null>(null)

  async function selectFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return

    setFileName(file.name)
    setRows([])
    setContentHash('')
    setIssues([])
    setFileError('')
    setResult(null)
    setDuplicate(null)
    if (
      !file.name.toLowerCase().endsWith('.csv')
      || file.size > MAX_TRADE_CSV_FILE_BYTES
    ) {
      setFileError(t('trade.csvInvalidFile'))
      return
    }

    try {
      const parsed = parseTradeCsv(await file.text(), accounts)
      const hash = parsed.rows.length > 0
        ? await tradeCsvContentHash(parsed.rows)
        : ''
      setRows(parsed.rows)
      setIssues(parsed.issues)
      setContentHash(hash)
    } catch {
      setFileError(t('trade.csvReadFailed'))
    }
  }

  async function beginImport(repeatConfirmed: boolean) {
    setImporting(true)
    setProgress(0)
    setResult(null)
    setDuplicate(null)
    setFileError('')
    try {
      const registration = await registerTradeImport({
        contentHash,
        fileName,
        rowCount: rows.length,
        repeatConfirmed,
      })
      await importTrades(registration)
    } catch (error) {
      if (
        error instanceof ApiProblemError
        && error.problem.status === 409
        && error.problem.duplicateImport
      ) {
        setDuplicate(error.problem.duplicateImport)
      } else {
        setFileError(importErrorMessage(
          error,
          t('trade.csvRegistrationFailed'),
        ))
      }
      setImporting(false)
    }
  }

  async function importTrades(registration: TradeImportRegistration) {
    const failures: ImportFailure[] = []
    let successCount = 0

    for (let index = 0; index < rows.length; index += 1) {
      const row = rows[index]
      try {
        await createTrade(row.input)
        successCount += 1
      } catch (error) {
        failures.push({
          rowNumber: row.rowNumber,
          message: importErrorMessage(error, t('trade.requestFailed')),
        })
      }
      setProgress(index + 1)
    }

    try {
      await completeTradeImport(registration.importId, {
        importCount: registration.importCount,
        successCount,
        failureCount: failures.length,
      })
    } catch {
      setFileError(t('trade.csvResultFailed'))
    }
    setResult({ successCount, failures })
    setImporting(false)
    if (successCount > 0) onImported()
  }

  const visibleIssues = issues.slice(0, 8)
  const previewRows = rows.slice(0, 3)

  return (
    <section className="csv-import" aria-labelledby="csv-import-title">
      <div className="csv-import-heading">
        <div>
          <p className="csv-import-kicker">{t('trade.csvKicker')}</p>
          <h3 id="csv-import-title">{t('trade.csvTitle')}</h3>
          <p>{t('trade.csvDescription')}</p>
        </div>
        <button
          type="button"
          className="csv-import-toggle"
          aria-expanded={expanded}
          aria-controls="csv-import-content"
          onClick={() => setExpanded((current) => !current)}
        >
          {expanded ? t('trade.csvClose') : t('trade.csvOpen')}
        </button>
      </div>

      {expanded && (
        <div id="csv-import-content" className="csv-import-content">
          <div className="csv-import-links">
            <a href={tradeCsvTemplateUrl} download="trade-import-template.csv">
              {t('trade.csvTemplate')}
            </a>
            <a href={tradeCsvDemoUrl} download="trade-import-demo.csv">
              {t('trade.csvSample')}
            </a>
          </div>
          <p className="csv-import-help">
            {t('trade.csvColumns')}
            <br />
            {t('trade.csvSemantics')}
          </p>

          <div className="csv-file-field">
            <span>{t('trade.csvFile')}</span>
            <input
              ref={inputRef}
              id={inputId}
              className="csv-file-input"
              type="file"
              accept=".csv,text/csv"
              aria-label={t('trade.csvFile')}
              disabled={importing}
              onChange={(event) => void selectFile(event)}
            />
            <div className="csv-file-control">
              <button
                type="button"
                className="csv-file-button"
                disabled={importing}
                onClick={() => inputRef.current?.click()}
              >
                {t('trade.csvChooseFile')}
              </button>
              <span className="csv-file-name" aria-live="polite">
                {fileName || t('trade.csvNoFile')}
              </span>
            </div>
          </div>

          {(fileError || issues.length > 0) && (
            <div className="csv-import-alert" role="alert">
              <strong>{fileName || t('trade.csvFile')}</strong>
              {fileError && <p>{fileError}</p>}
              {visibleIssues.length > 0 && (
                <ul>
                  {visibleIssues.map((issue, index) => (
                    <li key={`${issue.code}-${issue.rowNumber ?? 0}-${index}`}>
                      {csvIssueMessage(issue, t)}
                    </li>
                  ))}
                </ul>
              )}
              {issues.length > visibleIssues.length && (
                <p>
                  {t('trade.csvMore', {
                    count: issues.length - visibleIssues.length,
                  })}
                </p>
              )}
            </div>
          )}

          {rows.length > 0 && (
            <div className="csv-import-ready">
              <strong>
                {t('trade.csvReady', {
                  file: fileName,
                  count: rows.length,
                })}
              </strong>
              <ul className="csv-import-preview">
                {previewRows.map((row) => (
                  <li key={row.rowNumber}>
                    <span>{row.accountName}</span>
                    <strong>{row.input.ticker}</strong>
                    <span>{row.input.side}</span>
                    <span>
                      {formatDecimal(row.input.quantity, locale)}
                      {' @ '}
                      {formatMoney(row.input.tradePrice, locale)}
                    </span>
                    <small>{formatDateTime(row.input.executedAt, locale)}</small>
                  </li>
                ))}
              </ul>
              {rows.length > previewRows.length && (
                <p>
                  {t('trade.csvMore', {
                    count: rows.length - previewRows.length,
                  })}
                </p>
              )}
              <p className="csv-import-notice">{t('trade.csvSorted')}</p>
              <button
                type="button"
                className="csv-import-submit"
                disabled={importing || result !== null}
                onClick={() => void beginImport(false)}
              >
                {importing
                  ? t('trade.csvImporting')
                  : t('trade.csvImport', { count: rows.length })}
              </button>
            </div>
          )}

          {duplicate && (
            <div
              className="csv-import-duplicate"
              role="alertdialog"
              aria-labelledby="csv-duplicate-title"
              aria-describedby="csv-duplicate-description"
            >
              <strong id="csv-duplicate-title">
                {t('trade.csvDuplicateTitle')}
              </strong>
              <p id="csv-duplicate-description">
                {t('trade.csvDuplicateMessage', {
                  date: formatDateTime(duplicate.lastImportedAt, locale),
                })}
              </p>
              <div className="csv-import-duplicate-actions">
                <button
                  type="button"
                  onClick={() => setDuplicate(null)}
                >
                  {t('trade.csvDuplicateCancel')}
                </button>
                <button
                  type="button"
                  className="csv-import-submit"
                  onClick={() => void beginImport(true)}
                >
                  {t('trade.csvDuplicateConfirm')}
                </button>
              </div>
            </div>
          )}

          {importing && (
            <div className="csv-import-progress" role="status">
              <progress value={progress} max={rows.length} />
              <span>
                {t('trade.csvProgress', {
                  current: progress,
                  total: rows.length,
                })}
              </span>
            </div>
          )}

          {result && (
            <div
              className={`csv-import-result ${
                result.failures.length > 0 ? 'csv-import-result--warning' : ''
              }`}
              role={result.successCount === 0 ? 'alert' : 'status'}
            >
              <strong>
                {result.failures.length === 0
                  ? t('trade.csvSuccess', { success: result.successCount })
                  : result.successCount === 0
                    ? t('trade.csvAllFailed', {
                        failed: result.failures.length,
                      })
                    : t('trade.csvPartial', {
                        success: result.successCount,
                        failed: result.failures.length,
                      })}
              </strong>
              {result.failures.length > 0 && (
                <ul>
                  {result.failures.map((failure) => (
                    <li key={failure.rowNumber}>
                      {t('trade.csvFailureRow', {
                        row: failure.rowNumber,
                        message: failure.message,
                      })}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>
      )}
    </section>
  )
}

function csvIssueMessage(
  issue: TradeCsvIssue,
  t: ReturnType<typeof useI18n>['t'],
) {
  const variables = {
    row: issue.rowNumber ?? '',
    value: issue.value ?? '',
  }
  const keyByCode = {
    emptyFile: 'trade.csvIssueEmpty',
    malformedCsv: 'trade.csvIssueMalformed',
    missingHeaders: 'trade.csvIssueMissingHeaders',
    duplicateHeaders: 'trade.csvIssueDuplicateHeaders',
    noRows: 'trade.csvIssueNoRows',
    tooManyRows: 'trade.csvIssueTooManyRows',
    columnCount: 'trade.csvIssueColumnCount',
    account: 'trade.csvIssueAccount',
    ticker: 'trade.csvIssueTicker',
    side: 'trade.csvIssueSide',
    quantity: 'trade.csvIssueQuantity',
    tradePrice: 'trade.csvIssueTradePrice',
    executedAt: 'trade.csvIssueExecutedAt',
    futureExecution: 'trade.csvIssueFutureExecution',
  } as const
  return t(keyByCode[issue.code], variables)
}

function importErrorMessage(error: unknown, fallback: string) {
  if (!(error instanceof ApiProblemError)) return fallback
  return Object.values(error.problem.errors ?? {})[0]
    ?? error.problem.detail
    ?? error.message
}

export default TradeCsvImport
