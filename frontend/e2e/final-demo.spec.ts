import { expect, test, type Page } from '@playwright/test'

test('complete booking, P&L, cancellation, and outage journey', async ({
  page,
}, testInfo) => {
  const accountName = `E2E ${testInfo.project.name}`
  const ticker = testInfo.project.name.startsWith('mobile') ? 'MSFT' : 'AAPL'
  const unavailableTicker = testInfo.project.name.startsWith('mobile')
    ? 'SPY'
    : 'QQQ'
  const browserMessages: string[] = []
  const unexpectedServerErrors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error' || message.type() === 'warning') {
      if (
        message.type() === 'error'
        && message.text().includes('status of 503')
      ) {
        return
      }
      browserMessages.push(`${message.type()}: ${message.text()}`)
    }
  })
  page.on('pageerror', (error) =>
    browserMessages.push(`pageerror: ${error.message}`),
  )
  page.on('response', (response) => {
    if (response.status() < 500) return
    const path = new URL(response.url()).pathname
    if (
      response.status() === 503
      && path === `/api/market-data/quotes/${unavailableTicker}`
    ) {
      return
    }
    unexpectedServerErrors.push(`${response.status()} ${path}`)
  })

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Dashboard' }))
    .toBeVisible()
  if (await page.getByRole('button', { name: 'Open navigation' }).isVisible()) {
    await expect(page.getByLabel('Backend connected')).toBeVisible()
  } else {
    await expect(page.getByText('Connected')).toBeVisible()
  }
  await expect(page.getByRole('heading', { name: 'Recent Activity' })).toBeVisible()
  await expectNoPageOverflow(page)

  await navigate(page, 'Accounts')
  await page.getByLabel('Account name').fill(accountName)
  await page.getByLabel('Broker').fill('Playwright Broker')
  await page.getByLabel('Account number last 4').fill('4242')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByText(`${accountName} created.`)).toBeVisible()
  await expect(
    page.getByRole('article').filter({ hasText: accountName }),
  ).toBeVisible()
  await expectNoPageOverflow(page)

  const activityNavigation = page.getByRole('button', { name: 'Trade' })
  await activityNavigation.focus()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('heading', { name: 'Book a trade' })).toBeVisible()
  await accountSelector(page).selectOption({
    label: accountName,
  })
  await bookTrade(page, 'BUY', ticker, '10', '100')
  await expect(
    page.getByText(`${ticker} BUY trade booked successfully.`),
  ).toBeVisible()
  await accountSelector(page).selectOption({
    label: accountName,
  })
  await bookTrade(page, 'SELL', ticker, '4', '110')
  await expect(
    page.getByText(`${ticker} SELL trade booked successfully.`),
  ).toBeVisible()
  await expect(activityRow(page, accountName, ticker, 'SELL'))
    .toContainText('BOOKED')
  await expectNoPageOverflow(page)

  await navigate(page, 'Accounts')
  const accountCard = page.getByRole('article').filter({ hasText: accountName })
  await accountCard.getByRole('button', { name: 'View positions' }).click()
  const initialPosition = page
    .locator('.positions-panel')
    .getByRole('row')
    .filter({ hasText: ticker })
  await expect(initialPosition).toContainText('6')
  await expectNoPageOverflow(page)

  await navigate(page, 'Market Data')
  await accountSelector(page).selectOption({
    label: accountName,
  })
  await page.getByRole('button', { name: `Refresh ${ticker}` }).click()
  const liveQuote = quoteRow(page, ticker)
  await expect(liveQuote).toContainText('FINNHUB')
  await expect(liveQuote).toContainText('LIVE')
  await expect(liveQuote).toContainText('123.46')
  await expect(liveQuote).not.toContainText('STALE')
  await expectNoPageOverflow(page)

  await navigate(page, 'Dashboard')
  await accountSelector(page).selectOption({
    label: accountName,
  })
  const pnlRow = dashboardPositionRow(page, ticker)
  await expect(pnlRow).toContainText('6')
  await expect(pnlRow).toContainText('FINNHUB')
  await expect(
    page
      .locator('.dashboard-metrics article')
      .filter({ hasText: 'Unrealized P&L' }),
  ).toBeVisible()
  await expectNoPageOverflow(page)

  await navigate(page, 'Trade')
  page.once('dialog', (dialog) => dialog.accept())
  await activityRow(page, accountName, ticker, 'SELL')
    .getByRole('button', { name: 'Delete' })
    .click()
  await expect(
    page.getByText('Activity deleted with its audit record preserved.'),
  ).toBeVisible()
  await expect(activityRow(page, accountName, ticker, 'SELL'))
    .toContainText('CANCELLED')
  await expect(activityRow(page, accountName, ticker, 'SELL'))
    .toContainText('DELETED')

  await navigate(page, 'Accounts')
  await page
    .getByRole('article')
    .filter({ hasText: accountName })
    .getByRole('button', { name: 'View positions' })
    .click()
  await expect(
    page.locator('.positions-panel').getByRole('row').filter({ hasText: ticker }),
  ).toContainText('10')

  await navigate(page, 'Dashboard')
  await accountSelector(page).selectOption({
    label: accountName,
  })
  await page.getByRole('button', { name: 'Refresh', exact: true }).click()
  await expect(dashboardPositionRow(page, ticker)).toContainText('10')

  await navigate(page, 'Market Data')
  await accountSelector(page).selectOption({
    label: accountName,
  })
  await page.getByRole('button', { name: 'Simulate outage' }).click()
  await expect(page.getByText('Provider outage: SIMULATED')).toBeVisible()
  const staleQuote = quoteRow(page, ticker)
  await expect(staleQuote).toContainText('STALE')
  await expect(staleQuote).toContainText('CACHED')
  await expect(staleQuote).toContainText('123.46')
  await expect(staleQuote).not.toContainText('LIVE')

  await page.getByLabel('Ticker search').fill(unavailableTicker)
  await page.getByRole('button', { name: 'Search' }).click()
  await expect(
    page.getByText(
      'Demo outage is enabled and no cached quote is available.',
    ),
  ).toBeVisible()
  await expect(quoteRow(page, unavailableTicker)).toContainText('Unavailable')
  await expect(staleQuote).toContainText('STALE')

  await navigate(page, 'Dashboard')
  await accountSelector(page).selectOption({
    label: accountName,
  })
  await page.getByRole('button', { name: 'Refresh', exact: true }).click()
  await expect(page.getByText(/Cached, stale quotes are being used/)).toBeVisible()
  await expect(dashboardPositionRow(page, ticker)).toContainText('STALE')
  await expect(dashboardPositionRow(page, ticker)).not.toContainText('Unavailable')

  await navigate(page, 'Market Data')
  await page.getByRole('button', { name: 'Restore provider' }).click()
  await expect(page.getByText('Provider outage: OFF')).toBeVisible()
  const restoredQuote = quoteRow(page, ticker)
  await expect(restoredQuote).toContainText('LIVE')
  await expect(restoredQuote).not.toContainText('STALE')
  await expectNoPageOverflow(page)

  expect(
    unexpectedServerErrors,
    unexpectedServerErrors.join('\n'),
  ).toEqual([])
  expect(browserMessages, browserMessages.join('\n')).toEqual([])
})

