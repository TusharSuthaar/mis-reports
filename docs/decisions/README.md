# Decisions

One page per decision: **date · decision · why · rejected**. Add one when someone could later ask "why is it like this?".
Never edit an old one to change it — add a new one that replaces it, and link both ways.

| # | Decision | Date |
|---|---|---|
| [0001](0001-modular-monolith.md) | One app, split into modules (modular monolith), not microservices | 2026-10-15 |
| [0002](0002-finesse-replaces-investwell.md) | Finesse is the brokerage and client-master source | 2026-10-12 |
| [0003](0003-pan-is-the-client-key.md) | A client is a PAN; guardian PAN only without own PAN | 2026-10-14 |
| [0004](0004-master-mis-decides-rm-and-partners.md) | Master MIS alone decides RM and partners | 2026-10-12 |
| [0005](0005-split-order.md) | Sub broker first, referral on the rest, Epsilon keeps the remainder | 2026-10-08 |
| [0006](0006-dated-history-and-locked-months.md) | Dated history; corrections in place; locked months never change | 2026-10-09 |
| [0007](0007-no-real-data-in-the-repo.md) | Code only in Git; client data stays local and encrypted | 2026-10-15 |
| [0008](0008-shared-kernel.md) | One way to do each common job (Excel, uploads, jobs, matching, dated changes) | 2026-10-15 |
| [0009](0009-references-and-json-logs.md) | One reference per request; JSON logs with PANs masked; Activity screen | 2026-10-15 |
| [0010](0010-partner-rate-cards-and-branch-rules.md) | Partner % per product (rate card), client exceptions, pending partners, branch rules | 2026-10-15 |
| [0011](0011-other-products-fd-entry-and-tax-master.md) | Other products: FD entered by hand; GST/TDS per product in a dated master | 2026-10-19 |
| [0012](0012-one-asset-table-future-rebuild.md) | **Planned:** rebuild the database around one asset table | 2026-10-22 |
