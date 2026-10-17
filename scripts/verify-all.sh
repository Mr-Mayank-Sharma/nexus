#!/usr/bin/env bash
# One command that does what CI would: backend tests, frontend lint/types/tests/build, docs checks.
# CI is switched off (billing), so run this before every push:   scripts/verify-all.sh
# Optional hook:   git config core.hooksPath scripts/hooks   (runs the fast checks on every push)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || true)}"

echo "== backend (clean verify; needs the nexus-rls-test and nexus-twin-redis containers up)"
(cd "$ROOT/nexus-oms-backend" && mvn -B -q clean verify)

echo "== frontend"
cd "$ROOT/nexus-oms-frontend"
npm run lint --silent
npm run typecheck --silent
npm test --silent -- --run
npm run build --silent

echo "== docs"
cd "$ROOT" && python3 scripts/verify_docs.py

echo "== secrets in the staged/committed diff"
if git diff HEAD --name-only | xargs -I{} grep -IlE "shpat_[0-9a-f]{20,}|BEGIN (RSA|OPENSSH) PRIVATE KEY" {} 2>/dev/null | grep -q .; then
  echo "A secret-looking value is in the working tree. Remove it before pushing."; exit 1
fi
echo "ALL CHECKS PASSED"
