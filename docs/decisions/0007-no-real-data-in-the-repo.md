# 0007 · Code only in Git — 2026-10-15

**Decision:** the repository holds code and docs only. Client files live in `private-data/` (git-ignored); keys and
passwords in `.env` (git-ignored); tests use made-up names and PANs. PANs in the database are AES-encrypted.

**Why:** client PANs and brokerage are personal and commercial data; the user's instruction: "no data should go on GitHub,
only codebase".

**Must do:** back up `.env` (MIS_PAN_KEY, MIS_PAN_HASH_KEY) somewhere safe — without them stored PANs cannot be read.
