# Demo data (made up — safe to share)

A complete, **invented** month (August 2026) in the exact layouts of the real files, so the app can be run, tested and
developed anywhere — Claude Code cloud sessions included — without real client data. Real files stay in
`private-data/` on the office computer and never go to Git or the cloud ([decision 0007](../docs/decisions/0007-no-real-data-in-the-repo.md)).

| File | Real counterpart | Loaded on |
|---|---|---|
| `masters.xlsx` | org list (regions, branches, RMs, RDs) | Setup → Import |
| `ClientMasterReport.xlsx` | Finesse ClientMasterReport (Ops) | Data uploads → Finesse client master |
| `Master_MIS.xlsx` | Master MIS (partner sheets + Master sheet) | Data uploads → Master MIS |
| `MFBrokerage_Aug2026.xlsx` | Finesse MFBrokerage export | Month close → Finesse MF brokerage (2026-08) |
| `Equity_Brokerage_Aug2026.xlsx` | broker's Brokerage Due report | Products → Equity |
| `products.json` | FD / Bonds / AIF / Loans / GIFT / PMS / NPS / P2P registers | entered through the API by `seed.py` |

**What is in it:** 5 regions, 9 branches + HEAD OFFICE (house book), 20 RMs; 6 sub-brokers and 20 referral partners with product
rate cards (one partner pending approval); 2,400 clients (794 with a partner, a few with their own %, a few with a
sub-broker at 0% *and* a referral partner); 25 minors matched through the guardian's PAN; clients with two Finesse codes;
codes paid in two periods; a few negative (reversal) amounts; 4-decimal amounts; 43 equity clients / 251 trades; 40
product entries.

**Why it can't be real:** first names are common ones but every surname is invented (syllable pairs), firm names carry the word "Demo", amounts are random, emails are `@example.com`, phones
start `90000`, and every PAN has **X as its 4th letter** — the 4th letter of a real PAN is the holder type (P, C, H, F,
A, T, B, L, J, G), never X.

## Use it

```bash
tools/cloud/start-app.sh                 # cloud session / Linux: runs the app and loads this month (no Docker needed)
python tools/demo-data/seed.py           # or load it into any EMPTY running app (refuses if clients exist)
python tools/demo-data/generate.py       # re-create the files (pip install openpyxl); same seed → same files
```

Expected after loading (processed August 2026): 2,333 MF client lines, MF net ₹28,22,667.46; equity 251 trades,
franchise share ₹3,60,189.40; month net ₹36,77,916.18 → partners ₹4,25,241.82, Epsilon ₹32,52,674.36.

Change the generator, not the files by hand, so they stay consistent; keep the layouts in step with the real files
(column headers only — never copy real values).
