// Comprehensive E2E smoke test using installed Playwright
const BASE = 'http://localhost:3000';
const HASH = (path) => `${BASE}/#/${path}`;
const SCREENSHOT_DIR = '/home/mayanksharma/Desktop/Nexus/.playwright-mcp/screenshots';

const ROUTES = [
  { path: '', name: 'LaunchPad' },
  { path: 'login', name: 'Login' },
  { path: 'dashboard', name: 'Dashboard' },
  { path: 'settings', name: 'Settings' },
  { path: 'notifications', name: 'Notifications' },
  { path: 'orders', name: 'Orders' },
  { path: 'orders/new', name: 'Create Order' },
  { path: 'customers', name: 'Customers' },
  { path: 'products', name: 'Products' },
  { path: 'fulfillment', name: 'Fulfillment' },
  { path: 'task-queues', name: 'Task Queues' },
  { path: 'order-approvals', name: 'Order Approvals' },
  { path: 'fulfillment-limits', name: 'Fulfillment Limits' },
  { path: 'picking', name: 'Picking' },
  { path: 'packing', name: 'Packing' },
  { path: 'shipping', name: 'Shipping' },
  { path: 'label-printing', name: 'Label Printing' },
  { path: 'manifest', name: 'Manifest' },
  { path: 'pickers', name: 'Pickers' },
  { path: 'inventory', name: 'Inventory' },
  { path: 'inventory/enhanced', name: 'Inventory Enhanced' },
  { path: 'inventory/receiving', name: 'Receiving' },
  { path: 'inventory/cycle-counts', name: 'Cycle Counts' },
  { path: 'transfers', name: 'Transfers' },
  { path: 'replenishment', name: 'Replenishment' },
  { path: 'warehouse', name: 'Warehouse' },
  { path: 'warehouse-dashboard', name: 'Warehouse Dashboard' },
  { path: 'wave-planning', name: 'Wave Planning' },
  { path: 'labor-management', name: 'Labor Management' },
  { path: 'slotting-optimization', name: 'Slotting' },
  { path: 'yard-dock', name: 'Yard Dock' },
  { path: 'automation-systems', name: 'Automation' },
  { path: 'packer', name: 'Packer Screen' },
  { path: 'loader', name: 'Loader Screen' },
  { path: 'bopis', name: 'BOPIS' },
  { path: 'bopis-owner', name: 'BOPIS Owner' },
  { path: 'bopis-app', name: 'BOPIS App' },
  { path: 'store-dashboard', name: 'Store Dashboard' },
  { path: 'pre-orders', name: 'Pre Orders' },
  { path: 'promotions', name: 'Promotions' },
  { path: 'endless-aisle', name: 'Endless Aisle' },
  { path: 'returns', name: 'Returns' },
  { path: 'returns-enhanced', name: 'Returns Enhanced' },
  { path: 'rejections', name: 'Rejections' },
  { path: 'carriers', name: 'Carriers' },
  { path: 'rate-shopping', name: 'Rate Shopping' },
  { path: 'routing-rules', name: 'Routing Rules' },
  { path: 'order-routing', name: 'Order Routing' },
  { path: 'freight-audit', name: 'Freight Audit' },
  { path: 'integration-hub', name: 'Integration Hub' },
  { path: 'import-export', name: 'Import/Export' },
  { path: 'integrations/bigcommerce', name: 'BigCommerce' },
  { path: 'integrations/amazon', name: 'Amazon' },
  { path: 'integrations/ebay', name: 'eBay' },
  { path: 'integrations/walmart', name: 'Walmart' },
  { path: 'integrations/marketplace', name: 'Marketplace Hub' },
  { path: 'integrations/stores', name: 'Stores' },
  { path: 'edi', name: 'EDI' },
  { path: 'email-parser', name: 'Email Parser' },
  { path: 'b2b-portal', name: 'B2B Portal' },
  { path: 'procurement', name: 'Procurement' },
  { path: 'invoices', name: 'Invoicing' },
  { path: 'payments', name: 'Payments' },
  { path: 'analytics', name: 'Analytics' },
  { path: 'report-builder', name: 'Report Builder' },
  { path: 'ai', name: 'AI Overview' },
  { path: 'ai-platform', name: 'AI Platform' },
  { path: 'ai-briefing', name: 'AI Briefing' },
  { path: 'ai-routing', name: 'AI Routing' },
  { path: 'ai-packing', name: 'AI Packing' },
  { path: 'ai-loading', name: 'AI Loading' },
  { path: 'ai-audit', name: 'AI Audit Trail' },
  { path: 'ai-forecasting', name: 'AI Forecasting' },
  { path: 'experiments', name: 'Experiments' },
  { path: 'users', name: 'Users' },
  { path: 'audit', name: 'Audit Log' },
  { path: 'documents', name: 'Documents' },
  { path: 'workflows', name: 'Workflows' },
  { path: 'atp-rules', name: 'ATP Rules' },
];

