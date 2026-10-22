# 0007 · Code only in Git — 2026-10-15

**Decision:** the repository holds code and docs only. Client files live in `private-data/` (git-ignored); keys and
passwords in `.env` (git-ignored); tests use made-up names and PANs. PANs in the database are AES-encrypted.

**Why:** client PANs and brokerage are personal and commercial data; the user's instruction: "no data should go on GitHub,
only codebase".

**Must do:** back up `.env` (MIS_PAN_KEY, MIS_PAN_HASH_KEY) somewhere safe — without them stored PANs cannot be read.

**Update 2026-10-22 — demo data for work away from the office computer:** `demo-data/` holds an invented month in the
real files' layouts (generator `tools/demo-data/generate.py`, loader `tools/demo-data/seed.py`). It is committed on
purpose: invented surnames, "Demo" firm names, random amounts, `@example.com` emails and PANs with X as the 4th letter (never a
real holder type). Cloud sessions use only this; real files are never uploaded there, anonymised or not (amounts,
partner and RM names would still be confidential, and clients can be re-identified from them).
