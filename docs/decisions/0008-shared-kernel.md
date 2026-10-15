# 0008 · One way to do each common job — 2026-10-15

**Decision:** the common jobs live once, in `platform`, and every module uses them: Excel reading (`ExcelFile`), upload
checks (`UploadedFile`), background work (`TransactionalJob`), file tokens (`FileStore`), name and PAN matching (`Text`,
`PanCipher`), dated changes (`DatedHistory.setFrom`). Guardrail tests fail the build on a second copy.

**Why:** four Excel readers, five name/PAN cleaners, five job classes and two "change or correct" copies had grown —
each a place where a fix could be made in one copy and missed in the others. New products (Equity, AIF…) now plug in.

**See:** [adding-an-import.md](../architecture/adding-an-import.md), guardrails G-12, G-16, G-17, G-18.
