# 0009 · One reference per request, JSON logs, an Activity screen — 2026-10-15

**Decision:** every request and job gets a short reference (`X-Request-Id`), carried into every log line, every error
answer and the job record. Logs are one JSON object per line with PANs masked by the formatter. Unexpected errors are
also kept in `error_log`. The Activity screen shows jobs, operational steps and errors in plain words.

**Why:** "it failed" must lead to the cause without the original developer. One reference ties the screen, the job and
the log lines together; JSON lines can be searched and later shipped to any log tool unchanged.

**Rejected:** a log server (ELK/Grafana) now — too much to run for one office; the JSON format keeps that door open.
Keeping full request bodies — they hold client data.

**See:** [logging.md](../architecture/logging.md), guardrails G-19 – G-22.
