## What changes, in business words

## Checklist (docs/architecture/guardrails.md)
- [ ] Backend and frontend tests green (CI)
- [ ] Business rule added or changed → its row in `docs/product/business-rules.md` and its test updated
- [ ] New table → new Flyway migration **and** an owner row in `docs/architecture/modules.md`
- [ ] No SQL on another module's tables (call its service)
- [ ] No copy of an existing helper (name/PAN cleaning, Excel reading, dated history…)
- [ ] Money as `BigDecimal` / `NUMERIC`
- [ ] Every business change writes `audit_log`
- [ ] No real client data (names, PANs, amounts) in code, tests or docs
- [ ] A choice someone will ask "why?" about → note in `docs/decisions/`
