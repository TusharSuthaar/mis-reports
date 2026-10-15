# 0005 · Split order — 2026-10-08

**Decision:** sub broker takes its % of **net**; the referral partner takes its % of **what remains**; Epsilon keeps the
rest. Partner amounts are rounded to paise; Epsilon takes the rounding so every line adds back to its net.

**Why:** the business rule as stated: "from 100% the sub broker gets his 50%, from the other 50% the referral partner gets
his 20%, rest is Epsilon".

**Config:** `mis.sharing.order`, `mis.sharing.negative-net` (negative net: same %, to be revisited with insurance).
**Rules:** R-30 … R-32.
