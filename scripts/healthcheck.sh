#!/bin/bash
# =============================================================================
# Nexus — Health check
# Verifies every service in the stack is up and healthy.
# =============================================================================
set -uo pipefail

echo "🔍 Nexus health check"
echo "====================="

# 1. Container status
echo ""
echo "── Container status ──"
docker compose ps

# 2. Backend health
echo ""
echo "── Backend health ──"
if curl -sf http://localhost:8080/api/v1/actuator/health >/dev/null 2>&1; then
  echo "  ✅ Backend UP (http://localhost:8080)"
else
  echo "  ❌ Backend DOWN"
fi

# 3. Frontend
echo ""
echo "── Frontend ──"
if curl -sf http://localhost/ >/dev/null 2>&1; then
  echo "  ✅ Frontend UP (http://localhost)"
else
  echo "  ❌ Frontend DOWN"
fi

# 4. AI services
echo ""
echo "── AI services ──"
if curl -sf http://localhost:5000/api/health >/dev/null 2>&1; then
  echo "  ✅ AI Ops UP (http://localhost:5000/api/health)"
else
  echo "  ⚠️  AI Ops not responding (may be fine if AI disabled)"
fi
if curl -sf http://localhost:5001/api/health-extended >/dev/null 2>&1; then
  echo "  ✅ AI Intel UP (http://localhost:5001/api/health-extended)"
else
  echo "  ⚠️  AI Intel not responding (may be fine if AI disabled)"
fi

# 5. Monitoring
echo ""
echo "── Monitoring ──"
if curl -sf http://localhost:9090/-/healthy >/dev/null 2>&1; then
  echo "  ✅ Prometheus UP (http://localhost:9090)"
else
  echo "  ❌ Prometheus DOWN"
fi
if curl -sf http://localhost:3001/api/health >/dev/null 2>&1; then
  echo "  ✅ Grafana UP (http://localhost:3001)"
else
  echo "  ❌ Grafana DOWN"
fi

echo ""
echo "Done."
