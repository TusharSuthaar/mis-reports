# Epsilon Money — Brokerage & MIS

Replaces the manual Excel process for monthly brokerage: ingest provider income files, attribute each
client's net brokerage to partners, RM and Epsilon using effective-dated rules, keep an append-only
ledger, lock periods, and produce MIS reports and the monthly END file.

Stack: Java 21 / Spring Boot 3.5 (modular monolith), PostgreSQL 16 with Flyway, React 18 + TypeScript + Vite.

**Product knowledge and guardrails:** start at [docs/README.md](docs/README.md) — business rules (each proved by a test),
module ownership, guardrails and decision notes. Working on the code: read [CLAUDE.md](CLAUDE.md).

## Run it

Needs Docker Desktop.

```bash
cp .env.example .env    # then set the first admin and the two PAN keys (openssl rand -base64 32)
docker compose up -d --build
```

| Service | URL |
|---|---|
| App | http://localhost:5180 |
| API (direct) | http://localhost:8090/api/health |
| Postgres | localhost:5442, database `mis` |

On first start with an empty user table, the API creates the ADMIN from `MIS_BOOTSTRAP_ADMIN_*`.
After that those variables are ignored; manage users under **Users**.

## Develop

Backend: no local JDK needed, Maven runs in a container. The Docker socket is mounted so the
Testcontainers integration tests (`*IT`) can start Postgres.

```bash
backend/mvnw-docker.sh verify    # unit + integration tests
backend/mvnw-docker.sh test      # unit tests only
```

Frontend:

```bash
cd frontend && npm install
npm run dev     # http://localhost:5173, proxies /api to localhost:8090 (override with MIS_API_URL)
npm test        # Vitest
npm run build   # type-check + production build
```

## Layout

```
backend/src/main/java/money/epsilon/brokerage/
  platform/     security (session login, CSRF, roles), user, audit, job runner, web (errors, health),
                dated (effective-dated rows + history rules), crypto (PAN), storage (uploaded files), time
  org/          regions, branches, employees; branch→region, RD and RM→branch histories; org chart as of a date
  partner/      sub-brokers, referral partners, POSPs; partner↔RM links over time
  masterimport/ the masters workbook: template, all-or-nothing import, dry run
  client/       clients, their source codes (Iwell), their RM over time; change RM (one or many) from a date
  mapping/      remembered spellings: "this name in the Investwell file = that RM / partner"
  ingestion/    Investwell income-file reader (4-decimal amounts, Total-row check); first client load; monthly upload,
                review (RMs to fix, new clients, RM differences) and saving the month to the ledger
  sharing/      which partners share each client (a sub-broker and/or a referral partner), at what % of NET, over time
  period/       calendar-month periods per product (OPEN now; PROCESSED / LOCKED in Phase 5)
  ledger/       brokerage lines: one per client per month, 4 decimals, append-only (voided, never edited)
  attribution/  the split: per line, sub-broker % of net, then referral % of what is left, Epsilon the rest; hierarchy
                and names stored as they were; month Process / Lock (FINANCE) / Unlock (ADMIN); split summary
  …             product, period, attribution, ledger, reporting come in later phases
backend/src/main/resources/db/migration/   Flyway migrations (every schema change)
frontend/src/
  api/        fetch wrapper (session cookie, CSRF header) and types
  app/        auth context, route guard, shell
  features/   one folder per screen
```

Rules enforced by `ArchitectureTest`: no cycles between modules, and a module's entities and
repositories are used only inside that module. `GuardrailsTest` checks that every table has one owning module
([docs/architecture/modules.md](docs/architecture/modules.md)), that no module's SQL touches another module's tables,
and that every business rule in [docs/product/business-rules.md](docs/product/business-rules.md) names a test that exists.

## Conventions

- Money: `BigDecimal` / `NUMERIC`, never float/double.
- Schema: Flyway migrations only (`ddl-auto: validate`).
- Audit: every change to users, assignments, rules, aliases, period lock/unlock and every export is
  recorded in `audit_log` (append-only, enforced by a database trigger), in the same transaction as the change.
- Background work: implement `JobHandler`, submit with `JobService.submit`; progress at `GET /api/jobs/{id}`.
  Jobs run as the user who submitted them, so their audit records name that user.
- Effective dating: `valid_from` and `valid_to` are both inclusive; `valid_to` empty = current. History only grows at
  the end (`Timeline`): a new entry must start after the current one started, which is closed the day before.
  Postgres exclusion constraints make overlaps impossible even outside the app.
- PAN: AES-256-GCM at rest plus a keyed hash for lookups (`PanCipher`). Full PAN only for FINANCE/ADMIN;
  everyone else, and the audit log, sees `ABCDE****F`. **Back up the PAN keys**: without them stored PANs are unreadable.
- Real client data (Finesse files, Master MIS, END files) goes in `private-data/`, which is git-ignored. No real names, PANs or amounts in code, tests or docs.

## Build status

| Phase | Scope | Status |
|---|---|---|
| 0 | Foundations: skeleton, Flyway, auth/roles, audit log, job runner, React shell | Done |
| 1 | Masters: region, branch, employee, RD, partners, dated assignments, bulk import | Done |
| 2 | Bootstrap from the Investwell file | Done (Aug 2026 loaded; 88 RM DELTA clients wait for their book) |
| 3 | Sharing rules: sub-broker and/or referral % per client from the client master (no RM share) | Done |
| 4 | Monthly MF upload: review, fix, save the month to the ledger | Done |
| 5 | Split (attribution), month Process → Lock → Unlock, locked-month guard, corrections as adjustments | Done |
| 6 | MIS reports, END file, row-level security | |
| 7 | Parallel run | |
