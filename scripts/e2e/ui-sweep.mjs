#!/usr/bin/env node
/**
 * Full Playwright route sweep — captures pageerror/console.error per page.
 *
 * Exit code non-zero if any PAGE_ERROR is found (white-screen crash).
 * Console warnings are logged but don't fail the run (fixable, not crashers).
 *
 * Usage:
 *   node scripts/e2e/ui-sweep.mjs
 * Env:
 *   APP_URL    (default http://localhost:3000)
 *   API_URL    (default http://localhost:8085/api/v1)
 *   NEXUS_USER (default pipeline)
 *   NEXUS_PASS (default Pipeline@2026)
 */
import { createRequire as nodeCreateRequire } from 'module';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
const __dirname = dirname(fileURLToPath(import.meta.url));
const req = nodeCreateRequire(join(__dirname, '../../nexus-oms-frontend/package.json'));
const { chromium } = req('playwright');

const APP_URL = process.env.APP_URL || 'http://localhost:3000';
const API_URL = process.env.API_URL || 'http://localhost:8085/api/v1';
const USER = process.env.NEXUS_USER || 'pipeline';
const PASS = process.env.NEXUS_PASS || 'Pipeline@2026';

// All hash-router routes (sorted, no param/param routes)
const ROUTES = [
  '/ai', '/ai-audit', '/ai-briefing', '/ai-forecasting', '/ai-packing',
  '/ai-platform', '/ai-routing', '/analytics', '/analytics-dashboard',
  '/atp-rules', '/audit', '/automation-systems', '/b2b-portal',
  '/billing-statements', '/bopis', '/bopis-app', '/bopis-owner',
  '/brokering', '/carriers', '/client-portal', '/count', '/customers',
  '/dashboard', '/documents', '/edi', '/email-parser', '/endless-aisle',
  '/experiments', '/freight-audit', '/fulfillment', '/fulfillment-limits',
  '/import-export', '/integration-hub', '/integrations/amazon',
  '/integrations/bigcommerce', '/integrations/ebay',
  '/integrations/marketplace', '/integrations/shopify',
  '/integrations/stores', '/integrations/walmart', '/inventory',
  '/inventory/cycle-counts', '/inventory/enhanced', '/inventory/receiving',
  '/invoices', '/label-printing', '/labor-management', '/loader',
  '/manifest', '/notifications', '/order-approvals', '/order-routing',
  '/orders', '/orders/search', '/pack', '/packer', '/packing',
  '/payments', '/pick', '/pickers', '/picking', '/pre-orders',
  '/procurement', '/products', '/promotions', '/rate-cards',
  '/rate-shopping', '/receive', '/rejections', '/replenishment',
  '/report-builder', '/returns', '/returns-enhanced', '/rf',
  '/routing-rules', '/scan', '/settings', '/ship', '/shipping',
  '/slotting-optimization', '/store-dashboard', '/stores',
  '/task-queues', '/transfers', '/users', '/warehouse',
  '/warehouse-dashboard', '/wave-planning', '/workflows', '/yard-dock',
];

async function getToken() {
  const res = await fetch(`${API_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PASS }),
  });
  if (!res.ok) throw new Error(`login failed: HTTP ${res.status}`);
  return (await res.json()).data.accessToken;
}

(async () => {
  const token = await getToken();
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  await ctx.addInitScript((auth) => {
    const p = auth.permissions || [];
    localStorage.setItem('nexus_token', auth.accessToken);
    localStorage.setItem('nexus_refresh_token', auth.refreshToken);
    localStorage.setItem('nexus_user', JSON.stringify({
      id: auth.id || '', username: auth.username, email: auth.email || '',
      fullName: auth.fullName || auth.username, role: auth.role || 'ADMIN',
      permissions: p, securityGroups: auth.securityGroups || [],
    }));
    localStorage.setItem('nexus_permissions', JSON.stringify(p));
    localStorage.setItem('nexus_security_groups', JSON.stringify(auth.securityGroups || []));
  }, (await (await fetch(`${API_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PASS }),
  })).json()).data);

  const page = await ctx.newPage();
  const results = [];
  let pageErrors = 0;

  for (const route of ROUTES) {
    const routeErrors = [];
    const onPE = (e) => { routeErrors.push('PAGE_ERROR: ' + String(e).slice(0, 120)); };
    const onCE = (m) => { if (m.type() === 'error') routeErrors.push('CONSOLE_ERROR: ' + m.text().slice(0, 120)); };
    page.on('pageerror', onPE);
    page.on('console', onCE);

    try {
      await page.goto(`${APP_URL}/#${route}`, { waitUntil: 'domcontentloaded', timeout: 8000 });
      await page.waitForTimeout(2500);
      const h1 = await page.evaluate(() => document.querySelector('h1')?.textContent?.trim() || '(no h1)');
      const ok = routeErrors.length === 0;
      if (!ok) pageErrors += routeErrors.length;
      console.log(`${ok ? '✅' : '❌'} ${route.padEnd(32)} h1="${h1}" errors=${routeErrors.length}`);
      routeErrors.forEach(e => console.log(`   ${e}`));
      results.push({ route, h1, errors: routeErrors.length, ok });
    } catch (err) {
      console.log(`❌ ${route.padEnd(32)} NAVIGATION ERROR: ${err.message.slice(0, 100)}`);
      results.push({ route, h1: null, errors: 1, ok: false });
      pageErrors++;
    } finally {
      page.off('pageerror', onPE);
      page.off('console', onCE);
    }
  }

  const passCount = results.filter(r => r.ok).length;
  const totalCount = results.length;
  console.log(`\n📊 ${passCount}/${totalCount} pages clean | ${pageErrors} total errors`);

  if (pageErrors > 0) {
    const failed = results.filter(r => !r.ok).map(r => `  ${r.route}`).join('\n');
    console.log(`\n❌ FAILED ROUTES:\n${failed}`);
  }

  await browser.close();
  process.exit(pageErrors > 0 ? 1 : 0);
})().catch(e => { console.error('💥', e.message); process.exit(2); });
