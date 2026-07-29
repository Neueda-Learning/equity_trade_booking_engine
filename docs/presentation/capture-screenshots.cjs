const { chromium } = require('../../frontend/node_modules/playwright')
const path = require('path')

async function main() {
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1,
  })
  const output = (name) => path.join(__dirname, 'assets', name)

  await page.goto('http://localhost:3100', { waitUntil: 'networkidle' })
  await page.getByRole('heading', { name: 'Dashboard' }).waitFor()
  await page.getByRole('combobox', { name: 'Account', exact: true })
    .selectOption({ label: 'Demo Growth' })
  await page.waitForTimeout(700)
  await page.screenshot({ path: output('dashboard.png') })

  for (const [label, filename, heading] of [
    ['Accounts', 'accounts.png', 'Accounts'],
    ['Activity', 'activity.png', 'Book a trade'],
    ['Market Data', 'market-data.png', 'Market Data'],
  ]) {
    await page.getByRole('button', { name: label, exact: true }).click()
    await page.getByRole('heading', { name: heading, exact: true }).waitFor()
    await page.waitForTimeout(500)
    await page.screenshot({ path: output(filename) })
  }

  await browser.close()
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