async function run() {
  const playwright = require('/home/mayanksharma/Desktop/flowmind/node_modules/.pnpm/playwright@1.61.1/node_modules/playwright');
  const fs = require('fs');

  const browser = await playwright.chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();

  const consoleErrors = [];
  const pageErrors = [];
  const results = [];
  const failures = [];

  page.on('console', msg => {
    if (msg.type() === 'error') {
      consoleErrors.push({ text: msg.text() });
    }
  });
  page.on('pageerror', err => {
    pageErrors.push({ text: err.message });
  });

  // Login first
  console.log('=== LOGIN ===');
  await page.goto(HASH('login'), { waitUntil: 'networkidle', timeout: 15000 });
  await page.waitForTimeout(1000);

  // Check for auth - if localStorage has user, we're good
  const hasAuth = await page.evaluate(() => !!localStorage.getItem('nexus_user'));
  if (!hasAuth) {
    // Try to find and fill login form
    const inputs = await page.$$('input');
    console.log(`Found ${inputs.length} input fields on login page`);
    if (inputs.length >= 2) {
      await inputs[0].fill('admin@nexus.com');
      await inputs[1].fill('admin123');
      const submitBtn = await page.$('button[type="submit"]');
      if (submitBtn) await submitBtn.click();
      await page.waitForTimeout(2000);
    }
  }

  // Navigate to root (LaunchPad) after login
  await page.goto(BASE + '/', { waitUntil: 'networkidle', timeout: 15000 });
  await page.waitForTimeout(1500);

  const afterLoginUrl = page.url();
  console.log(`After login, URL: ${afterLoginUrl}`);

  // Check if we're on login page still (auth failed)
  const stillOnLogin = afterLoginUrl.includes('login');
  if (stillOnLogin) {
    console.log('Still on login page - auth may be mocked via localStorage. Setting mock user...');
    await page.evaluate(() => {
      localStorage.setItem('nexus_user', JSON.stringify({
        id: '1',
        email: 'admin@nexus.com',
        name: 'Admin User',
        role: 'ADMIN',
        tenantId: '1'
      }));
    });
    await page.goto(BASE + '/', { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1500);
  }

  console.log(`\nCurrent URL: ${page.url()}`);
  await page.screenshot({ path: `${SCREENSHOT_DIR}/00-launchpad.png` });

  // Test all routes
  console.log('\n=== TESTING ALL ROUTES ===');
  let pass = 0, fail = 0, warn = 0;

  for (let i = 0; i < ROUTES.length; i++) {
    const route = ROUTES[i];
    const url = route.path === '' ? BASE + '/' : HASH(route.path);
    const errorsBefore = consoleErrors.length;
    const pageErrBefore = pageErrors.length;

    try {
      await page.goto(url, { waitUntil: 'networkidle', timeout: 12000 });
      await page.waitForTimeout(800);

      const bodyText = await page.textContent('body') || '';
      const hasErrorBoundary = bodyText.includes('Something went wrong');
      const hasCrash = bodyText.includes('Objects are not valid') ||
                       bodyText.includes('TypeError') ||
                       bodyText.includes('Cannot read propert');

      const newConsoleErr = consoleErrors.length - errorsBefore;
      const newPageErr = pageErrors.length - pageErrBefore;
      const routePageErrors = pageErrors.slice(pageErrBefore);

      const result = {
        route: route.path || '/',
        name: route.name,
        hasErrorBoundary,
        hasCrash,
        consoleErrors: newConsoleErr,
        pageErrors: newPageErr,
        errorMessages: routePageErrors.map(e => e.text?.substring(0, 300)),
      };

      if (hasCrash) {
        result.status = 'CRASH';
        fail++;
        failures.push(result);
        await page.screenshot({ path: `${SCREENSHOT_DIR}/CRASH-${i}-${route.path.replace(/\//g, '-')}.png` });
        console.log(`  ❌ CRASH: ${route.name}`);
      } else if (hasErrorBoundary) {
        result.status = 'ERROR_BOUNDARY';
        fail++;
        failures.push(result);
        await page.screenshot({ path: `${SCREENSHOT_DIR}/ERR-${i}-${route.path.replace(/\//g, '-')}.png` });
        console.log(`  ❌ ERROR_BOUNDARY: ${route.name}`);
      } else if (newPageErr > 0) {
        result.status = 'PAGE_ERROR';
        fail++;
        failures.push(result);
        await page.screenshot({ path: `${SCREENSHOT_DIR}/ERR-${i}-${route.path.replace(/\//g, '-')}.png` });
        console.log(`  ❌ PAGE_ERROR(${newPageErr}): ${route.name}`);
      } else if (newConsoleErr > 3) {
        result.status = 'WARN';
        warn++;
        console.log(`  ⚠️  WARN(${newConsoleErr} errs): ${route.name}`);
      } else {
        result.status = 'PASS';
        pass++;
        console.log(`  ✅ ${route.name}`);
      }

      results.push(result);
    } catch (e) {
      fail++;
      const result = {
        route: route.path,
        name: route.name,
        status: 'TIMEOUT',
        errorMessage: e.message?.substring(0, 200)
      };
      failures.push(result);
      results.push(result);
      console.log(`  ❌ TIMEOUT: ${route.name} - ${e.message?.substring(0, 80)}`);
    }
  }

  // Summary
  console.log('\n========================================');
  console.log(`TOTAL: ${ROUTES.length} routes`);
  console.log(`✅ PASS: ${pass}`);
  console.log(`❌ FAIL: ${fail}`);
  console.log(`⚠️  WARN: ${warn}`);
  console.log('========================================');

  if (failures.length > 0) {
    console.log('\n=== FAILURE DETAILS ===');
    for (const f of failures) {
      console.log(`\n[${f.status}] ${f.name} (${f.route})`);
      if (f.errorMessages?.length) {
        f.errorMessages.forEach(m => console.log(`  → ${m}`));
      }
      if (f.errorMessage) console.log(`  → ${f.errorMessage}`);
    }
  }

  // Save all errors
  console.log(`\nTotal console errors across all pages: ${consoleErrors.length}`);
  console.log(`Total page errors across all pages: ${pageErrors.length}`);

  if (pageErrors.length > 0) {
    console.log('\n=== ALL PAGE ERRORS ===');
    pageErrors.forEach((e, i) => console.log(`${i + 1}. ${e.text?.substring(0, 200)}`));
  }

  fs.writeFileSync('/home/mayanksharma/Desktop/Nexus/.playwright-mcp/results.json', JSON.stringify({ results, failures, consoleErrors, pageErrors }, null, 2));

  await browser.close();
  console.log('\nDone! Results saved.');
}

run().catch(e => { console.error('FATAL:', e); process.exit(1); });
