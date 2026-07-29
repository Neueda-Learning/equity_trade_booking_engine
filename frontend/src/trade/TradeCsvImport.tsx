import { useId, useState, type ChangeEvent } from 'react'
import {
  ApiProblemError,
  createTrade,
  type Account,
} from '../api'
import { formatDateTime, formatDecimal, formatMoney } from '../format'
import { useI18n } from '../i18n'
import {
  MAX_TRADE_CSV_FILE_BYTES,
  parseTradeCsv,
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
  const [expanded, setExpanded] = useState(false)
  const [fileName, setFileName] = useState('')
  const [rows, setRows] = useState<PreparedTradeCsvRow[]>([])
  const [issues, setIssues] = useState<TradeCsvIssue[]>([])
  const [fileError, setFileError] = useState('')
  const [importing, setImporting] = useState(false)
  const [progress, setProgress] = useState(0)
  const [result, setResult] = useState<ImportResult | null>(null)

  async function selectFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return

    setFileName(file.name)
    setRows([])
    setIssues([])
    setFileError('')
    setResult(null)
    if (
      !file.name.toLowerCase().endsWith('.csv')
      || file.size > MAX_TRADE_CSV_FILE_BYTES
    ) {
      setFileError(t('trade.csvInvalidFile'))
      return
    }

    try {
      const parsed = parseTradeCsv(await file.text(), accounts)
      setRows(parsed.rows)
      setIssues(parsed.issues)
    } catch {
      setFileError(t('trade.csvReadFailed'))
    }
  }

  async function importTrades() {
    setImporting(true)
    setProgress(0)
    setResult(null)
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

          <label className="csv-file-field" htmlFor={inputId}>
            <span>{t('trade.csvFile')}</span>
            <input
              id={inputId}
              type="file"
              accept=".csv,text/csv"
              disabled={importing}
              onChange={(event) => void selectFile(event)}
            />
          </label>

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
                onClick={() => void importTrades()}
              >
                {importing
                  ? t('trade.csvImporting')
                  : t('trade.csvImport', { count: rows.length })}
              </button>
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
