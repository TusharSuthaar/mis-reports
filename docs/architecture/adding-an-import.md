# Adding a new import (a product file, a master)

Every upload in the app has the same shape. A new one (Equity, AIF, insurance…) is a **reader + rules**, not a new pipeline.

```
UploadedFile.of(...)  →  preview (reads with ExcelFile, saves nothing)  →  job: TransactionalJob.apply (all or nothing, audited)
```

| Step | Use | Do not |
|---|---|---|
| 1. Check the upload | `UploadedFile.of(file, "default.xlsx", "the X file")` in the controller | check empty / .xlsx yourself |
| 2. Keep the file | `FileStore.save(AREA, content, ".xlsx")`; read it back with `FileStore.read(AREA, token)` | invent your own token checks |
| 3. Read the sheet | `ExcelFile.open(bytes)` → `table(sheet, requiredHeaders, searchRows)` → `row.text / amount / date` | use POI directly (G-16 fails the build) |
| 4. Match people | PAN via `PanCipher.normaliseOrNull` + `ClientService`; names via `Text.nameKey` | write another name/PAN cleaner (G-12) |
| 5. Apply | a `@Component` that `extends TransactionalJob<Payload>` with a plain-words title (`super(TYPE, "Uploaded the equity file", …)`, shown on Activity); write `audit_log` inside `apply` | implement `JobHandler` (G-17) |
| 5b. Progress | in long loops call `JobProgress.current()`: `steps(n)`, then `step("Adding clients", total)` and `advance()` per item; the screen shows it with `<JobProgressBar job={job} />` | a bare "Loading…" text |
| 6. Dated master data | `DatedHistory.setFrom(...)` through the owning module's service | update `valid_from` / `valid_to` yourself (G-18) |
| 7. Money | `BigDecimal`, the source's own precision; GST rule written down as a business rule | `double` |

**Then:** add the business rules to `docs/product/business-rules.md` with their tests, and any new table to
`docs/architecture/modules.md`. Examples to copy: `FinesseBrokerageParser` (monthly file), `ShareLoadService` (master with
preview and a "from" date), `FinesseClientMasterService` (small synchronous master).
