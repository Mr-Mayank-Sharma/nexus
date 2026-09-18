#!/bin/bash
# =============================================================================
# Nexus — One-shot setup script
# Clones the repo, creates .env, builds, and starts the full stack.
# =============================================================================
set -euo pipefail

REPO_URL="https://github.com/Mr-Mayank-Sharma/Nexus-docker.git"
DIR="${1:-Nexus-docker}"

echo "🚀 Nexus production setup"
echo "========================="

# 1. Clone if not already present
if [ ! -d "$DIR" ]; then
  echo "📦 Cloning $REPO_URL ..."
  git clone "$REPO_URL" "$DIR"
fi
cd "$DIR"

# 2. Create .env from template if missing
if [ ! -f .env ]; then
  echo "📝 Creating .env from .env.example ..."
  cp .env.example .env
  echo ""
  echo "⚠️  EDIT .env NOW and set: DB_PASSWORD, JWT_SECRET, GRAFANA_ADMIN_PASSWORD"
  echo "   Generate secrets with:  openssl rand -base64 48"
  echo ""
  read -r -p "Press Enter once you've saved .env ... " _
fi

# 3. Build & start
echo "🔨 Building images (first build takes several minutes) ..."
docker compose build

echo "🚀 Starting the full stack ..."
docker compose up -d

echo ""
echo "✅ Nexus is starting. Verify with:"
echo "   docker compose ps"
echo "   curl http://localhost:8080/api/v1/actuator/health"
echo ""
echo "   Frontend:  http://localhost"
echo "   Backend:   http://localhost:8080"
echo "   Grafana:   http://localhost:3001  (admin / your GRAFANA_ADMIN_PASSWORD)"
echo "   Prometheus:http://localhost:9090"
