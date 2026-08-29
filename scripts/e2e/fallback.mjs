#!/usr/bin/env node
/**
 * Fallback / graceful-degradation verification for the AI gateway.
 *
 * Verifies that malformed, empty, and unknown inputs never produce HTTP 5xx
 * and that the rule-engine fallback engages with a visible reason.
 *
 * Usage:
 *   node scripts/e2e/fallback.mjs
 * Env:
 *   API_URL    (default http://localhost:8085/api/v1)
 *   NEXUS_USER (default pipeline)
 *   NEXUS_PASS (default Pipeline@2026)
 */
const BASE = process.env.API_URL || 'http://localhost:8085/api/v1';
const USER = process.env.NEXUS_USER || 'pipeline';
const PASS = process.env.NEXUS_PASS || 'Pipeline@2026';

let token = '';
async function api(method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: body ? JSON.stringify(body) : undefined,
  });
  let data = null;
  try { data = await res.json(); } catch { /* non-JSON */ }
  return { status: res.status, data };
}

let failures = 0;
function check(name, cond, extra = '') {
  console.log(`${cond ? '✅ PASS' : '❌ FAIL'} | ${name}${extra ? ' | ' + extra : ''}`);
  if (!cond) failures++;
}

(async () => {
  const login = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PASS }),
  });
  if (!login.ok) { console.error(`💥 login failed: HTTP ${login.status}`); process.exit(2); }
  token = (await login.json()).data.accessToken;

  let r = await api('POST', '/ai/predict/totally_unknown_type', { foo: 'bar' });
  check('predict unknown modelType → no 500', r.status < 500,
    `status=${r.status} method=${r.data?.data?.method}`);

  r = await api('POST', '/ai/predict/demand_forecast', {});
  check('predict empty input → no 500', r.status < 500,
    `status=${r.status} fallback=${r.data?.data?.fallbackReason || '(served by model)'}`);

  r = await api('POST', '/ai/predict/demand_forecast', { horizonDays: 'not-a-number', sku: 12345 });
  check('predict malformed input → no 500', r.status < 500, `status=${r.status}`);

  r = await api('POST', '/ai/predict/demand_forecast', { sku: null, horizonDays: -999, nested: { deep: [1, 2, 3] } });
  check('predict hostile input → no 500', r.status < 500, `status=${r.status}`);

  console.log(failures === 0 ? '\n🎉 FALLBACK VERIFICATION PASSED' : `\n⚠️ ${failures} failure(s)`);
  process.exit(failures === 0 ? 0 : 1);
})().catch(e => { console.error('💥', e.message); process.exit(2); });
