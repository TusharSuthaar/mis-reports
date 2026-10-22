# Database rework plan — one holdings model

Status: **future plan, not started.** Nothing here is built yet; each phase starts only when chosen. Why: [decision 0012](../decisions/0012-one-asset-table-future-rebuild.md).
Target schema: [asset-schema.sql](asset-schema.sql).

## What changes, in one table

| | Today | After |
|---|---|---|
| A client's holdings | 9 entry tables, one per product (`fd_entry` … `pms_investment`) | `asset` (+ `asset_terms` for fixed income, `asset_transaction`, `asset_holder`, `asset_check`, `asset_snapshot`), one row per holding per PAN |
| The security held | typed into each entry (FD company, bond name, fund) | `product` + `product_price`, stored once |
| Commission, GST, TDS, invoice | columns on each entry table | `revenue`, one row per commission earned, any product |
| Where a ledger line came from | 6 nullable columns on `brokerage_line` | one `revenue_id` |
| Backend modules for products | `fd`, `bond`, `aif`, `loan`, `gift`, `p2p`, `nps`, `pms` | `holding` (assets, products, masters) and `revenue` |
| Screens | one per product | one holding form with a section per product type; per-product Excel exports kept |
| A new product | new table, module, screen, ledger column | new `product_type` row and products |

**Table count:** 45 today → 50 (10 entry tables removed; 5 masters, 8 holdings tables, `revenue` and `client_contact`
added); columns 677 → 502. Mobile, e-mail and address move to `client_contact`, servicing ticks become rows in
`asset_check`. Left out until a screen needs them: goals, ARN / DP masters, insurance policy details. Checked by
building the whole after-state on PostgreSQL 16.

## Stays as it is
Clients and PANs (`client`, `client_external_id`, `client_former_pan`, `client_rm_assignment`), org, partners, sharing,
`source_alias`, Finesse master, uploads and staged rows, `period`, `brokerage_line`, `attribution_run`,
`brokerage_split`, audit, jobs, sessions, tax and FX masters, `equity_upload` / `equity_trade` (the broker's file, kept
trade by trade). The ledger, splits and audit log stay append-only.

## Two ways to do it — decide first

| | A. In place (recommended while real months exist) | B. Fresh start |
|---|---|---|
| How | New tables next to the old, copy, switch reads, then drop the old (phases 2–6) | New baseline schema; reload everything from source files and registers |
| Keeps | Every saved, processed and locked month, audit history | Only what the source files can rebuild |
| Loses | Nothing | Hand-typed entries not in a file, audit history, locked months as they were sent |
| Effort / risk | Larger, low risk: old and new run side by side | Smaller, but only safe if the live data can be thrown away |
| Flyway | New migrations V40+ | History reset (breaks the "never edit an applied migration" rule) |

B is only acceptable if the office confirms nothing in the live database is needed beyond what files can reload.

## Phases (path A)

Each phase is its own branch, migration and tests; the app works after every phase.

| # | Phase | Migration | Done when |
|---|---|---|---|
| 0 | **Decide** the 5 open points below | — | answers recorded as rule rows / decision notes |
| 1 | **Masters**: product types, asset classes, providers, frequencies, servicing checks; Masters → Asset masters screen | V39 | masters editable, audited, retire-not-delete |
| 2 | **Holdings tables**: `product`, `product_price`, `asset`, `asset_terms`, `asset_transaction`, `asset_holder`, `asset_check`, `asset_snapshot`, `client_contact`; new `holding` module | V40 | empty tables, service + API, guardrail and architecture tests pass |
| 3 | **Backfill holdings**: products from distinct FD companies / bonds / funds; one asset per entry, with `legacy_product` + `legacy_id`; AIF instalments → `asset_transaction` | V41 + one-off job | reconciliation report: count and amount per product per month, old = new to the rupee |
| 4 | **Reads switch**: client Holdings tab, product lists and Excel exports read `asset`; entry screens write both (dual write) | — | same screens, same exports (compare files), all tests green |
| 5 | **Revenue**: `revenue` table, backfilled from the entry tables; new ledger lines carry `revenue_id`; attribution and END file read one revenue service | V42 | END file for **every** past month identical before/after (byte compare); locked months untouched |
| 6 | **One entry screen** writes `asset` + `revenue` only; dual write ends | — | old entry screens removed |
| 7 | **Retire**: entry tables renamed `legacy_*`, read-only, then dropped after two locked months with no differences; 8 product modules removed; docs and rules updated | V43, V44 | 10 tables and 8 modules gone; `modules.md`, `business-rules.md` match |

**Old ledger lines are never updated** (append-only): they keep their old `*_entry_id`, and a mapping
(`revenue.legacy_product` + `legacy_id`) lets reports resolve both old and new lines. The six old columns go only when
no line uses them, which may be never for locked months — then they stay, unused by new code.

## Open points (phase 0)
1. **Path A or B**: does the live database hold months or entries that must be kept? (Recommended: A.)
2. **Revenue**: one `revenue` table for all products (recommended), or commission stays with each holding.
3. **Values and returns** on `asset` (current value, XIRR): from which files (RTA, platform, Finesse) and how often.
   Until decided, they stay empty and lists show cost.
4. **Ownership**: `holding` owns asset, product and masters; `revenue` owns revenue; `ledger` unchanged. Confirm.
5. **MF and equity**: do MF folios and demat holdings become `asset` rows too (needs an RTA / holdings file), or only
   the hand-entered products for now?

## Risks and how they are handled
| Risk | Guard |
|---|---|
| A past END file changes | phase 5 compares every month's END file byte for byte before switching |
| Totals drift during the copy | reconciliation report per product per month; the switch waits for zero differences |
| Locked months edited | the period guard already refuses it; backfills only insert |
| Real data in tests | demo data only (rule 6); the real-month comparison runs on the office computer |
| Long freeze | dual write keeps every screen working; each phase ships alone |

## Rough size
Phases 1–2: days. Phase 3: a week with the reconciliation. Phases 4–5: the bulk (exports, attribution, END file).
Phases 6–7: a week. Each needs a run on the office computer against the real database before it is merged.
