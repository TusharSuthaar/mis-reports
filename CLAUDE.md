# Working on this repo (people and AI assistants)

Read [docs/README.md](docs/README.md) first, then [docs/architecture/guardrails.md](docs/architecture/guardrails.md).

## Hard rules (the build checks most of them)
1. **Modules own their tables** ([modules.md](docs/architecture/modules.md)). Never query another module's table — call its
   public service. `GuardrailsTest.modulesOnlyQueryTheirOwnTables` fails otherwise.
2. **Business rules live in [business-rules.md](docs/product/business-rules.md)**, each with a test. Change a rule → change
   its row, its code and its test together.
3. **Reuse, don't copy** (the shared kernel in `platform`): name/PAN cleaning (`Text`, `PanCipher`), Excel reading
   (`ExcelFile`), uploads (`UploadedFile`), background work (`TransactionalJob`), dated changes (`DatedHistory.setFrom`),
   audit (`AuditService`). `GuardrailsTest` fails on copies, on other Excel readers and on jobs outside the pipeline.
   New import? Follow [docs/architecture/adding-an-import.md](docs/architecture/adding-an-import.md).
4. **Schema = new Flyway migration**, never edit an applied one. **Money = BigDecimal / NUMERIC.**
5. **Audit every business change** (who, when, before, after, reason). Ledger, splits and audit log are append-only.
   **Log through SLF4J only**, never a full PAN; errors carry the request reference ([logging.md](docs/architecture/logging.md)).
6. **No real client data anywhere in the repo** — `private-data/` and `.env` stay local. Tests use made-up names and PANs.
7. **Ask before guessing a business rule.** Record the answer as a rule row (and a decision note if it is a choice).

## Commands
- `backend/mvnw-docker.sh verify` — all backend tests (needs Docker)
- `cd frontend && npm test` — frontend tests
- `docker compose up -d --build` — run the app on http://localhost:5180

## Cloud sessions (Claude Code on the web)
- **Only demo data exists here.** `private-data/` and `.env` are not in Git, so a cloud session never has real client
  files — and must never be given any. Work against [demo-data/](demo-data/README.md): an invented August 2026 in the real
  files' layouts (PANs with X as 4th letter cannot be real).
- The SessionStart hook ([tools/cloud/session-start.sh](tools/cloud/session-start.sh)) runs only in the cloud: it
  installs frontend packages, starts the Docker daemon (for Testcontainers) and starts the app with the demo month in
  the background — log `/tmp/mis-start.log`. API http://localhost:8090, screens http://localhost:5180, sign in
  `demo` / `Sandbox@2026`. Restart after backend changes: `tools/cloud/start-app.sh` (rebuilds when code changed).
- Backend tests in the cloud: `cd backend && mvn -B verify` (the VM has Java 21 + Maven; `mvnw-docker.sh` is for Windows).
- Anything that needs the real month (comparing with the real END file, loading real files, timings on real volumes)
  is done on the office computer: say so in the hand-off instead of guessing.

## Working lean (token use)
- One task per session; the docs and these rules carry the context, not a long chat.
- Test runs: show only the summary and failing tests (e.g. `grep -E "Tests run:.*(Fail|Err)|ERROR\]   "`), never whole logs.
- Read only the lines you need; don't re-read a file you just edited.
- Local helper scripts (API login/calls, real-data scan before commit) live in `private-data/tools/` (not in Git).
