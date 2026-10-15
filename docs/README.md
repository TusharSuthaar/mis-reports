# Start here

**Epsilon Brokerage & MIS** — monthly brokerage in, partner sharing and RM/branch reporting out, END file for finance.
One app (Java + React + PostgreSQL), run with `docker compose up -d --build` (see the repo [README](../README.md)).

| I want to… | Read | Time |
|---|---|---|
| Understand what the system does, in business words | [product/business-rules.md](product/business-rules.md) | 5 min |
| Know a term (PAN, guardian PAN, SB, RF, period…) | [product/glossary.md](product/glossary.md) | 2 min |
| Find where code lives and which module owns what | [architecture/modules.md](architecture/modules.md) | 4 min |
| Change code without breaking something elsewhere | [architecture/guardrails.md](architecture/guardrails.md) | 4 min |
| Add a new file import (a new product, a new master) | [architecture/adding-an-import.md](architecture/adding-an-import.md) | 3 min |
| Find out what happened / what failed (logs, references, Activity) | [architecture/logging.md](architecture/logging.md) | 3 min |
| Know *why* something is the way it is | [decisions/](decisions/) — one page each | 1 min each |

## The rules for these docs
- One page, one job. If a page needs scrolling twice, split it.
- Tables and lists, not paragraphs. Examples beat explanations.
- **How** lives in the docs; **why** lives in a decision note; **proof** lives in a test.
- `business-rules.md` and `architecture/modules.md` are read by tests (`GuardrailsTest`): if they drift from the code, the build fails.
- Real client data never goes here (or anywhere in the repo).
