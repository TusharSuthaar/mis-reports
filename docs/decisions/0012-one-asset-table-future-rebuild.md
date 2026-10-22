# 0012 — Future: rebuild the database around one asset table

Date: 2026-10-22 · Status: **planned, not started** (user, 2026-10-22: "keep this in future plans of rewinding entire DB
to fix the system")

## Context
Every product got its own entry table (`fd_entry`, `bond_entry`, `aif_entry`, `loan_entry`, `gift_entry`, `p2p_entry`,
`nps_entry`, `pms_investment`, `equity_trade`), each mixing what a client holds with what Epsilon earns on it. The
schema is now 45 tables, and a client's holdings have to be pieced together from nine modules (R-09). The user finds
this too many tables and wants a simpler model, built around one holdings table, as in their wealth-platform layout
(112 columns: units, NAV, current value, returns, bond / FD / insurance details).

## Plan
The proposed schema is [docs/design/asset-schema.sql](../design/asset-schema.sql) (tested on PostgreSQL 16 over V1–V38):
- `asset`: one row per holding of one client (PAN), with value and returns as a daily snapshot. Lists, totals and the
  PAN view read it alone.
- `product` (+ `product_price`): the scheme, FD scheme, bond, fund or strategy, stored once for every holder.
- `asset_terms` (fixed income: rate, payouts, compounding, tenure, maturity), `asset_transaction` (purchases,
  drawdowns, redemptions, interest), `asset_holder` (joint holders, nominees, guardian by PAN), `asset_check` (servicing
  ticks), `asset_snapshot` (month-end value, for AUM trends).
- Masters: product type, asset class, provider, frequency, servicing check. `revenue` for commission on any product;
  `client_contact` for mobile / e-mail / address once per client. Reuses `client`, `employee`, `partner`,
  `intermediary`, `upload`.
- Designed from the fields today's screens record, not from the 112-column example (2026-10-22: "the example was
  just an example").

## Plan of work
Phases, the two ways to do it (in place vs fresh start), checks and risks: [docs/design/db-rework-plan.md](../design/db-rework-plan.md).

## To decide before starting
1. Revenue: the entry tables keep brokerage, GST, TDS and invoices and point at `asset`, or that money moves into the
   new model too (then the ledger, split and END file read from it).
2. Moving the data: a migration that copies each entry table into `asset` + details, checked total by total against
   the old tables, before any old table is dropped. Locked months and the append-only ledger must come out unchanged.
3. Where the snapshot values come from (RTA / platform files, a daily NAV job) and how often.
4. Which module owns `asset` and its masters (rule 1 in CLAUDE.md).

## Until then
Nothing changes: the entry tables, screens and code stay as they are. This is a plan to come back to.

## Rejected for now
- One 112-column table, as given: every row would carry dozens of empty FD / bond / policy columns, and scheme data
  would be repeated on every holding.
- Doing it now: it touches nine modules, the ledger and the END file; it is planned as its own piece of work.
