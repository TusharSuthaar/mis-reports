# 0002 · Finesse is the source — 2026-10-12

**Decision:** MF brokerage comes from the Finesse "MF Brokerage" export (sheet *Client Wise Summary*); PANs come from the
Finesse *ClientMasterReport*. August 2026 is redone with Finesse. The Investwell parser stays in the code but is not offered.

**Why:** the business moved to Finesse; its brokerage file carries only client codes and net amounts.

**Facts:** Brokerage Amount is net of GST (GST = 18%); the title month is when money arrived, the period columns are the
brokerage month; RM/Branch columns read "Suspense" and are ignored.
