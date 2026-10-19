// Films the real Nexus app for the website (needs the app running locally, see README "Product media").
import { createRequire } from 'module';
import { readFileSync, writeFileSync, mkdirSync } from 'fs';
const { chromium } = createRequire('/Users/mayanksharma/Desktop/nexus/nexus-oms-frontend/package.json')('playwright');
const OUT = process.argv[2];
mkdirSync(`${OUT}/frames`, { recursive: true });
mkdirSync(`${OUT}/stills`, { recursive: true });
const PASS = process.env.NEXUS_PASS ?? readFileSync(process.env.PASS_FILE, 'utf8').trim();
const USER = process.env.NEXUS_USER ?? 'opsdemo';
const APP = process.env.APP_URL ?? 'http://localhost:5199';
const W = 1440, H = 900;
const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: W, height: H }, deviceScaleFactor: 2 });
await ctx.addInitScript(() => {
  localStorage.setItem('nexus-theme', 'dark');
  addEventListener('DOMContentLoaded', () => {
    const c = document.createElement('div');
    c.id = 'film-cursor';
    c.style.cssText = 'position:fixed;z-index:2147483647;left:0;top:0;width:22px;height:22px;pointer-events:none;transform:translate(-100px,-100px)';
    c.innerHTML = '<svg viewBox="0 0 24 24" width="22" height="22"><path d="M3 2l7 19 2.5-7.5L20 11z" fill="#fff" stroke="#000" stroke-width="1.4" stroke-linejoin="round"/></svg>';
    document.body.appendChild(c);
    addEventListener('mousemove', e => { c.style.transform = `translate(${e.clientX - 3}px,${e.clientY - 2}px)`; }, true);
  });
});
const page = await ctx.newPage();
await page.goto(APP + '/#/login');
await page.getByPlaceholder('Enter your username').fill(USER);
await page.getByPlaceholder('Enter your password').fill(PASS);
await page.getByRole('button', { name: 'Sign in', exact: true }).click();
await page.waitForTimeout(3000);

const cdp = await ctx.newCDPSession(page);
const frames = [];
let recording = false;
cdp.on('Page.screencastFrame', async f => {
  await cdp.send('Page.screencastFrameAck', { sessionId: f.sessionId }).catch(() => {});
  if (!recording) return;
  const i = frames.length;
  writeFileSync(`${OUT}/frames/f${String(i).padStart(5, '0')}.jpg`, Buffer.from(f.data, 'base64'));
  frames.push({ i, t: f.metadata.timestamp });
});
const marks = [];
let mx = W / 2, my = H / 2;
const move = async (x, y, steps = 32) => { await page.mouse.move(x, y, { steps }); mx = x; my = y; };
const wheel = async (dy, n = 24) => { for (let k = 0; k < n; k++) { await page.mouse.wheel(0, dy / n); await page.waitForTimeout(28); } };
// stills hide the film cursor
const still = async n => {
  await page.evaluate(() => { const c = document.getElementById('film-cursor'); if (c) c.style.visibility = 'hidden'; });
  await page.screenshot({ path: `${OUT}/stills/${n}.png` });
  await page.evaluate(() => { const c = document.getElementById('film-cursor'); if (c) c.style.visibility = 'visible'; });
};
// load each page while not recording, so the film never shows a spinner
const scene = async (route, name, wait = 3800) => {
  recording = false;
  await page.goto(APP + '/#' + route);
  await page.waitForTimeout(wait);
  await page.mouse.move(mx, my);
  await page.waitForTimeout(150);
  marks.push({ name, frame: frames.length });
  recording = true;
};

await cdp.send('Page.startScreencast', { format: 'jpeg', quality: 90, maxWidth: W, maxHeight: H, everyNthFrame: 1 });

await scene('/dashboard', 'dashboard');
await move(420, 300); await move(900, 420); await page.waitForTimeout(700); await still('dashboard');
await wheel(380); await page.waitForTimeout(800);

await scene('/integrations/stores', 'channels');
await move(330, 240); await page.waitForTimeout(400);
const shop = page.getByText(/Shopify live test/i).first();
if (await shop.count()) { const b = await shop.boundingBox(); if (b) { await move(b.x + 40, b.y + 8); await shop.click(); await page.waitForTimeout(1600); } }
await still('channels');

await scene('/fulfillment', 'fulfillment');
await move(700, 430); await move(760, 560); await page.waitForTimeout(800); await still('fulfillment');

await scene('/order-routing', 'routing');
await move(560, 480); await page.waitForTimeout(600); await still('routing');
await wheel(300); await page.waitForTimeout(600);

await scene('/order-replay', 'replay');
const sample = page.getByText(/sample data/i).first(); const sb = await sample.boundingBox();
if (sb) await move(sb.x + 30, sb.y + 8);
await sample.click(); await page.waitForTimeout(900);
const run = page.getByRole('button', { name: /run the replay/i }); const rb = await run.boundingBox();
if (rb) await move(rb.x + 40, rb.y + 12);
await page.waitForTimeout(300); await run.click(); await page.waitForTimeout(2200);
await wheel(560, 40); await page.waitForTimeout(1100); await still('replay');
await wheel(520, 40); await page.waitForTimeout(1100); await still('replay-orders');

await scene('/jobs', 'jobs');
await move(760, 330); await page.waitForTimeout(900); await still('jobs');

await scene('/store-dashboard', 'store');
await move(500, 420); await page.waitForTimeout(900); await still('store');

await scene('/transfers', 'transfers');
await move(600, 420); await page.waitForTimeout(700); await still('transfers');

recording = false;
await cdp.send('Page.stopScreencast');
writeFileSync(`${OUT}/frames.json`, JSON.stringify({ frames, marks }));
console.log(frames.length, 'frames', JSON.stringify(marks));
await browser.close();
