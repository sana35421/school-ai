import puppeteer from 'puppeteer-core'

// Try Edge first, then Chrome
const browserPaths = [
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
]

import fs from 'fs'
let browserPath = null
for (const p of browserPaths) {
  if (fs.existsSync(p)) {
    browserPath = p
    break
  }
}
if (!browserPath) {
  console.error('No Chrome/Edge found')
  process.exit(1)
}
console.log('Using browser:', browserPath)

const browser = await puppeteer.launch({
  executablePath: browserPath,
  headless: 'new',
  args: ['--no-sandbox', '--disable-setuid-sandbox'],
})

try {
  const page = await browser.newPage()

  // Capture console
  page.on('console', (msg) => {
    console.log(`[BROWSER ${msg.type()}]`, msg.text())
  })
  page.on('pageerror', (err) => {
    console.log('[PAGE ERROR]', err.message)
  })
  page.on('requestfailed', (req) => {
    console.log('[REQ FAILED]', req.url(), req.failure()?.errorText)
  })
  page.on('response', (resp) => {
    if (resp.url().includes('localhost')) {
      console.log(`[${resp.status()}] ${resp.url()}`)
    }
  })

  console.log('=== 1. Open login page ===')
  await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle0', timeout: 15000 })

  console.log('=== 2. Type credentials ===')
  await page.type('input[autocomplete="username"]', '20220101')
  await page.type('input[autocomplete="current-password"]', '123456')

  console.log('=== 3. Click login ===')
  await page.click('button[type="submit"]')

  console.log('=== 4. Wait 5s and check URL ===')
  await new Promise((r) => setTimeout(r, 5000))
  console.log('Current URL:', page.url())

  console.log('=== 5. Check localStorage ===')
  const ls = await page.evaluate(() => ({
    token: localStorage.getItem('token'),
    user: localStorage.getItem('user'),
  }))
  console.log('localStorage:', JSON.stringify(ls, null, 2))

  console.log('=== 6. Page HTML excerpt ===')
  const body = await page.evaluate(() => document.body.innerText.slice(0, 500))
  console.log(body)
} finally {
  await browser.close()
}