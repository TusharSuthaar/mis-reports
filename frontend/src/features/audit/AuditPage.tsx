import { Fragment, useEffect, useState, type FormEvent } from 'react';
import { api, ApiError } from '../../api/client';
import type { AuditEntry, Page } from '../../api/types';
import { formatDateTime } from '../../format';

const PAGE_SIZE = 50;

interface Filters {
  entity: string;
  entityId: string;
  action: string;
  by: string;
  from: string; // yyyy-mm-dd, local
  to: string; // yyyy-mm-dd, local, inclusive
}

const EMPTY: Filters = { entity: '', entityId: '', action: '', by: '', from: '', to: '' };

/** Builds the query string; local dates become instants, and "to" is inclusive (sent as the next midnight). */
export function auditQuery(f: Filters, page: number): string {
  const q = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
  for (const key of ['entity', 'entityId', 'action', 'by'] as const) {
    if (f[key].trim()) q.set(key, f[key].trim());
  }
  if (f.from) q.set('from', new Date(`${f.from}T00:00:00`).toISOString());
  if (f.to) {
    const end = new Date(`${f.to}T00:00:00`);
    end.setDate(end.getDate() + 1);
    q.set('to', end.toISOString());
  }
  return q.toString();
}

export function AuditPage() {
  const [draft, setDraft] = useState<Filters>(EMPTY);
  const [filters, setFilters] = useState<Filters>(EMPTY);
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<AuditEntry> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<number | null>(null);

  useEffect(() => {
    // Only the answer for the current filters/page is shown (a slow earlier one arriving last is dropped).
    let current = true;
    setError(null);
    api<Page<AuditEntry>>(`/api/audit?${auditQuery(filters, page)}`)
      .then((d) => current && setData(d))
      .catch((e) => current && setError(e instanceof ApiError ? e.message : 'Could not load the audit log'));
    return () => {
      current = false;
    };
  }, [filters, page]);

  function onSearch(e: FormEvent) {
    e.preventDefault();
    setPage(0);
    setFilters(draft);
  }

  const field = (key: keyof Filters, label: string, type = 'text') => (
    <label>
      {label}
      <input type={type} value={draft[key]} onChange={(e) => setDraft({ ...draft, [key]: e.target.value })} />
    </label>
  );

  const lastPage = data ? Math.max(0, Math.ceil(data.total / PAGE_SIZE) - 1) : 0;

  return (
    <section>
      <h1>Audit log</h1>
      <form className="filters" onSubmit={onSearch}>
        {field('entity', 'Entity')}
        {field('entityId', 'Entity id')}
        {field('action', 'Action')}
        {field('by', 'By user')}
        {field('from', 'From', 'date')}
        {field('to', 'To', 'date')}
        <button type="submit">Search</button>
      </form>
      {error && <p className="error">{error}</p>}
      {data && data.items.length === 0 && <p className="muted">No audit entries match.</p>}
      {data && data.items.length > 0 && (
        <>
          <table>
            <thead>
              <tr>
                <th>When</th>
                <th>By</th>
                <th>Entity</th>
                <th>Id</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((a) => (
                <Fragment key={a.id}>
                  <tr className="clickable" onClick={() => setOpen(open === a.id ? null : a.id)}>
                    <td>{formatDateTime(a.at)}</td>
                    <td>{a.by}</td>
                    <td>{a.entity}</td>
                    <td>{a.entityId}</td>
                    <td>{a.action}</td>
                  </tr>
                  {open === a.id && (
                    <tr className="detail">
                      <td colSpan={5}>
                        <div className="diff">
                          <div>
                            <strong>Before</strong>
                            <pre>{a.before == null ? '—' : JSON.stringify(a.before, null, 2)}</pre>
                          </div>
                          <div>
                            <strong>After</strong>
                            <pre>{a.after == null ? '—' : JSON.stringify(a.after, null, 2)}</pre>
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
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
