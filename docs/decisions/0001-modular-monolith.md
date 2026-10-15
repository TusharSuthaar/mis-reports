# 0001 · One app, split into modules — 2026-10-15

**Decision:** a modular monolith: one Spring Boot app and one PostgreSQL database, divided into modules that own their tables
and talk only through each other's public services ([modules.md](../architecture/modules.md)).

**Why:** money must be saved all-or-nothing (one database transaction); one developer must be able to run and change it;
volumes are thousands of rows a month. Isolation comes from module boundaries checked by tests, not from separate deployments.

**Rejected:** microservices — network failures between services, distributed transactions for every month, many
deployments to run; it would make the system harder, not easier, to hand over.

**Revisit when:** several teams release independently, or one module needs very different scaling. Then that module (clean
boundary already) can be split out.
