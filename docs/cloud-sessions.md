# Working in Claude Code cloud sessions

Cloud sessions run Claude Code on a VM in the cloud (from claude.ai/code, the Claude app, or the Desktop app's
**Cloud** option), so work goes on while this computer is off. They see **only what is in Git**: the code, the docs and
the invented month in [demo-data/](../demo-data/README.md). Real client files stay in `private-data/` on the office
computer ([decision 0007](decisions/0007-no-real-data-in-the-repo.md)).

## One-time set-up (on claude.ai)

1. **Connect GitHub**: claude.ai/code → connect your GitHub account and install the **Claude** GitHub App on
   `TusharSuthaar/mis-reports` (it is private, so the app must be installed on it).
2. **Create an environment**: when asked (or Settings → Cloud environments), keep **Trusted** network access (it reaches
   Maven Central, npm and Docker Hub, which the build needs). No environment variables or setup script are needed —
   the repository sets itself up (below). Never put real data or real PAN keys in the environment.
3. Start a session on `mis-reports`, branch `main`.

## What happens in each session (automatic)

[.claude/settings.json](../.claude/settings.json) runs [tools/cloud/session-start.sh](../tools/cloud/session-start.sh)
when a session starts. On this computer it does nothing; in the cloud it:

- installs the frontend packages,
- starts Docker (the backend integration tests start PostgreSQL in a container),
- starts the app in the background with [tools/cloud/start-app.sh](../tools/cloud/start-app.sh): the VM's own
  PostgreSQL 16, the API built with Maven, the screens with Vite, and the demo month loaded and processed.
  It is ready a few minutes after the session starts (first build) — log `/tmp/mis-start.log`.

| | |
|---|---|
| Screens | http://localhost:5180 — sign in `demo` / `Sandbox@2026` (a throwaway VM; demo data only) |
| API | http://localhost:8090 |
| Backend tests | `cd backend && mvn -B verify` |
| Frontend tests | `cd frontend && npm test` |
| Restart after backend changes | `tools/cloud/start-app.sh` |

## What stays on this computer

- Loading the real monthly files and checking the result against the real END file.
- Timings on real volumes.

A typical loop: a cloud session builds and tests a change on demo data and pushes a branch; here you pull it, run it on
the real month, and merge.
