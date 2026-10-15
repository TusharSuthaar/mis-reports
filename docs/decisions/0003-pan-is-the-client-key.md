# 0003 · A client is a PAN — 2026-10-14

**Decision:** clients are matched and reported by PAN, one row per PAN (all Finesse codes of a person add up).
1. Own PAN → that PAN only; not in Master MIS → highlighted (guardian not used).
2. No own PAN → guardian PAN.
3. Still nothing → exact name among Master MIS clients without a PAN (only if unique).
4. Otherwise highlighted; the month cannot be processed.

**Why:** names are spelt differently across files; PAN is the only stable key. The user chose "own PAN must be right"
over "fall back to guardian" so wrong PANs get fixed instead of hidden.

**Rules:** R-01 … R-04 in [business-rules.md](../product/business-rules.md).
