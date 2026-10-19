# 0011 — Other products: FD entered by hand; GST/TDS per product in a dated master

Date: 2026-10-19 · Status: accepted (entry only; FD calculations later)

## Context
Phase 2 adds products other than MF, starting with FD. FDs are not in a monthly file: each FD booked is entered by
hand, with the fields of the user's FD mapping sheet. GST and TDS differ by product and may change over time.

## Decision
- New module `tax` (`product_tax_rate`): GST %, TDS %, "TDS by default" per product, dated like every other master
  (change from a date, correct from the first day, locked months guarded). An entry with no rate for its month is
  refused rather than guessed (R-71). Starting rows: MF and FD GST 18% from go-live, TDS 0 (set on the screen).
- New module `fd` (`fd_entry`): one row per FD. The commission is worked out on every save — gross = amount × margin,
  TDS on gross when ticked, GST on gross, net = gross − TDS, invoice = gross + GST — and stored with the % used (R-70).
  Entries are edited (payment status, invoice raised) and cancelled, never deleted; all audited.
- A new client typed on the FD screen is added through the Clients service (one client per PAN, R-01) with the FD's RM.

## Deferred (user, 2026-10-19: "figured out later when we do FD calculations")
FD lines in the ledger / process / split / END FD column, what partners share for FD, and
whether MF's GST moves onto the tax master (MF still uses its fixed 18% in the Finesse parser).

## Update 2026-10-19
Maturity value is now worked out (R-72): cumulative compounds as chosen per FD (default quarterly); payout FDs show the
principal plus all simple interest (user's choice).