test('warns before importing the same CSV table again', async ({
  page,
}, testInfo) => {
  const accountName = `CSV E2E ${testInfo.project.name}`
  const ticker = testInfo.project.name.startsWith('mobile') ? 'MSFT' : 'AAPL'
  const executedAt = new Date(Date.now() - 60_000).toISOString()
  const contents = [
    'account,ticker,side,quantity,tradePrice,executedAt',
    `${accountName},${ticker},BUY,1,100,${executedAt}`,
  ].join('\n')

  await page.goto('/')
  await navigate(page, 'Accounts')
  await page.getByLabel('Account name').fill(accountName)
  await page.getByLabel('Broker').fill('CSV Playwright')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByText(`${accountName} created.`)).toBeVisible()

  await navigateToTrade(page)
  await page.getByRole('button', { name: 'Open importer' }).click()
  await page.getByLabel('CSV file').setInputFiles({
    name: 'trades.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(contents),
  })
  await page.getByRole('button', { name: 'Import 1 trades' }).click()
  await expect(page.getByText('Imported 1 trades successfully.')).toBeVisible()

  await page.getByLabel('CSV file').setInputFiles({
    name: 'renamed.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(contents),
  })
  await page.getByRole('button', { name: 'Import 1 trades' }).click()
  const warning = page.getByRole('alertdialog', {
    name: 'CSV table already imported',
  })
  await expect(warning).toContainText(
    'This will create another set of trades.',
  )
  await warning.getByRole('button', { name: 'Cancel' }).click()
  await expect(warning).not.toBeVisible()
  await expect(activityRow(page, accountName, ticker, 'BUY')).toHaveCount(1)

  await page.getByRole('button', { name: 'Import 1 trades' }).click()
  await page
    .getByRole('alertdialog', { name: 'CSV table already imported' })
    .getByRole('button', { name: 'Import again' })
    .click()
  await expect(page.getByText('Imported 1 trades successfully.')).toBeVisible()
  await expect(activityRow(page, accountName, ticker, 'BUY')).toHaveCount(2)
  await expectNoPageOverflow(page)
})

