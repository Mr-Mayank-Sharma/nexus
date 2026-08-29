#!/usr/bin/env node
/**
 * AI platform end-to-end lifecycle test.
 *
 * Verifies: dashboard health -> model create -> champion deploy (gate passes)
 * -> bad challenger blocked (422) -> forced override -> ramp ladder ->
 * rollback -> versions listing -> registry summary -> gateway predict.
 * Also asserts the gate-override audit stamp when running against a backend
 * that has the audit columns (skips gracefully otherwise).
 *
 * Usage:
 *   node scripts/e2e/ai-flow.mjs
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
  return cond;
}

(async () => {
  const login = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PASS }),
  });
  if (!login.ok) { console.error(`💥 login failed: HTTP ${login.status}`); process.exit(2); }
  token = (await login.json()).data.accessToken;
  console.log('🔐 authenticated\n');

  // 1. Dashboard health
  let r = await api('GET', '/ai');
  check('GET /ai dashboard', r.status === 200 && r.data?.data?.status === 'ok', `status=${r.status}`);

  // 2. Create model
  r = await api('POST', '/ai/models', {
    name: `e2e-gate-test-${Date.now()}`, displayName: 'E2E Gate Test Model',
    modelType: 'demand_forecast', category: 'FORECASTING', status: 'ACTIVE', isActive: true,
    createdBy: 'e2e-test',
  });
  const okCreate = r.status === 200 && r.data?.data?.id;
  if (!check('POST /ai/models (create)', okCreate, `status=${r.status} ${okCreate ? '' : JSON.stringify(r.data).slice(0, 150)}`)) process.exit(1);
  const modelId = r.data.data.id;
  console.log(`   modelId=${modelId}\n`);

  // 3. Champion v1 - good metrics; absolute gates only (no champion yet)
  r = await api('POST', `/ai/models/${modelId}/versions`, {
    version: 'v1.0.0', framework: 'sklearn',
    metrics: JSON.stringify({ wape: 0.15, mase: 0.80 }),
  });
  const v1 = r.data?.data?.id;
  check('create champion v1 (wape=0.15, mase=0.80)', !!v1, `status=${r.status}`);

  r = await api('POST', `/ai/models/${modelId}/deploy/${v1}?environment=PRODUCTION`);
  check('deploy v1 → gate PASSES (no champion yet)', r.status === 200 && r.data?.data?.status === 'ACTIVE',
    `status=${r.status}`);

  // 4. Challenger v2 - bad metrics; must be BLOCKED
  r = await api('POST', `/ai/models/${modelId}/versions`, {
    version: 'v2.0.0-bad', framework: 'sklearn',
    metrics: JSON.stringify({ wape: 0.95, mase: 1.50 }),
  });
  const v2 = r.data?.data?.id;
  check('create challenger v2 (wape=0.95, mase=1.50)', !!v2, `status=${r.status}`);

  r = await api('POST', `/ai/models/${modelId}/deploy/${v2}?environment=PRODUCTION`);
  check('deploy v2 → gate BLOCKS (expect 422)', r.status === 422,
    `status=${r.status} msg=${String(r.data?.message || '').slice(0, 140)}`);

  // 5. Force override
  r = await api('POST', `/ai/models/${modelId}/deploy/${v2}?environment=PRODUCTION&force=true`);
  check('deploy v2 ?force=true → overrides gate', r.status === 200 && r.data?.data?.status === 'ACTIVE',
    `status=${r.status}`);

  // 5b. Audit stamp: forced version must be distinguishable from clean pass
  r = await api('GET', `/ai/models/${modelId}/versions`);
  const v2detail = (r.data?.data || []).find(v => v.version === 'v2.0.0-bad');
  if (v2detail && 'gateOverride' in v2detail) {
    check('audit: v2 flagged gateOverride=true', v2detail.gateOverride === true || v2detail.gateOverride === 'true',
      `gateOverride=${v2detail.gateOverride}`);
    const v1detail = (r.data?.data || []).find(v => v.version === 'v1.0.0');
    check('audit: clean v1 NOT flagged', !(v1detail?.gateOverride === true || v1detail?.gateOverride === 'true'),
      `gateOverride=${v1detail?.gateOverride}`);
  } else {
    console.log('⏭️  SKIP | audit flag check (backend lacks gateOverride column)');
  }

  // 6. Ramp
  r = await api('POST', `/ai/models/${modelId}/ramp/${v2}`);
  check('ramp v2 traffic', r.status === 200, `status=${r.status} weight=${r.data?.data?.trafficWeight}`);

  // 7. Rollback TO champion
  r = await api('POST', `/ai/models/${modelId}/rollback/${v1}`);
  check('rollback → re-activate champion v1', r.status === 200, `status=${r.status}`);

  // 8-9. Listings
  r = await api('GET', `/ai/models/${modelId}/versions`);
  check('list versions', r.status === 200 && Array.isArray(r.data?.data) && r.data.data.length >= 2,
    `count=${r.data?.data?.length}`);
  r = await api('GET', '/ai/models/summary');
  check('GET /ai/models/summary', r.status === 200, `status=${r.status}`);

  // 10. Gateway predict (may legitimately fall back to rules - just must not fail)
  r = await api('POST', '/ai/predict/demand_forecast', { sku: 'SKU-001', horizonDays: 14 });
  check('POST /ai/predict/demand_forecast', r.status === 200 && r.data?.data != null,
    `status=${r.status} method=${r.data?.data?.method} fallback=${r.data?.data?.fallbackReason || 'none'}`);

  console.log(`\n${failures === 0 ? '🎉 ALL CHECKS PASSED' : '⚠️  ' + failures + ' FAILURE(S)'}`);
  process.exit(failures === 0 ? 0 : 1);
})().catch(e => { console.error('💥 script error:', e.message); process.exit(2); });
