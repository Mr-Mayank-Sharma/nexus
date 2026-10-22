#!/usr/bin/env bash
# Runs Nexus on this laptop from the .env file at the repo root.
#
#   scripts/nexus-local.sh start     build if needed, start the backend and the frontend
#   scripts/nexus-local.sh stop      stop both
#   scripts/nexus-local.sh restart   stop, rebuild, start (after changing .env or the code)
#   scripts/nexus-local.sh status    what is running
#   scripts/nexus-local.sh logs      follow the backend log
#   scripts/nexus-local.sh reset     back up the database, then empty it (asks first)
#
# Needs: Java 17, Maven, Node, Docker (PostgreSQL and Redis containers), Ollama for the local AI model.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUN_DIR="$HOME/.nexus-local"
BACKEND_DIR="$REPO_ROOT/nexus-oms-backend"
FRONTEND_DIR="$REPO_ROOT/nexus-oms-frontend"
BACKUP_DIR="$REPO_ROOT/private/backups"
mkdir -p "$RUN_DIR"

say() { printf '\033[1;34m[nexus]\033[0m %s\n' "$1"; }
warn() { printf '\033[1;33m[nexus]\033[0m %s\n' "$1"; }
fail() { printf '\033[1;31m[nexus]\033[0m %s\n' "$1"; exit 1; }

# Step 0: settings from .env.
[ -f "$REPO_ROOT/.env" ] || fail "No .env at $REPO_ROOT. Copy .env.template to .env and fill it in."
set -a
# shellcheck disable=SC1091
. "$REPO_ROOT/.env"
set +a
BACKEND_PORT="${PORT:-8080}"
FRONTEND_PORT="${NEXUS_FRONTEND_PORT:-5173}"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || true)}"

# The database server, name, user and password, from the JDBC url.
DB_HOST_PORT="$(echo "$NEXUS_DB_URL" | sed -E 's#jdbc:postgresql://([^/]+)/.*#\1#')"
DB_HOST="${DB_HOST_PORT%%:*}"
DB_PORT="${DB_HOST_PORT##*:}"
DB_NAME="$(echo "$NEXUS_DB_URL" | sed -E 's#.*/([^/?]+).*#\1#')"
psql_admin() { PGPASSWORD="$NEXUS_DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$NEXUS_DB_USERNAME" -d postgres -Atq "$@"; }

# pg_dump must not be older than the server: use the one inside the database's Docker container when there is one.
dump_database() {
  local container
  container="$(docker ps --filter "publish=$DB_PORT" --format '{{.Names}}' 2>/dev/null | head -1)"
  if [ -n "$container" ]; then
    docker exec -e PGPASSWORD="$NEXUS_DB_PASSWORD" "$container" pg_dump -U "$NEXUS_DB_USERNAME" -Fc "$1" > "$2"
  else
    PGPASSWORD="$NEXUS_DB_PASSWORD" pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$NEXUS_DB_USERNAME" -Fc -f "$2" "$1"
  fi
  [ -s "$2" ] || fail "The backup of $1 is empty; nothing was dropped."
}

running() { [ -f "$RUN_DIR/$1.pid" ] && kill -0 "$(cat "$RUN_DIR/$1.pid")" 2>/dev/null; }

check_services() {
  psql_admin -c "SELECT 1" >/dev/null 2>&1 || fail "PostgreSQL is not reachable at $DB_HOST:$DB_PORT. Start its Docker container first."
  if ! curl -s -m 3 "${NEXUS_AI_OLLAMA_BASE_URL:-http://localhost:11434}/api/tags" >/dev/null; then
    warn "Ollama is not answering at ${NEXUS_AI_OLLAMA_BASE_URL:-http://localhost:11434}: the AI assistant will not work until it runs."
  fi
}

