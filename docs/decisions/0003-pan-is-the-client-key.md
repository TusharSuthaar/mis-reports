# 0003 · A client is a PAN — 2026-10-14

**Decision:** clients are matched and reported by PAN, one row per PAN (all Finesse codes of a person add up).
1. Own PAN → that PAN only; not in Master MIS → highlighted (guardian not used).
2. No own PAN → the minor's own Master MIS row: exact name among Master MIS clients without a PAN (only if unique).
3. Still nothing → guardian PAN.
3a. Neither PAN nor guardian PAN → exact name among all Master MIS clients (only if unique) (user, 2026-10-27).
4. Otherwise highlighted; the month cannot be processed.

*Changed 2026-10-27:* steps 2 and 3 swapped. The real END keeps a minor that has its own Master MIS row on that row
(no PAN, its own RM and partners), not under the guardian; the user asked for the END to match by PAN.

**Why:** names are spelt differently across files; PAN is the only stable key. The user chose "own PAN must be right"
over "fall back to guardian" so wrong PANs get fixed instead of hidden.

**Rules:** R-01 … R-04 in [business-rules.md](../product/business-rules.md).
