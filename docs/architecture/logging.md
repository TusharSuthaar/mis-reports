# Logs, references and the Activity screen

**The one idea:** every request and every background job gets a **reference** (8 characters, e.g. `7K2MQX4P`).
The same reference is on the screen, in the error, in the job record and on every log line. Quote it and the whole
story can be found.

## Where things are recorded

| What | Where | Kept | Who reads it |
|---|---|---|---|
| Who changed which data, before → after | `audit_log` (Audit log screen) | Forever, append-only | ADMIN |
| Uploads, imports, processing — status, error, reference | `job` (Activity screen) | Forever | ADMIN, HO, FINANCE |
| Unexpected errors (500s) — message (PAN masked), reference | `error_log` (Activity screen) | `MIS_ERROR_RETENTION_DAYS` (400) | ADMIN, HO, FINANCE |
| Every log line (JSON) | Container output: `docker compose logs api` | Rotated: 10 × 20 MB per service | Support |

## A log line

```json
{"time":"2026-10-15T07:03:39Z","level":"INFO","requestId":"G3B3CTEQ","user":"asha","module":"ingestion",
 "action":"UploadController.start","logger":"access","thread":"…","message":"POST /api/uploads -> 202 in 410 ms"}
```

| Field | Meaning |
|---|---|
| `requestId` | The reference. For a job: the reference of the request that started it |
| `user` | Who (or `system`) |
| `module` / `action` | Module (package under `money.epsilon.brokerage`) and controller method, or job type |
| `jobId` | Only on job lines |
| `error` / `stack` | Only when something failed |

## How to find what happened

| You have | Do |
|---|---|
| A reference from the screen | Activity screen → Reference box. Then `docker compose logs api \| findstr 7K2MQX4P` (Windows) or `grep` |
| "Something failed yesterday" | Activity screen → *Only problems* + dates |
| "Who changed this client?" | Audit log screen → Entity `CLIENT`, Id |
| Readable (non-JSON) logs while debugging | Set `MIS_LOG_FORMAT=` (empty) in `.env` and restart `api` |

## Rules (see guardrails G-19 – G-22)
- Log with SLF4J (`LoggerFactory.getLogger(…)`); never `System.out` or `printStackTrace` (build fails).
- Never log a full PAN, a password or a file's rows. The formatter masks anything PAN-shaped anyway — that is the safety
  net, not permission. Query strings are not logged (client search can hold a PAN).
- Unexpected errors show the user only "Something went wrong — reference X"; details go to the log.
- A new background job extends `TransactionalJob` with a plain-words title (it shows on the Activity screen).
- An operational step that is not a job (save, lock, export…) shows on Activity by adding its audit key to
  `platform/activity/ActivityText`.

Code: `platform/logging` (`RequestContext`, `RequestIdFilter`, `JsonLogFormatter`), `platform/activity`,
`platform/web/ApiExceptionHandler`.
