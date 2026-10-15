# Guardrails — read before changing code

Each rule says **what**, and **what stops you** from breaking it. "Review" means no automatic check yet: the pull-request
checklist asks for it.

| ID | Rule | Enforced by |
|---|---|---|
| G-01 | One deployable app (modular monolith). No new services or databases without a decision note. | [Decision 0001](../decisions/0001-modular-monolith.md), review |
| G-02 | Every table has exactly one owning module, listed in [modules.md](modules.md). | `GuardrailsTest.everyTableHasExactlyOneOwner` |
| G-03 | A module's SQL touches only its own tables. Anything else goes through the owner's public service. | `GuardrailsTest.modulesOnlyQueryTheirOwnTables` |
| G-04 | Entities and repositories are used only inside their own module; no dependency cycles between modules. | `ArchitectureTest` |
| G-05 | Every schema change is a **new** Flyway migration (`V<n>__what.sql`). Never edit one that has run. | Flyway checksum check at startup, review |
| G-06 | Money is `BigDecimal` in Java and `NUMERIC` in SQL. Never `double`/`float` for amounts. | Review |
| G-07 | Every business change writes `audit_log`: who, when, before, after, reason. | `AuditService`, review (database-level history: Phase D) |
| G-08 | Dated data (RM, partner share, branch, links) changes only through `DatedHistory` / `Timeline`, never by editing `valid_from`/`valid_to` by hand. | Review, `TimelineTest` |
| G-09 | Every business rule has an ID in [business-rules.md](../product/business-rules.md) and at least one test that proves it. | `GuardrailsTest.everyBusinessRuleIsProvedByATestThatExists` |
| G-10 | No real client data in the repo: `private-data/` and `.env` are ignored; tests use made-up names and PANs. | `.gitignore`, review |
| G-11 | PAN only through `PanCipher` (encrypt, hash, mask). Never log or show a full PAN except to FINANCE/ADMIN. | Review |
| G-12 | Name and PAN cleaning live once: `Text.key / nameKey / compact`, `PanCipher.normalise / normaliseOrNull`. No copies anywhere else. | `GuardrailsTest.noNewCopiesOfSharedHelpers` |
| G-13 | `main` only moves with a green build (backend + frontend tests). | GitHub Actions `ci.yml` |
| G-14 | Ledger lines, splits and the audit log are append-only: correct by voiding / adding, never by updating. | Database triggers, `UploadIT`, `MonthLifecycleIT`, `MigrationIT` |
| G-15 | Rules the business may change (GST rate, sharing order…) are settings, not constants in code. | Review (settings screen: Phase E) |
| G-16 | Uploaded Excel is read only with `platform.excel.ExcelFile` (headers by name, exact amounts, dates, row-numbered errors). | `GuardrailsTest.onlyThePlatformExcelReaderOpensWorkbooks` |
| G-17 | Every upload is checked with `UploadedFile.of`; every background job extends `TransactionalJob` (one transaction, all or nothing). | `GuardrailsTest.importsUseTheSharedPipeline` |
| G-18 | A dated value (RM, share…) changes only through `DatedHistory.setFrom`: same value → nothing, same first day → corrected, otherwise a new row from the date; ended before its first day → removed (CANCEL). | Review, `ShareIT`, `TimelineTest` |
| G-19 | Every request and job has a **reference**: in the `X-Request-Id` header, every error answer, the job record and every log line. | `ActivityIT` |
| G-20 | Log only through SLF4J: JSON lines with requestId, user, module, action. No `System.out` / `printStackTrace`. | `GuardrailsTest.nothingPrintsToTheConsole`, `JsonLogFormatterTest` |
| G-21 | No full PAN in logs or error records (the formatter masks PAN-shaped text as a safety net); query strings are never logged. | `JsonLogFormatterTest`, `ActivityIT`, review |
| G-22 | Unexpected errors show only "something went wrong + reference"; every job type has a plain-words title for the Activity screen. | `ActivityIT`, `JobHandler.title()` (compiler) |

## Before you commit
1. `backend/mvnw-docker.sh verify` and `cd frontend && npm test` are green.
2. Changed a business rule? Update its row in `business-rules.md` and its test.
3. New table? Migration + owner row in `modules.md`.
4. A choice someone will later ask "why?" about? Add a 5-line note in `docs/decisions/`.
5. Logging something? Read [logging.md](logging.md): reference, no PANs, plain-words titles.
