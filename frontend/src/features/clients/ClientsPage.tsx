import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../../api/client';
import { PARTNER_TYPE_LABEL, type ClientDetail, type ClientRow, type Employee, type HistoryEntry, type Page, type Partner, type Share } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { DatedChangeForm } from '../../components/DatedChangeForm';
import { HistoryTable } from '../../components/HistoryTable';
import { SidePanel } from '../../components/SidePanel';
import { formatDate, todayIso } from '../../format';

const PAGE_SIZE = 50;

export function ClientsPage() {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const [urlParams] = useSearchParams();
  const [search, setSearch] = useState(urlParams.get('q') ?? '');
  const [query, setQuery] = useState(urlParams.get('q') ?? '');
  const [needsRm, setNeedsRm] = useState(false);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [open, setOpen] = useState<number | null>(null);
  const employees = useLoad<Employee[]>('/api/employees');
  const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE), needsRm: String(needsRm) });
  if (query) params.set('q', query);
  const clients = useLoad<Page<ClientRow>>(`/api/clients?${params}`);
  const ids = clients.data?.items.map((c) => c.id).join(',') ?? '';
  const shares = useLoad<Record<string, Share[]>>(ids ? `/api/shares?clientIds=${ids}` : null);

  // search as you type, without a request per keystroke
  useEffect(() => {
    const t = window.setTimeout(() => {
      setPage(0);
      setQuery(search.trim());
    }, 300);
    return () => window.clearTimeout(t);
  }, [search]);

  const close = useCallback(() => setOpen(null), []);
  const rms = (employees.data ?? []).filter((e) => e.status === 'ACTIVE');
  const data = clients.data;
  const lastPage = data ? Math.max(0, Math.ceil(data.total / PAGE_SIZE) - 1) : 0;
  const toggle = (id: number) =>
    setSelected((s) => {
      const next = new Set(s);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  return (
    <section>
      <h1>Clients</h1>
      <div className="filters">
        <label>
          Search
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Name, client code or full PAN" />
        </label>
        <label className="check">
          <input
            type="checkbox"
            checked={needsRm}
            onChange={(e) => {
              setPage(0);
              setNeedsRm(e.target.checked);
            }}
          />
          Only clients without an RM
        </label>
      </div>
      {canEdit && selected.size > 0 && (
        <BulkRmBar
          ids={[...selected]}
          rms={rms}
          onDone={() => {
            setSelected(new Set());
            clients.reload();
          }}
        />
      )}
      {clients.error && <p className="error">{clients.error}</p>}
      {data && data.total === 0 && <p className="muted">{query || needsRm ? 'No clients match.' : 'No clients yet. Load them from the MF file under Setup → Load clients.'}</p>}
      {data && data.total > 0 && (
        <>
          <table>
            <thead>
              <tr>
                {canEdit && (
                  <th>
                    <input
                      type="checkbox"
                      aria-label="Select all on this page"
                      checked={data.items.every((c) => selected.has(c.id))}
                      onChange={(e) => setSelected(e.target.checked ? new Set([...selected, ...data.items.map((c) => c.id)]) : new Set())}
                    />
                  </th>
                )}
                <th>Code</th>
                <th>Client</th>
                <th>PAN</th>
                <th>RM</th>
                <th>Branch</th>
                <th>Partner</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((c) => (
                <tr key={c.id}>
                  {canEdit && (
                    <td>
                      <input type="checkbox" aria-label={`Select ${c.name}`} checked={selected.has(c.id)} onChange={() => toggle(c.id)} />
                    </td>
                  )}
                  <td className="mono">{c.iwellCode}</td>
                  <td>
                    <button type="button" className="cell-link" onClick={() => setOpen(c.id)}>
                      {c.name}
                    </button>
                  </td>
                  <td className="mono">{c.pan}</td>
                  <td>{c.rmName ?? <span className="badge warn-badge">needs RM</span>}</td>
                  <td>{c.branchName}</td>
                  <td>{shareText(shares.data?.[c.id])}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="pager">
            <button type="button" className="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
              Previous
            </button>
            <span>
              Page {page + 1} of {lastPage + 1} · {data.total} clients
            </span>
            <button type="button" className="secondary" disabled={page >= lastPage} onClick={() => setPage(page + 1)}>
              Next
            </button>
          </div>
        </>
      )}
      {open !== null && (
        <ClientPanel
          id={open}
          rms={rms}
          canEdit={canEdit}
          onClose={close}
          onChanged={() => {
            clients.reload();
            shares.reload();
          }}
        />
      )}
    </section>
  );
}

/** "ALPHA WEALTH 50% (Sub-broker) + PARTNER TWO 20% (Referral partner)" */
function shareText(list: Share[] | undefined) {
  if (!list || list.length === 0) return <span className="muted">—</span>;
  return list.map((s, i) => (
    <span key={s.id}>
      {i > 0 && ' + '}
      {s.partnerName} {s.percent}% <span className="muted">({PARTNER_TYPE_LABEL[s.partnerRole]})</span>
    </span>
  ));
}

function RmSelect({ rms, value, onChange, exclude }: { rms: Employee[]; value: string; onChange: (v: string) => void; exclude?: number | null }) {
  return (
    <label>
      RM
      <select value={value} onChange={(e) => onChange(e.target.value)} required>
        <option value="">Choose…</option>
        {rms
          .filter((e) => e.id !== exclude)
          .map((e) => (
            <option key={e.id} value={e.id}>
              {e.name}
              {e.branchName ? ` — ${e.branchName}` : ''}
            </option>
          ))}
      </select>
    </label>
  );
}

/** Set the same RM, from the same date, for all selected clients. */
function BulkRmBar({ ids, rms, onDone }: { ids: number[]; rms: Employee[]; onDone: () => void }) {
  const [rmId, setRmId] = useState('');
  const [from, setFrom] = useState(todayIso());
  const { busy, error, run } = useSubmit();
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (await run(() => api('/api/clients/rm', { method: 'POST', body: { clientIds: ids, rmId: Number(rmId), from } }))) onDone();
  }
  return (
    <form className="bulk-bar" onSubmit={submit}>
      <strong>{ids.length} selected</strong>
      <RmSelect rms={rms} value={rmId} onChange={setRmId} />
      <label>
        From
        <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} required />
      </label>
      <button type="submit" disabled={busy || !rmId}>
        Set RM
      </button>
      {error && <span className="error">{error}</span>}
    </form>
  );
}

function ClientPanel({ id, rms, canEdit, onClose, onChanged }: { id: number; rms: Employee[]; canEdit: boolean; onClose: () => void; onChanged: () => void }) {
  const detail = useLoad<ClientDetail>(`/api/clients/${id}`);
  const history = useLoad<HistoryEntry[]>(`/api/clients/${id}/rm`);
  const [rmId, setRmId] = useState('');
  const c = detail.data?.client;

  return (
    <SidePanel title={c?.name ?? 'Client'} onClose={onClose}>
      {detail.error && <p className="error">{detail.error}</p>}
      {c && detail.data && (
        <>
          <dl className="facts">
            <dt>Code</dt>
            <dd className="mono">{c.iwellCode}</dd>
            <dt>PAN</dt>
            <dd className="mono">
              {c.pan ?? '—'}
              {canEdit && (
                <PanEdit
                  clientId={id}
                  onDone={() => {
                    detail.reload();
                    onChanged();
                  }}
                />
              )}
            </dd>
            <dt>Family head</dt>
            <dd>{detail.data.familyHeadName ?? detail.data.familyHeadCode ?? '—'}</dd>
            <dt>RM now</dt>
            <dd>{c.rmName ? `${c.rmName}${c.branchName ? ` — ${c.branchName}` : ''}` : 'none'}</dd>
          </dl>
          {canEdit && (
            <DatedChangeForm
              title={c.rmId ? 'Change RM' : 'Set RM'}
              dateLabel="From"
              submitLabel="Save"
              canSubmit={rmId !== ''}
              onSubmit={(from) => api(`/api/clients/${id}/rm`, { method: 'POST', body: { rmId: Number(rmId), from } })}
              onDone={() => {
                setRmId('');
                detail.reload();
                history.reload();
                onChanged();
              }}
            >
              <RmSelect rms={rms} value={rmId} onChange={setRmId} exclude={c.rmId} />
            </DatedChangeForm>
          )}
          <details className="past">
            <summary>Past RM changes</summary>
            {history.data && <HistoryTable label="RM" rows={history.data.map((h) => ({ ...h, name: h.targetName }))} />}
          </details>
          <PartnerSection clientId={id} canEdit={canEdit} onChanged={onChanged} />
        </>
      )}
    </SidePanel>
  );
}

/** The client's partners: a sub-broker and/or a referral partner, each with its own % and dates. */
function PartnerSection({ clientId, canEdit, onChanged }: { clientId: number; canEdit: boolean; onChanged: () => void }) {
  const history = useLoad<Share[]>(`/api/clients/${clientId}/partner`);
  const partners = useLoad<Partner[]>(canEdit ? '/api/partners' : null);
  const refresh = () => {
    history.reload();
    onChanged();
  };
  const all = history.data ?? [];

  return (
    <div className="separated">
      <h3>Partners</h3>
      <p className="muted">The sub-broker is paid first; the referral partner&apos;s % is taken from what is left.</p>
      {(['SUB_BROKER', 'REFERRAL'] as const).map((role) => (
        <RoleShare
          key={role}
          clientId={clientId}
          role={role}
          now={all.find((s) => s.partnerRole === role && s.validTo === null)}
          partners={(partners.data ?? []).filter((p) => p.type === role && p.status === 'ACTIVE')}
          canEdit={canEdit}
          onChanged={refresh}
        />
      ))}
      {all.length > 0 && (
        <details className="past">
          <summary>Past partner changes</summary>
          <table className="compact">
            <thead>
              <tr><th>Partner</th><th>Type</th><th>%</th><th>From</th><th>To</th></tr>
            </thead>
            <tbody>
              {all.map((s) => (
                <tr key={s.id}>
                  <td>{s.partnerName}</td>
                  <td>{PARTNER_TYPE_LABEL[s.partnerRole]}</td>
                  <td className="num">{s.percent}%</td>
                  <td>{formatDate(s.validFrom)}</td>
                  <td>{s.validTo ? formatDate(s.validTo) : <span className="badge">current</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </details>
      )}
    </div>
  );
}

function RoleShare({ clientId, role, now, partners, canEdit, onChanged }: {
  clientId: number;
  role: 'SUB_BROKER' | 'REFERRAL';
  now: Share | undefined;
  partners: Partner[];
  canEdit: boolean;
  onChanged: () => void;
}) {
  const [partnerId, setPartnerId] = useState('');
  const [percent, setPercent] = useState('');
  const [lastDay, setLastDay] = useState('');
  const { busy, error, run } = useSubmit();
  const label = PARTNER_TYPE_LABEL[role];

  return (
    <div className="role-share">
      <h4>{label}</h4>
      <p>{now ? `${now.partnerName} · ${now.percent}%` : <span className="muted">None</span>}</p>
      {canEdit && (
        <DatedChangeForm
          title={now ? `Change % or ${label.toLowerCase()}` : `Add a ${label.toLowerCase()}`}
          dateLabel="From"
          submitLabel="Save"
          canSubmit={(partnerId !== '' || now !== undefined) && percent !== ''}
          onSubmit={(from) =>
            api(`/api/clients/${clientId}/partner`, {
              method: 'POST',
              body: { partnerId: Number(partnerId || now?.partnerId), percent: Number(percent), from },
            })
          }
          onDone={() => {
            setPartnerId('');
            setPercent('');
            onChanged();
          }}
        >
          <label>
            {label}
            <select value={partnerId} onChange={(e) => setPartnerId(e.target.value)} required={!now}>
              <option value="">{now ? `Keep ${now.partnerName}` : 'Choose…'}</option>
              {partners.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            {now ? 'New sharing %' : 'Sharing %'}
            <input type="number" min="0" max="100" step="0.01" value={percent} onChange={(e) => setPercent(e.target.value)} required />
          </label>
          {now && (
            <p className="muted">
              From a later date: the old % stays for the months before. From {now.validFrom} (its first day): the % is corrected.
            </p>
          )}
        </DatedChangeForm>
      )}
      {canEdit && now && (
        <form
          className="inline-form"
          onSubmit={async (e) => {
            e.preventDefault();
            if (await run(() => api(`/api/clients/${clientId}/partner/end`, { method: 'POST', body: { role, lastDay } }))) onChanged();
          }}
        >
          <span>No {label.toLowerCase()} after</span>
          <input type="date" value={lastDay} onChange={(e) => setLastDay(e.target.value)} aria-label={`Last day with ${label.toLowerCase()}`} required />
          <button type="submit" className="secondary" disabled={busy || !lastDay}>
            Remove
          </button>
          {error && <span className="error">{error}</span>}
        </form>
      )}
    </div>
  );
}

/** Corrects the client's PAN (a typo in the master or in Finesse). Audited; refused if another client has the PAN. */
function PanEdit({ clientId, onDone }: { clientId: number; onDone: () => void }) {
  const [open, setOpen] = useState(false);
  const [pan, setPan] = useState('');
  const [reason, setReason] = useState('');
  const { busy, error, run } = useSubmit();
  if (!open) {
    return (
      <button type="button" className="link" onClick={() => setOpen(true)}>
        Edit PAN
      </button>
    );
  }
  return (
    <form
      className="inline-form"
      onSubmit={async (e: FormEvent) => {
        e.preventDefault();
        if (await run(() => api(`/api/clients/${clientId}/pan`, { method: 'POST', body: { pan, reason } }))) {
          setOpen(false);
          setPan('');
          setReason('');
          onDone();
        }
      }}
    >
      <input value={pan} onChange={(e) => setPan(e.target.value.toUpperCase())} placeholder="ABCDE1234F" aria-label="New PAN" maxLength={10} required />
      <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Reason" aria-label="Reason for the PAN change" />
      <button type="submit" className="secondary" disabled={busy || pan.trim().length !== 10}>
        Save PAN
      </button>
      <button type="button" className="link" onClick={() => setOpen(false)}>
        Cancel
      </button>
      {error && <span className="error">{error}</span>}
    </form>
  );
}
