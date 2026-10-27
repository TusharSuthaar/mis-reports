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
| `client` | `client`, `client_external_id`, `client_rm_assignment`, `client_former_pan` | Clients (one per PAN), their codes in source systems, RM over time, PAN search/edit | `ClientService` |
| `mapping` | `source_alias` | Remembered spellings: "this name in a file = that RM / partner" | `AliasService` |
| `sharing` | `client_partner_share`, `partner_product_share`, `branch_partner` | Which partner shares each client and each partner's % per product, over time (client exceptions on the link); branch rules (a partner for every client of a branch); Master MIS upload; partner merge | `ShareService`, `ShareLoadService`, `PartnerMergeService` |
| `ingestion` | `finesse_client`, `upload`, `staged_row` | Finesse client master; monthly brokerage upload → review → save | `UploadService`, `FinesseClientMasterService` |
| `period` | `period` | Months per product: OPEN → PROCESSED → LOCKED; locked-month guard | `PeriodService` |
| `ledger` | `brokerage_line` | Append-only brokerage lines per client per month (+ corrections) | `LedgerService` |
| `attribution` | `attribution_run`, `brokerage_split` | Splits each line: sub broker, referral, Epsilon; RM/branch snapshot | `AttributionService` |
| `reporting` | `v_line_split` | END file and reports, read from the stored split only | `EndFileService` |
| `tax` | `product_tax_rate` | Products & taxes: GST and TDS % per product, dated | `ProductTaxService` |
| `intermediary` | `intermediary`, `intermediary_terms` | Platforms investments are booked through (GoldenPi, SMC…) and their dated brokerage terms; "Platforms" on screen | `IntermediaryService` |
| `bond` | `bond_entry` | Bond deals entered on the Bonds screen, with their brokerage (gross, TDS, GST, net, invoice) | `BondService` |
| `aif` | `aif_entry`, `aif_instalment` | AIF commitments entered on the AIF screen, their drawdowns, brokerage and per-deal sharing % | `AifService` |
| `fx` | `exchange_rate` | Exchange rates by day: fetched from Frankfurter (ECB) once per day, or set by hand | `ExchangeRateService` |
| `equity` | `equity_upload`, `equity_trade` | Equity brokerage: the broker's monthly Brokerage Due report (SMC) uploaded, kept trade by trade; each client's franchise share is the month's Equity line | `EquityService` |
| `preipo` | `preipo_scrip`, `preipo_price`, `preipo_entry` | Pre-IPO (unlisted shares): scrips, each vendor's dated price list ("Available IPOs"), and buy / buyback deals with their revenue; deals feed the month's Pre-IPO lines | `PreIpoService`, `PreIpoPriceService` |
| `p2p` | `p2p_entry` | P2P investments (platform, scheme, ROI, dates) with a typed brokerage; entries with brokerage are the month's P2P lines | `P2pService` |
| `nps` | `nps_entry` | NPS investments with their revenue (POP charges × sharing %, or typed; CGST + SGST); entries with revenue are the month's NPS lines | `NpsService` |
| `pms` | `pms_investment`, `pms_payout`, `pms_fee` | PMS client investments, AMC payouts per invoice period, and each client's PMS revenue per month received (the month's PMS lines) | `PmsService`, `PmsFeeService` |
| `insurance` | `insurance_policy` | Insurance policies typed on the Insurance screen or uploaded as the month's policy sheet, mapped to clients by PAN, with expected and POSP commission; policies are the month's Insurance lines | `InsuranceService` |
| `gift` | `gift_entry` | GIFT City investments entered on the GIFT CITY screen (USD / ₹ amount, fund, transaction status) with a typed brokerage (TDS, GST, net, invoice) | `GiftService` |
| `loan` | `loan_entry` | Loan payouts entered on the Loans screen (disbursal and LAS trail payouts), with their revenue (gross, TDS, GST, net, invoice) | `LoanService` |
| `fd` | `fd_entry` | FD bookings entered on the FD screen, with their commission (gross, TDS, GST, net, invoice) | `FdService` |
| `masterimport` | — | One-off org/partner masters workbook import (uses other modules' services) | `MasterImportProcessor` |

## Allowed directions (simplified)

```
platform  ←  everyone
org  ←  partner, client, sharing, attribution, reporting
client  ←  ingestion, sharing, attribution, reporting
period / ledger  ←  ingestion, attribution, reporting
attribution  ←  ingestion (void a month's split), reporting (read-only view)
sharing (Product)  ←  tax
client, org, partner, tax  ←  fd  ←  attribution (FD lines), reporting (FD labels)
client, org, partner, tax  ←  loan  ←  attribution (loan lines), reporting (loan labels)
client, org, partner, tax  ←  pms
client, org, partner, tax  ←  nps
client, org, partner, tax  ←  p2p
client, period  ←  equity  ←  attribution (equity lines)
client, org, partner, tax  ←  gift  ←  attribution (GIFT City lines), reporting (GIFT City labels)
client, org, partner, tax, intermediary  ←  bond  ←  attribution (bond lines), reporting (bond labels)
client, org, partner, tax, intermediary  ←  aif  ←  attribution (AIF lines), reporting (AIF labels)
```
No cycles between modules (checked by `ArchitectureTest`).
