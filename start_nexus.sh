#!/bin/bash
# NexusShip - Complete Startup Script
# Starts all 5 services in order: Infra → AI Models → Backend → Frontend

set -e

PROJECT_ROOT="/home/mayanksharma/Desktop/Nexus"
JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
PATH="$JAVA_HOME/bin:/tmp/apache-maven-3.9.6/bin:$PATH"
AI_ENV="/home/mayanksharma/ai-env/bin/activate"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() { echo -e "${GREEN}[NEXUS]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
err() { echo -e "${RED}[ERROR]${NC} $1"; }

cleanup() {
    log "Shutting down NexusShip..."
    kill $AI_PID 2>/dev/null || true
    kill $AI2_PID 2>/dev/null || true
    kill $BACKEND_PID 2>/dev/null || true
    kill $FRONTEND_PID 2>/dev/null || true
    log "All services stopped."
    exit 0
}
trap cleanup SIGINT SIGTERM

# Step 1: Start Infrastructure (Docker)
log "Starting infrastructure (Redis, Kafka)..."
cd "$PROJECT_ROOT"
docker compose up -d redis kafka 2>&1 || docker-compose up -d redis kafka 2>&1

log "Waiting for Redis..."
for i in $(seq 1 15); do
    if docker exec nexus-redis redis-cli ping 2>/dev/null | grep -q PONG; then
        log "Redis is ready!"
        break
    fi
    sleep 1
done

log "Waiting for Kafka..."
for i in $(seq 1 30); do
    if docker exec nexus-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list 2>/dev/null > /dev/null; then
        log "Kafka is ready!"
        break
    fi
    sleep 1
done

# Step 2: Ensure PostgreSQL database exists
log "Ensuring database..."
sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='nexus_oms'" 2>/dev/null | grep -q 1 || sudo -u postgres psql -c "CREATE DATABASE nexus_oms;" 2>/dev/null || warn "Database setup skipped (run manually if first time)"

# Step 3: Start AI Model APIs
log "Starting AI Models API (Port 5000)..."
source "$AI_ENV"
cd "$PROJECT_ROOT/supply_chain_ai/api"
nohup python api_server.py > /tmp/nexus-ai.log 2>&1 &
AI_PID=$!
sleep 3

log "Starting AI Intelligence API (Port 5001)..."
cd "$PROJECT_ROOT/supply_chain_ai2/api"
nohup python api_demand_inventory.py > /tmp/nexus-ai2.log 2>&1 &
AI2_PID=$!
sleep 3

# Verify AI APIs
for i in 1 2 3 4 5; do
    if curl -s http://localhost:5000/api/health > /dev/null 2>&1; then
        log "AI Ops API is ready!"
        break
    fi
    sleep 2
done

for i in 1 2 3 4 5; do
    if curl -s http://localhost:5001/api/health-extended > /dev/null 2>&1; then
        log "AI Intel API is ready!"
        break
    fi
    sleep 2
done

# Step 4: Start Spring Boot Backend
log "Starting Spring Boot Backend (Port 8080)..."
cd "$PROJECT_ROOT/nexus-oms-backend"
export JAVA_HOME JAVA_OPTS="-Xmx512m"
nohup mvn spring-boot:run -q > /tmp/nexus-backend.log 2>&1 &
BACKEND_PID=$!

log "Waiting for backend (up to 60s)..."
for i in $(seq 1 30); do
    if curl -s http://localhost:8080/api/v1/actuator/health > /dev/null 2>&1; then
        log "Backend is ready!"
        break
    fi
    sleep 2
done

# Step 5: Start React Frontend
log "Starting React Frontend (Port 3000)..."
cd "$PROJECT_ROOT/nexus-oms-frontend"
nohup npx vite --port 3000 --host > /tmp/nexus-frontend.log 2>&1 &
FRONTEND_PID=$!
sleep 3

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  NexusShip - Supply Chain Command Center${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "  AI Ops API:      ${GREEN}http://localhost:5000/api/health${NC}"
echo -e "  AI Intel API:    ${GREEN}http://localhost:5001/api/health-extended${NC}"
echo -e "  Backend API:     ${GREEN}http://localhost:8080/api/v1/actuator/health${NC}"
echo -e "  Swagger UI:      ${GREEN}http://localhost:8080/api/v1/swagger-ui.html${NC}"
echo -e "  Frontend:        ${GREEN}http://localhost:3000${NC}"
echo -e ""
echo -e "  Login:           admin / nexus_admin_2026"
echo -e "  Press CTRL+C to stop all services"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"

# Wait for any process to exit
wait
