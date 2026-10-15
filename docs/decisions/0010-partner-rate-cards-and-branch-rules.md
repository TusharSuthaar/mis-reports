# 0010 · Partner % per product, client exceptions, branch rules — 2026-10-15

**Decision:** a partner's % is kept **per product** and dated (its rate card, from the Master MIS partner sheets or the
Partners screen). A client's link carries its own % only when it really differs (an exception, set by hand or when the
row's % is neither the partner's MF % nor its general %). A partner "under process" is PENDING and paid nothing. A
**branch rule** gives every client of a branch a partner who is paid first (one sub-broker for all clients of one
branch). Such a branch gets its own book of the RM; the RM's other rows go to his general book (R-21).

**Why:** the Master MIS states sharing per partner and product; most referral partners get 0% on MF though their client
rows carry their non-MF %. Copying row %s would have paid them on MF. The branch arrangement is written once in the
master's notes, not on each row.

**Rejected:** one % per client for everything (wrong for referral MF, and every new product would need new columns);
naming the branch partner on every row of that branch (the user prefers one rule in the app); a branch rule on the RM's
whole book (it would pay the branch partner on the RM's clients in other branches too).

**See:** rules R-21, R-53 – R-58; [business-rules.md](../product/business-rules.md).
