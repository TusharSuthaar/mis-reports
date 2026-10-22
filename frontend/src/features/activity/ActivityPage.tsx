import { useEffect, useState, type FormEvent } from 'react';
import { api, errorText } from '../../api/client';
import type { Activity, ActivityKind, ActivitySummary, Page } from '../../api/types';
import { formatDateTime } from '../../format';

const PAGE_SIZE = 50;

interface Filters {
  kind: '' | ActivityKind;
  problemsOnly: boolean;
  by: string;
  reference: string;
  from: string; // yyyy-mm-dd, local
  to: string; // yyyy-mm-dd, local, inclusive
}

const EMPTY: Filters = { kind: '', problemsOnly: false, by: '', reference: '', from: '', to: '' };

const KINDS: { value: Filters['kind']; label: string }[] = [
  { value: '', label: 'Everything' },
  { value: 'JOB', label: 'Uploads, imports and processing' },
  { value: 'EVENT', label: 'Saves, locks and downloads' },
  { value: 'ERROR', label: 'Unexpected errors' },
];

const RESULT_TEXT: Record<Activity['result'], string> = {
  WAITING: 'Waiting',
  RUNNING: 'Running',
  DONE: 'Done',
  FAILED: 'Failed',
};

/** Query string; local dates become instants and "to" is inclusive (sent as the next midnight). */
export function activityQuery(f: Filters, page: number): string {
  const q = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
  if (f.kind) q.set('kind', f.kind);
  if (f.problemsOnly) q.set('problemsOnly', 'true');
  if (f.by.trim()) q.set('by', f.by.trim());
  if (f.reference.trim()) q.set('reference', f.reference.trim());
  if (f.from) q.set('from', new Date(`${f.from}T00:00:00`).toISOString());
  if (f.to) {
    const end = new Date(`${f.to}T00:00:00`);
    end.setDate(end.getDate() + 1);
    q.set('to', end.toISOString());
  }
  return q.toString();
}

/** How long a job took, in words ("12 s", "3 min"). */
export function took(a: Pick<Activity, 'at' | 'finishedAt'>): string {
  if (!a.finishedAt) return '';
  const s = Math.max(0, Math.round((new Date(a.finishedAt).getTime() - new Date(a.at).getTime()) / 1000));
  return s < 90 ? `${s} s` : `${Math.round(s / 60)} min`;
}

/** What happened, who did it, did it work — uploads, processing, locks, downloads and errors in one list. */
export function ActivityPage() {
  const [draft, setDraft] = useState<Filters>(EMPTY);
  const [filters, setFilters] = useState<Filters>(EMPTY);
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<Activity> | null>(null);
  const [summary, setSummary] = useState<ActivitySummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshed, setRefreshed] = useState(0);

  useEffect(() => {
    // Only the answer for the current filters/page is shown (a slow earlier one arriving last is dropped).
    let current = true;
    setError(null);
    api<Page<Activity>>(`/api/activity?${activityQuery(filters, page)}`)
      .then((d) => current && setData(d))
      .catch((e) => current && setError(errorText(e)));
    api<ActivitySummary>('/api/activity/summary')
      .then((d) => current && setSummary(d))
      .catch(() => current && setSummary(null));
    return () => {
      current = false;
    };
  }, [filters, page, refreshed]);

  function onSearch(e: FormEvent) {
    e.preventDefault();
    setPage(0);
    setFilters(draft);
  }

  function showProblems() {
    const next = { ...EMPTY, problemsOnly: true };
    setDraft(next);
    setPage(0);
    setFilters(next);
  }

  const lastPage = data ? Math.max(0, Math.ceil(data.total / PAGE_SIZE) - 1) : 0;

  return (
    <section>
      <div className="page-head">
        <h1>Activity</h1>
        <button type="button" className="secondary" onClick={() => setRefreshed((n) => n + 1)}>
          Refresh
        </button>
      </div>
      <p className="muted">
        Everything that was uploaded, processed, saved, locked or downloaded, and anything that went wrong. When something
        fails, the <strong>reference</strong> is what the support team needs: the same reference is on every log line.
      </p>

      {summary && (
        <div className="stat-row">
          <div className="stat">
            <span>Running now</span>
            <strong>{summary.runningJobs}</strong>
          </div>
          <button
            type="button"
            className={`stat stat-button${summary.failedJobs > 0 ? ' danger' : ''}`}
            onClick={showProblems}
            title="Show only what failed"
          >
            <span>Failed jobs, last 7 days</span>
            <strong>{summary.failedJobs}</strong>
          </button>
          <button
            type="button"
            className={`stat stat-button${summary.errors > 0 ? ' danger' : ''}`}
            onClick={showProblems}
            title="Show only what failed"
          >
            <span>Unexpected errors, last 7 days</span>
            <strong>{summary.errors}</strong>
          </button>
        </div>
      )}

      <form className="filters" onSubmit={onSearch}>
        <label>
          Show
          <select value={draft.kind} onChange={(e) => setDraft({ ...draft, kind: e.target.value as Filters['kind'] })}>
            {KINDS.map((k) => (
              <option key={k.value} value={k.value}>
                {k.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Who
          <input value={draft.by} onChange={(e) => setDraft({ ...draft, by: e.target.value })} placeholder="Username" />
        </label>
        <label>
          Reference
          <input
            value={draft.reference}
            onChange={(e) => setDraft({ ...draft, reference: e.target.value })}
            placeholder="e.g. 7K2MQX4P"
          />
        </label>
        <label>
          From
          <input type="date" value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })} />
        </label>
        <label>
          To
          <input type="date" value={draft.to} onChange={(e) => setDraft({ ...draft, to: e.target.value })} />
        </label>
        <label className="check">
          <input
            type="checkbox"
            checked={draft.problemsOnly}
            onChange={(e) => setDraft({ ...draft, problemsOnly: e.target.checked })}
          />
          Only problems
        </label>
        <button type="submit">Search</button>
      </form>

      {error && <p className="error">{error}</p>}
      {data && data.items.length === 0 && <p className="muted">Nothing matches.</p>}
      {data && data.items.length > 0 && (
        <>
          <table>
            <thead>
              <tr>
                <th>When</th>
                <th>What</th>
                <th>Who</th>
                <th>Result</th>
                <th>Reference</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((a) => (
                <tr key={`${a.kind}-${a.id}`}>
                  <td>{formatDateTime(a.at)}</td>
                  <td>
                    {a.title}
                    {a.detail && (
                      <div className={a.result === 'FAILED' ? 'error' : 'muted'}>{a.detail}</div>
                    )}
                  </td>
                  <td>{a.by}</td>
                  <td>
                    <span className={`badge${a.result === 'FAILED' ? ' fail-badge' : a.result === 'DONE' ? '' : ' warn-badge'}`}>
                      {RESULT_TEXT[a.result]}
                    </span>
                    {took(a) && <div className="muted">{took(a)}</div>}
                  </td>
                  <td className="mono">{a.reference ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="pager">
            <button type="button" className="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
              Previous
            </button>
            <span>
              Page {page + 1} of {lastPage + 1} · {data.total} entries
            </span>
            <button type="button" className="secondary" disabled={page >= lastPage} onClick={() => setPage(page + 1)}>
              Next
            </button>
          </div>
        </>
      )}
    </section>
  );
}
