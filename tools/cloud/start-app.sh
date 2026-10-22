#!/usr/bin/env bash
# Runs the whole app inside a Claude Code cloud session (or any Linux box with Java 21, Maven, Node 22, PostgreSQL 16)
# without Docker, and loads the demo month (demo-data/) when the database is empty. Real client data is never here:
# cloud sessions only ever see the made-up files in demo-data/.
#
#   tools/cloud/start-app.sh          start (or restart) everything; safe to run again
#   tools/cloud/start-app.sh stop     stop the API and the screens
#
# API http://localhost:8090 · screens http://localhost:5180 · sign in demo / Sandbox@2026
# Logs: /tmp/mis-api.log, /tmp/mis-ui.log, /tmp/mis-seed.log
set -euo pipefail
cd "$(dirname "$0")/../.."
ROOT="$(pwd)"
API_LOG=/tmp/mis-api.log
UI_LOG=/tmp/mis-ui.log

stop() {
  pkill -f 'brokerage-.*\.jar' 2>/dev/null || true
  pkill -f 'vite.*--port 5180' 2>/dev/null || true
}
if [ "${1:-}" = "stop" ]; then stop; echo "stopped"; exit 0; fi

# 1. Settings: a throwaway .env with fresh keys and the demo admin (only if there is none).
if [ ! -f .env ]; then
  cat > .env <<EOF
MIS_BOOTSTRAP_ADMIN_USERNAME=demo
MIS_BOOTSTRAP_ADMIN_PASSWORD=Sandbox@2026
MIS_PAN_KEY=$(openssl rand -base64 32)
MIS_PAN_HASH_KEY=$(openssl rand -base64 32)
EOF
  echo "wrote .env (demo admin, fresh PAN keys)"
fi
set -a; . ./.env; set +a

# 2. Database: the VM's own PostgreSQL 16, database mis / user mis (superuser: Flyway creates extensions).
if ! pg_isready -q -h localhost 2>/dev/null; then
  service postgresql start >/dev/null
  for _ in $(seq 30); do pg_isready -q -h localhost && break; sleep 1; done
fi
psql_admin() { su postgres -c "psql -v ON_ERROR_STOP=1 -qtAc \"$1\""; }
[ "$(psql_admin "SELECT 1 FROM pg_roles WHERE rolname='mis'")" = "1" ] || psql_admin "CREATE ROLE mis LOGIN SUPERUSER PASSWORD 'mis'"
[ "$(psql_admin "SELECT 1 FROM pg_database WHERE datname='mis'")" = "1" ] || psql_admin "CREATE DATABASE mis OWNER mis"

# 3. API: build the jar when the code is newer than it, then run it.
stop
JAR="$(ls backend/target/brokerage-*.jar 2>/dev/null | grep -v original | head -1 || true)"
if [ -z "$JAR" ] || [ -n "$(find backend/src backend/pom.xml -newer "$JAR" -type f -print -quit)" ]; then
  echo "building the API (first time takes a few minutes)..."
  (cd backend && mvn -B -q -DskipTests package)
  JAR="$(ls backend/target/brokerage-*.jar | grep -v original | head -1)"
fi
mkdir -p /tmp/mis-files
MIS_DB_URL=jdbc:postgresql://localhost:5432/mis MIS_DB_USER=mis MIS_DB_PASSWORD=mis MIS_STORAGE_DIR=/tmp/mis-files \
  SERVER_PORT=8090 MIS_LOG_FORMAT= nohup java -jar "$JAR" > "$API_LOG" 2>&1 < /dev/null &
echo -n "starting the API"
for _ in $(seq 120); do
  curl -sf http://localhost:8090/api/health >/dev/null 2>&1 && break
  if ! pgrep -f 'brokerage-.*\.jar' >/dev/null; then echo; echo "API stopped; last lines of $API_LOG:"; tail -20 "$API_LOG"; exit 1; fi
  echo -n "."; sleep 2
done
curl -sf http://localhost:8090/api/health >/dev/null || { echo; echo "API not answering; see $API_LOG"; exit 1; }
echo " up"

# 4. Demo data, only into an empty app (seed.py checks and refuses otherwise).
if python3 tools/demo-data/seed.py > /tmp/mis-seed.log 2>&1; then
  echo "demo month loaded and processed (details in /tmp/mis-seed.log)"
else
  grep -q "already has clients" /tmp/mis-seed.log && echo "demo data already loaded" || { echo "seed failed:"; tail -5 /tmp/mis-seed.log; }
fi

# 5. Screens: Vite dev server, /api proxied to the API.
# Fully detached (own output, no terminal), so whoever ran this script gets its prompt back.
(cd frontend && { [ -d node_modules ] || npm ci --no-audit --no-fund; } && exec npx vite --host 0.0.0.0 --port 5180) > "$UI_LOG" 2>&1 < /dev/null &
for _ in $(seq 30); do curl -sf http://localhost:5180 >/dev/null 2>&1 && break; sleep 1; done
echo "screens: http://localhost:5180  (sign in $MIS_BOOTSTRAP_ADMIN_USERNAME)"
