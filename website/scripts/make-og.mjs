// Renders public/og.png (1200x630), the image link previews show. Re-run after a name change: node scripts/make-og.mjs
import sharp from 'sharp';
import { readFileSync } from 'node:fs';

const cfg = readFileSync(new URL('../src/site.config.ts', import.meta.url), 'utf8');
const name = (cfg.match(/name:\s*'([^']+)'/) ?? [, 'Nexus'])[1];

const dots = [];
for (let i = 0; i < 520; i++) {
  const y = 1 - (i / 519) * 2, r = Math.sqrt(1 - y * y), t = i * 2.39996;
  const x = Math.cos(t) * r, z = Math.sin(t) * r;
  if (z < -0.1) continue;
  dots.push(`<circle cx="${(900 + x * 300).toFixed(1)}" cy="${(330 + y * 300).toFixed(1)}" r="${(1.2 + z * 1.6).toFixed(2)}" fill="#8fb0ff" fill-opacity="${(0.25 + z * 0.6).toFixed(2)}"/>`);
}
const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630">
  <defs>
    <radialGradient id="g" cx="0.75" cy="0.5" r="0.6"><stop offset="0" stop-color="#1b2a55"/><stop offset="1" stop-color="#05070d"/></radialGradient>
    <linearGradient id="rim" x1="0" x2="1"><stop offset="0" stop-color="#5b8cff"/><stop offset="1" stop-color="#ff8a4c"/></linearGradient>
  </defs>
  <rect width="1200" height="630" fill="url(#g)"/>
  <circle cx="900" cy="330" r="302" fill="none" stroke="url(#rim)" stroke-width="3" stroke-opacity=".7"/>
  ${dots.join('')}
  <path d="M760 420 Q 860 250 1010 300" fill="none" stroke="#ff8a4c" stroke-width="3"/>
  <circle cx="760" cy="420" r="9" fill="#ff8a4c"/><circle cx="1010" cy="300" r="7" fill="#fff"/>
  <text x="72" y="110" font-family="Helvetica, Arial, sans-serif" font-size="30" font-weight="700" letter-spacing="3" fill="#e8edf7">${name.toUpperCase()}</text>
  <text x="72" y="300" font-family="Helvetica, Arial, sans-serif" font-size="78" font-weight="800" fill="#fff">ONE BRAIN</text>
  <text x="72" y="385" font-family="Helvetica, Arial, sans-serif" font-size="78" font-weight="800" fill="#fff">FOR EVERY ORDER</text>
  <text x="72" y="470" font-family="Helvetica, Arial, sans-serif" font-size="26" fill="#8b97ad">Order orchestration for Shopify retailers with stores</text>
  <text x="72" y="560" font-family="Menlo, monospace" font-size="18" letter-spacing="2" fill="#5b8cff">STOCK PER LOCATION · FAILURE-AWARE ROUTING · WHAT-IF</text>
</svg>`;
await sharp(Buffer.from(svg)).png().toFile(new URL('../public/og.png', import.meta.url).pathname);
console.log('public/og.png written');