async function navigate(
  page: Page,
  name: 'Dashboard' | 'Accounts' | 'Trade' | 'Market Data',
) {
  const menu = page.getByRole('button', { name: 'Open navigation' })
  if (await menu.isVisible()) await menu.click()
  await page.getByRole('button', { name, exact: true }).click()
  await expect(
    page.getByRole('button', { name, exact: true }),
  ).toHaveAttribute('aria-current', 'page')
}

async function navigateToTrade(page: Page) {
  const menu = page.getByRole('button', { name: 'Open navigation' })
  if (await menu.isVisible()) await menu.click()
  const tradeNavigation = page.getByRole('button', {
    name: /^(Activity|Trade)$/,
  })
  await tradeNavigation.click()
  await expect(tradeNavigation).toHaveAttribute('aria-current', 'page')
}

async function bookTrade(
  page: Page,
  side: 'BUY' | 'SELL',
  ticker: string,
  quantity: string,
  price: string,
) {
  await page
    .getByRole('combobox', { name: 'Side', exact: true })
    .selectOption(side)
  const tickerSearch = page.getByRole('combobox', {
    name: 'Ticker or company',
  })
  await tickerSearch.fill(ticker)
  await page.getByRole('option', {
    name: new RegExp(`^${ticker}\\b`),
  }).click()
  await expect(page.getByText(new RegExp(`^Verified: ${ticker}`)))
    .toBeVisible()
  const [accountHeight, sideHeight, tickerHeight, quantityHeight] =
    await Promise.all([
      accountSelector(page).evaluate(
        (element) => element.getBoundingClientRect().height,
      ),
      page.getByRole('combobox', { name: 'Side', exact: true }).evaluate(
        (element) => element.getBoundingClientRect().height,
      ),
      tickerSearch.evaluate(
        (element) => element.getBoundingClientRect().height,
      ),
      page.getByLabel('Quantity').evaluate(
        (element) => element.getBoundingClientRect().height,
      ),
    ])
  expect(Math.abs(accountHeight - tickerHeight)).toBeLessThanOrEqual(1)
  expect(Math.abs(sideHeight - quantityHeight)).toBeLessThanOrEqual(1)
  expect(Math.abs(tickerHeight - quantityHeight)).toBeLessThanOrEqual(1)
  await page.getByLabel('Quantity').fill(quantity)
  await page.getByLabel('Trade price (USD)').fill(price)
  await page.getByRole('button', { name: `Book ${side} trade` }).click()
}

function activityRow(
  page: Page,
  accountName: string,
  ticker: string,
  side: 'BUY' | 'SELL',
) {
  return page
    .locator('.ledger-panel')
    .getByRole('row')
    .filter({ hasText: ticker })
    .filter({ hasText: accountName })
    .filter({ hasText: side })
}

function quoteRow(page: Page, ticker: string) {
  return page.getByRole('row').filter({ hasText: ticker }).first()
}

function dashboardPositionRow(page: Page, ticker: string) {
  return page
    .getByRole('heading', { name: 'Position P&L' })
    .locator('..')
    .getByRole('row')
    .filter({ hasText: ticker })
}

function accountSelector(page: Page) {
  return page.getByRole('combobox', { name: 'Account', exact: true })
}

async function expectNoPageOverflow(page: Page) {
  await expect
    .poll(() =>
      page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    )
    .toBe(true)
}
