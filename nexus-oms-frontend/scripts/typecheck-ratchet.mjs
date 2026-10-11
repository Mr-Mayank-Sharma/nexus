#!/usr/bin/env node
/**
 * Type-check ratchet.
 *
 * The root tsconfig.json is "solution style" (files: []), so `tsc` / `tsc -p .` checks nothing; the
 * real project is tsconfig.app.json. It carries historical errors that cannot all be fixed at
 * once, so this gate records them per file in typecheck-baseline.json and fails when:
 *   - any file has more errors than its baseline, or
 *   - a file with no baseline entry has any error.
 * Fixing errors only ever lowers the baseline (run with --update to record the improvement).
 *
 *   node scripts/typecheck-ratchet.mjs            check
 *   node scripts/typecheck-ratchet.mjs --update   rewrite the baseline (refuses to raise any count)
 */
import { spawnSync } from 'node:child_process'
import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const baselinePath = join(root, 'typecheck-baseline.json')
const update = process.argv.includes('--update')

const tsc = spawnSync('npx', ['tsc', '--noEmit', '-p', 'tsconfig.app.json', '--pretty', 'false'], { cwd: root, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
const output = `${tsc.stdout ?? ''}${tsc.stderr ?? ''}`
if (tsc.error) {
  console.error('Could not run tsc:', tsc.error.message)
  process.exit(2)
}

const current = {}
for (const line of output.split('\n')) {
  const m = line.match(/^(.+?)\(\d+,\d+\): error TS\d+:/)
  if (m) current[m[1]] = (current[m[1]] ?? 0) + 1
}
const total = Object.values(current).reduce((a, b) => a + b, 0)
const hasBaseline = existsSync(baselinePath)
const baseline = hasBaseline ? JSON.parse(readFileSync(baselinePath, 'utf8')).files : {}
const baselineTotal = Object.values(baseline).reduce((a, b) => a + b, 0)

const worse = Object.entries(current).filter(([file, n]) => n > (baseline[file] ?? 0))
const better = Object.entries(baseline).filter(([file, n]) => (current[file] ?? 0) < n)

if (update) {
  if (hasBaseline && worse.length) {
    console.error('Refusing to update: these files have more errors than the baseline allows:')
    worse.forEach(([f, n]) => console.error(`  ${f}: ${baseline[f] ?? 0} -> ${n}`))
    process.exit(1)
  }
  const files = Object.fromEntries(Object.entries(current).sort(([a], [b]) => a.localeCompare(b)))
  writeFileSync(baselinePath, JSON.stringify({ note: 'Per-file TypeScript error counts for tsconfig.app.json. Only ever lower these.', files }, null, 2) + '\n')
  console.log(`Baseline updated: ${baselineTotal} -> ${total} errors in ${Object.keys(files).length} files.`)
  process.exit(0)
}

if (worse.length) {
  console.error(`Type check failed: ${worse.length} file(s) have new type errors.\n`)
  for (const [file, n] of worse) {
    console.error(`  ${file}: ${n} error(s), baseline allows ${baseline[file] ?? 0}`)
    output.split('\n').filter(l => l.startsWith(file + '(')).slice(0, 5).forEach(l => console.error('    ' + l.slice(file.length)))
  }
  console.error('\nFix the errors. (Do not raise the baseline.)')
  process.exit(1)
}
console.log(`Type check OK: ${total} error(s), none new (baseline ${baselineTotal}).`)
if (better.length) console.log(`${better.length} file(s) improved; run "npm run typecheck:update" to lock that in.`)
