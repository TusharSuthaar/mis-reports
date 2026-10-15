# Modules and who owns what

A **modular monolith** ([decision 0001](../decisions/0001-modular-monolith.md)): one app, one database, split into modules
under `backend/src/main/java/money/epsilon/brokerage/<module>/`. Each module **owns** its tables. Another module never
reads or writes those tables with SQL — it calls the owner's public service.

> **This table is checked by `GuardrailsTest`.** Add a table in a migration → add it here. A module's SQL touches a table
> it does not own → the build fails.

| Module | Owns tables | Job | Public entry point |
|---|---|---|---|
| `platform` | `app_user`, `audit_log`, `job`, `error_log`, `spring_session`, `spring_session_attributes` | Shared kernel: login, roles, users, audit log, background jobs, request IDs + JSON logs, activity, error log, PAN crypto, dated history, Excel reading, upload/import pipeline, file storage, errors | `AuditService`, `JobService`, `PanCipher`, `DatedHistory`, `Text`, `ExcelFile`, `UploadedFile`, `TransactionalJob`, `FileStore`, `RequestContext`, `ActivityService` |
| `org` | `region`, `branch`, `employee`, `branch_region_assignment`, `rd_assignment`, `rm_branch_assignment` | Regions, branches, RMs/RDs and their history; org chart on any date | `EmployeeService`, `OrgChartService` |
| `partner` | `partner`, `partner_rm_link` | Sub brokers, referral partners, POSPs; partner↔RM links | `PartnerService`, `PartnerRmLinkService` |
| `client` | `client`, `client_external_id`, `client_rm_assignment` | Clients (one per PAN), their codes in source systems, RM over time, PAN search/edit | `ClientService` |
| `mapping` | `source_alias` | Remembered spellings: "this name in a file = that RM / partner" | `AliasService` |
| `sharing` | `client_partner_share`, `partner_product_share`, `branch_partner` | Which partner shares each client and each partner's % per product, over time (client exceptions on the link); branch rules (a partner for every client of a branch); Master MIS upload; partner merge | `ShareService`, `ShareLoadService`, `PartnerMergeService` |
| `ingestion` | `finesse_client`, `upload`, `staged_row` | Finesse client master; monthly brokerage upload → review → save | `UploadService`, `FinesseClientMasterService` |
| `period` | `period` | Months per product: OPEN → PROCESSED → LOCKED; locked-month guard | `PeriodService` |
| `ledger` | `brokerage_line` | Append-only brokerage lines per client per month (+ corrections) | `LedgerService` |
| `attribution` | `attribution_run`, `brokerage_split` | Splits each line: sub broker, referral, Epsilon; RM/branch snapshot | `AttributionService` |
| `reporting` | `v_line_split` | END file and reports, read from the stored split only | `EndFileService` |
| `masterimport` | — | One-off org/partner masters workbook import (uses other modules' services) | `MasterImportProcessor` |

## Allowed directions (simplified)

```
platform  ←  everyone
org  ←  partner, client, sharing, attribution, reporting
client  ←  ingestion, sharing, attribution, reporting
period / ledger  ←  ingestion, attribution, reporting
attribution  ←  ingestion (void a month's split), reporting (read-only view)
```
No cycles between modules (checked by `ArchitectureTest`).
