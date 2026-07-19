#!/bin/bash
# Start backend + frontend with nohup (survives terminal close)

set -e

PROJECT_ROOT="/home/mayanksharma/Desktop/Nexus"
JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
PATH="$JAVA_HOME/bin:$PATH"
PID_FILE="/tmp/nexus-dev.pids"

cleanup() {
    echo "Stopping Nexus dev services..."
    if [ -f "$PID_FILE" ]; then
        while read -r pid; do
            kill "$pid" 2>/dev/null || true
        done < "$PID_FILE"
        rm -f "$PID_FILE"
    fi
    echo "Stopped."
    exit 0
}
trap cleanup SIGINT SIGTERM EXIT

echo "=== Starting Nexus Dev ==="

echo "Starting Backend (port 8080)..."
nohup java -jar "$PROJECT_ROOT/nexus-oms-backend/target/oms-1.0.0.jar" --spring.profiles.active=dev \
    > /tmp/nexus-backend.log 2>&1 &
BACKEND_PID=$!
echo "$BACKEND_PID" > "$PID_FILE"

for i in $(seq 1 30); do
    if curl -s http://localhost:8080/api/v1/actuator/health > /dev/null 2>&1; then
        echo "Backend ready!"
        break
    fi
    sleep 2
done

echo "Starting Frontend (port 3000)..."
nohup npx vite --port 3000 --host \
    > /tmp/nexus-frontend.log 2>&1 &
FRONTEND_PID=$!
echo "$FRONTEND_PID" >> "$PID_FILE"
sleep 3

echo ""
echo "=== Nexus Dev Running ==="
echo "Frontend : http://localhost:3000"
echo "Backend  : http://localhost:8080/api/v1"
echo "Swagger  : http://localhost:8080/api/v1/swagger-ui/index.html"
echo ""
echo "Logs: tail -f /tmp/nexus-backend.log | /tmp/nexus-frontend.log"
echo "Stop: kill \$(cat $PID_FILE)  or  ./start-dev.sh (auto-stops previous)"
echo ""

wait
