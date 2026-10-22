#!/usr/bin/env bash
# SessionStart hook (.claude/settings.json). Does nothing on your own computer; in a Claude Code cloud session it gets the
# VM ready: frontend packages, the Docker daemon for the Testcontainers tests, and the app with the demo month running in
# the background (log: /tmp/mis-start.log). Real client data never reaches the cloud: only demo-data/ is used.
set -uo pipefail
[ "${CLAUDE_CODE_REMOTE:-}" = "true" ] || exit 0
cd "$(dirname "$0")/../.."

# Frontend packages (needed by npm test / build), only when missing or the lock file changed.
if [ ! -d frontend/node_modules ] || [ frontend/package-lock.json -nt frontend/node_modules ]; then
  (cd frontend && npm ci --no-audit --no-fund >/dev/null 2>&1 && touch node_modules) || echo "npm ci failed; run it in frontend/"
fi

# Docker daemon, for the backend integration tests (Testcontainers starts PostgreSQL in a container).
if ! docker info >/dev/null 2>&1; then
  (dockerd > /tmp/dockerd.log 2>&1 &)
fi

# The app with demo data, in the background so the session is not held up.
nohup tools/cloud/start-app.sh > /tmp/mis-start.log 2>&1 &
echo "Cloud session: app starting with the DEMO month in the background (tail /tmp/mis-start.log)." \
     "Screens http://localhost:5180 (demo / Sandbox@2026), API http://localhost:8090. Backend tests: cd backend && mvn -B verify."
exit 0
