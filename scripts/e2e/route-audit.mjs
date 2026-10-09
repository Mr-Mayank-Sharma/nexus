#!/usr/bin/env node
/**
 * Route audit: visits every static route declared in nexus-oms-frontend/src/App.tsx and reports,
 * per page, what a user would hit:
 *   - page crashes (pageerror) and console errors
 *   - failing API calls (HTTP >= 400, grouped by method + path)
 *   - placeholder copy ("coming soon", "mock", "not implemented", lorem ipsum, …)
 *   - buttons with no handler (no onclick, not a submit/link/menu trigger)
 *
 * Usage:
 *   APP_URL=http://localhost:5199 NEXUS_USER=opsdemo NEXUS_PASS='…' node scripts/e2e/route-audit.mjs [--json out.json]
 */
import { readFileSync, writeFileSync } from 'fs'
import { createRequire } from 'module'
import { dirname, join } from 'path'
import { fileURLToPath } from 'url'

const root = join(dirname(fileURLToPath(import.meta.url)), '../..')
const req = createRequire(join(root, 'nexus-oms-frontend/package.json'))
const { chromium } = req('@playwright/test')

const APP_URL = process.env.APP_URL || 'http://localhost:5173'
const USER = process.env.NEXUS_USER || 'admin'
const PASS = process.env.NEXUS_PASS || ''
const jsonOut = process.argv.includes('--json') ? process.argv[process.argv.indexOf('--json') + 1] : null

const app = readFileSync(join(root, 'nexus-oms-frontend/src/App.tsx'), 'utf8')
const routes = [...new Set([...app.matchAll(/<Route\s+path="([^"]+)"/g)].map(m => m[1]))]
  .filter(p => !p.includes(':') && !p.includes('*') && p !== 'login' && p !== '/')
  .map(p => '/' + p.replace(/^\//, ''))
  .sort()

const PLACEHOLDER = /coming soon|not (yet )?implemented|lorem ipsum|\bmock(ed)? data\b|\bdummy\b|placeholder data|sample only|under construction/i

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

await page.goto(`${APP_URL}/#/login`)
await page.getByPlaceholder('Enter your username').fill(USER)
await page.getByPlaceholder('Enter your password').fill(PASS)
await page.getByRole('button', { name: 'Sign in', exact: true }).click()
await page.waitForTimeout(2500)

const results = []
for (const route of routes) {
  const r = { route, pageErrors: [], consoleErrors: [], apiFailures: {}, placeholders: [], deadButtons: 0 }
  const onPageError = e => r.pageErrors.push(e.message.slice(0, 200))
  const onConsole = m => { if (m.type() === 'error') r.consoleErrors.push(m.text().slice(0, 200)) }
  const onResponse = res => {
    const url = res.url()
    if (!url.includes('/api/') || res.status() < 400) return
    const key = `${res.request().method()} ${res.status()} ${new URL(url).pathname.replace(/\/api\/v1/, '').replace(/[0-9a-f-]{36}/g, ':id')}`
    r.apiFailures[key] = (r.apiFailures[key] || 0) + 1
  }
  page.on('pageerror', onPageError)
  page.on('console', onConsole)
  page.on('response', onResponse)
  try {
    await page.goto(`${APP_URL}/#${route}`)
    await page.waitForTimeout(2200)
    const text = await page.locator('main').innerText().catch(() => '')
    const hits = text.split('\n').filter(l => PLACEHOLDER.test(l)).map(l => l.trim().slice(0, 120))
    r.placeholders = [...new Set(hits)].slice(0, 5)
    r.deadButtons = await page.evaluate(() => {
      const main = document.querySelector('main') || document.body
      return [...main.querySelectorAll('button')].filter(b => {
        if (b.disabled || b.type === 'submit' || b.closest('form') || b.getAttribute('aria-haspopup')) return false
        if (b.getAttribute('role') === 'tab' || b.getAttribute('aria-controls')) return false
        const props = Object.keys(b).find(k => k.startsWith('__reactProps'))
        const p = props ? b[props] : {}
        return !(p.onClick || p.onMouseDown || p.onPointerDown || b.onclick)
      }).length
    })
  } catch (e) {
    r.pageErrors.push('NAVIGATION: ' + e.message.slice(0, 160))
  }
  page.off('pageerror', onPageError)
  page.off('console', onConsole)
  page.off('response', onResponse)
  results.push(r)
}
await browser.close()

const bad = results.filter(r => r.pageErrors.length || Object.keys(r.apiFailures).length || r.placeholders.length || r.deadButtons)
console.log(`Audited ${results.length} routes; ${bad.length} with problems.\n`)
for (const r of bad) {
  console.log(r.route)
  r.pageErrors.forEach(e => console.log('  CRASH   ', e))
  Object.entries(r.apiFailures).forEach(([k, n]) => console.log('  API     ', k, n > 1 ? `x${n}` : ''))
  r.placeholders.forEach(p => console.log('  COPY    ', p))
  if (r.deadButtons) console.log('  BUTTONS ', `${r.deadButtons} without a handler`)
}
if (jsonOut) writeFileSync(jsonOut, JSON.stringify(results, null, 2))
process.exitCode = results.some(r => r.pageErrors.length) ? 1 : 0