build_backend() {
  local jar="$BACKEND_DIR/target/oms-1.0.0.jar"
  if [ "${1:-}" = "force" ] || [ ! -f "$jar" ] || [ -n "$(find "$BACKEND_DIR/src/main" -newer "$jar" -type f -print -quit)" ]; then
    say "Building the backend..."
    (cd "$BACKEND_DIR" && mvn -B -q -o package -DskipTests 2>/dev/null || mvn -B -q package -DskipTests) || fail "Backend build failed"
  fi
}

start() {
  check_services
  if running backend; then
    say "Backend already running on http://localhost:$BACKEND_PORT"
  else
    build_backend
    say "Starting the backend on port $BACKEND_PORT..."
    nohup "$JAVA_HOME/bin/java" -jar "$BACKEND_DIR/target/oms-1.0.0.jar" --server.port="$BACKEND_PORT" \
      --app.cors.allowed-origins="http://localhost:$FRONTEND_PORT" > "$RUN_DIR/backend.log" 2>&1 &
    echo $! > "$RUN_DIR/backend.pid"
    for _ in $(seq 1 90); do
      sleep 2
      if curl -s -o /dev/null -w '%{http_code}' "http://localhost:$BACKEND_PORT/api/v1/actuator/health" | grep -q 200; then break; fi
      running backend || fail "The backend stopped while starting. See $RUN_DIR/backend.log"
    done
  fi
  if running frontend; then
    say "Frontend already running on http://localhost:$FRONTEND_PORT"
  else
    [ -d "$FRONTEND_DIR/node_modules" ] || (cd "$FRONTEND_DIR" && npm install --silent)
    say "Starting the frontend on port $FRONTEND_PORT..."
    (cd "$FRONTEND_DIR" && NEXUS_API_URL="http://localhost:$BACKEND_PORT" nohup npx vite --port "$FRONTEND_PORT" --strictPort \
      > "$RUN_DIR/frontend.log" 2>&1 & echo $! > "$RUN_DIR/frontend.pid")
  fi
  say "Nexus is up: http://localhost:$FRONTEND_PORT  (sign in as ${NEXUS_ADMIN_USERNAME:-admin}; the password is NEXUS_ADMIN_PASSWORD in .env)"
}

stop() {
  for service in frontend backend; do
    if running "$service"; then
      kill "$(cat "$RUN_DIR/$service.pid")" 2>/dev/null || true
      say "Stopped the $service"
    fi
    rm -f "$RUN_DIR/$service.pid"
  done
}

status() {
  for service in backend frontend; do
    if running "$service"; then say "$service: running (pid $(cat "$RUN_DIR/$service.pid"))"; else say "$service: stopped"; fi
  done
}

reset() {
  # Step 1: ask, because this removes every company's data.
  local company_databases
  company_databases="$(psql_admin -c "SELECT datname FROM pg_database WHERE datname LIKE 'nexus_t_%'" | tr '\n' ' ')"
  warn "This empties Nexus: database $DB_NAME and the company databases: ${company_databases:-none}"
  read -r -p "Type reset to continue: " answer
  [ "$answer" = "reset" ] || fail "Nothing changed."
  stop

  # Step 2: a backup of everything first.
  mkdir -p "$BACKUP_DIR"
  local stamp; stamp="$(date +%Y%m%d-%H%M%S)"
  for database in "$DB_NAME" $company_databases; do
    dump_database "$database" "$BACKUP_DIR/$database-$stamp.dump"
    say "Backed up $database to private/backups/$database-$stamp.dump"
  done

  # Step 3: drop and create empty. The schema is created again on the next start.
  for database in $company_databases "$DB_NAME"; do
    psql_admin -c "DROP DATABASE IF EXISTS \"$database\" WITH (FORCE)"
  done
  psql_admin -c "CREATE DATABASE \"$DB_NAME\""
  say "Database $DB_NAME is empty. Run: scripts/nexus-local.sh start"
}

case "${1:-}" in
  start) start ;;
  stop) stop ;;
  restart) stop; build_backend force; start ;;
  status) status ;;
  logs) tail -f "$RUN_DIR/backend.log" ;;
  reset) reset ;;
  *) sed -n '2,10p' "$0"; exit 1 ;;
esac
