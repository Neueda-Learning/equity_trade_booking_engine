import { expect, test, type Page } from '@playwright/test'

test('complete booking, P&L, cancellation, and outage journey', async ({
  page,
}, testInfo) => {
  const browserMessages: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error' || message.type() === 'warning') {
      browserMessages.push(`${message.type()}: ${message.text()}`)
    }
  })
  page.on('pageerror', (error) =>
    browserMessages.push(`pageerror: ${error.message}`),
  )

  const accountName = `E2E ${testInfo.project.name}`
  const ticker = testInfo.project.name.startsWith('mobile') ? 'MSFT' : 'AAPL'
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
  await expect(liveQuote).toContainText('123.456789')
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
  await page.getByRole('button', { name: `Refresh ${ticker}` }).click()
  const staleQuote = quoteRow(page, ticker)
  await expect(staleQuote).toContainText('STALE')
  await expect(staleQuote).toContainText('CACHED')
  await expect(staleQuote).toContainText('123.456789')
  await expect(staleQuote).not.toContainText('LIVE')

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
  await page.getByRole('button', { name: `Refresh ${ticker}` }).click()
  const restoredQuote = quoteRow(page, ticker)
  await expect(restoredQuote).toContainText('LIVE')
  await expect(restoredQuote).not.toContainText('STALE')
  await expectNoPageOverflow(page)

  expect(browserMessages, browserMessages.join('\n')).toEqual([])
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
