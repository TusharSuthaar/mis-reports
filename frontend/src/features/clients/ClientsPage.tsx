import { MfBrokerage } from './MfBrokerage';
import { OpsMaster } from './OpsMaster';
import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api } from '../../api/client';
import { PARTNER_TYPE_LABEL, type ClientDetail, type ClientRow, type Employee, type HistoryEntry, type Page, type Partner, type Share } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { DatedChangeForm } from '../../components/DatedChangeForm';
import { HistoryTable } from '../../components/HistoryTable';
import { SidePanel } from '../../components/SidePanel';
import { Tabs, useTab } from '../../components/Tabs';
import type { ClientHoldings, HoldingMember } from '../../api/types';
import { Amount } from '../../components/Amount';
import { formatMonth } from '../fd/FdPage';
import { formatDate, formatDateTime, todayIso } from '../../format';

const PAGE_SIZE = 50;

export function ClientsPage() {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const [urlParams, setUrlParams] = useSearchParams();
  const navigate = useNavigate();
  const [search, setSearch] = useState(urlParams.get('q') ?? '');
  const [query, setQuery] = useState(urlParams.get('q') ?? '');
  const show = (urlParams.get('show') ?? '') as Show;
  const setShow = (v: Show) => {
    const next = new URLSearchParams(urlParams);
    if (v) next.set('show', v);
    else next.delete('show');
    setUrlParams(next, { replace: true });
  };
  const [adding, setAdding] = useState(false);
  const attention = useLoad<{ sharedPan: number; noRm: number; noPan: number }>('/api/clients/attention');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const employees = useLoad<Employee[]>('/api/employees');
  const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE), needsRm: String(show === 'NO_RM') });
  if (show === 'SHARED_PAN' || show === 'NO_PAN') params.set('attention', show);
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
      <div className="page-head">
        <div>
          <h1>Clients</h1>
          <p className="muted" style={{ margin: 0 }}>
            The client master: one record per PAN, with its RM and partners. Open a client to change anything about it.
          </p>
        </div>
        {canEdit && (
          <button type="button" onClick={() => setAdding(true)}>
            Add client
          </button>
        )}
      </div>
      <div className="filters list-filters">
        <label className="grow">
          Search
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Name, client code or full PAN" />
        </label>
        <div className="chip-row" role="group" aria-label="Show">
          {(
            [
              ['', 'All clients', null],
              ['NO_RM', 'Without an RM', attention.data?.noRm],
              ['SHARED_PAN', 'PAN on several records', attention.data?.sharedPan],
              ['NO_PAN', 'Without a PAN', attention.data?.noPan],
            ] as [Show, string, number | null | undefined][]
          ).map(([key, text, n]) => (
            <button
              key={key || 'all'}
              type="button"
              className={show === key ? 'chip active' : 'chip'}
              aria-pressed={show === key}
              onClick={() => {
                setPage(0);
                setShow(key);
              }}
            >
              {text}
              {n != null && <span className="count">{n}</span>}
            </button>
          ))}
        </div>
      </div>
      {show === 'SHARED_PAN' && (
        <p className="muted">
          One PAN, several records (old data). Usually a minor carries the guardian&apos;s PAN: open the minor and use &ldquo;It&apos;s the
          guardian&apos;s PAN&rdquo;. A duplicate made by a file: open the right client, Edit PAN, and move it.
        </p>
      )}
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
      {data && data.total === 0 && <p className="muted">{query || show ? 'No clients match.' : 'No clients yet. Load them from the MF file under Setup → Load clients.'}</p>}
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
                    <Link to={`/clients/${c.id}`} className="cell-link">
                      {c.name}
                    </Link>
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
      {adding && (
        <SidePanel title="Add client" onClose={() => setAdding(false)}>
          <AddClientForm
            rms={rms}
            onDone={(id) => {
              setAdding(false);
              clients.reload();
              attention.reload();
              navigate(`/clients/${id}`);
            }}
          />
        </SidePanel>
      )}
    </section>
  );
}

type Show = '' | 'NO_RM' | 'SHARED_PAN' | 'NO_PAN';

/** A client not in any file yet: name, PAN (must be new), RM from a date. */
function AddClientForm({ rms, onDone }: { rms: Employee[]; onDone: (id: number) => void }) {
  const [name, setName] = useState('');
  const [pan, setPan] = useState('');
  const [rmId, setRmId] = useState('');
  const [from, setFrom] = useState(todayIso());
  const [reason, setReason] = useState('');
  const { busy, error, run } = useSubmit();
  async function submit(e: FormEvent) {
    e.preventDefault();
    let id = 0;
    const ok = await run(async () => {
      const r = await api<{ id: number }>('/api/clients', {
        method: 'POST',
        body: { name, pan: pan.trim() || null, rmId: Number(rmId), from, reason },
      });
      id = r.id;
    });
    if (ok) onDone(id);
  }
  return (
    <form onSubmit={submit}>
      <label>
        Client name
        <input value={name} onChange={(e) => setName(e.target.value)} required />
      </label>
      <label>
        PAN <span className="muted">(leave blank for a minor: matched by guardian PAN)</span>
        <input value={pan} onChange={(e) => setPan(e.target.value.toUpperCase())} placeholder="ABCDE1234F" maxLength={10} />
      </label>
      <RmSelect rms={rms} value={rmId} onChange={setRmId} />
      <label>
        RM from
        <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} required />
      </label>
      <label>
        Reason
        <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. new account" />
      </label>
      {error && <p className="error">{error}</p>}
      <button type="submit" disabled={busy || !name.trim() || !rmId}>
        Add client
      </button>
    </form>
  );
}

/** The record's own audited changes: added, PAN corrections (who, when, why). */
function RecordChanges({ clientId }: { clientId: number }) {
  const changes = useLoad<{ id: number; action: string; after: { maskedPan?: string | null; reason?: string | null } | null; by: string; at: string }[]>(
    `/api/clients/${clientId}/changes`,
  );
  if (!changes.data || changes.data.length === 0) return null;
  return (
    <details className="past">
      <summary>Changes to this record ({changes.data.length})</summary>
      <table className="compact">
        <thead>
          <tr>
            <th>When</th>
            <th>What</th>
            <th>By</th>
          </tr>
        </thead>
        <tbody>
          {changes.data.map((a) => (
            <tr key={a.id}>
              <td>{formatDateTime(a.at)}</td>
              <td>
                {a.action === 'CREATE' ? 'Added' : a.action === 'PAN_CHANGE' ? `PAN → ${a.after?.maskedPan ?? 'none'}` : a.action}
                {a.after?.reason && <div className="muted">{a.after.reason}</div>}
              </td>
              <td>{a.by}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </details>
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

const PROFILE_TABS = [
  { key: 'profile', label: 'Profile' },
  { key: 'mf', label: 'MF brokerage' },
  { key: 'rm', label: 'RM history' },
  { key: 'partners', label: 'Partners & sharing' },
  { key: 'holdings', label: 'Holdings' },
  { key: 'changes', label: 'Changes' },
];

/** One client on its own page: profile (PAN, codes, RM), RM history, partners, holdings in other products, changes. */
export function ClientProfilePage() {
  const { id: idParam } = useParams();
  const id = Number(idParam);
  const navigate = useNavigate();
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const detail = useLoad<ClientDetail>(`/api/clients/${id}`);
  const history = useLoad<HistoryEntry[]>(`/api/clients/${id}/rm`);
  const employees = useLoad<Employee[]>('/api/employees');
  const rms = (employees.data ?? []).filter((e) => e.status === 'ACTIVE');
  const [rmId, setRmId] = useState('');
  const [tab, setTab] = useTab(PROFILE_TABS);
  const c = detail.data?.client;
  const changed = () => {
    detail.reload();
    history.reload();
  };

  return (
    <section aria-label={c?.name ?? 'Client'}>
      <p style={{ margin: '0 0 8px' }}>
        <Link to="/clients">← Clients</Link>
      </p>
      {detail.error && <p className="error">{detail.error}</p>}
      {c && detail.data && (
        <>
          <div className="page-head">
            <div>
              <h1>{c.name}</h1>
              <div className="actions">
                <span className="badge">{c.iwellCode ?? 'OFFLINE'}</span>
                <span className="badge mono">{c.pan ?? 'no PAN'}</span>
                {c.rmName ? <span className="badge ok">RM {c.rmName}</span> : <span className="badge warn">needs RM</span>}
                {c.branchName && <span className="badge">{c.branchName}</span>}
              </div>
            </div>
          </div>
          <Tabs tabs={PROFILE_TABS} value={tab} onChange={setTab} />
          {tab === 'profile' && (
            <div className="grid-2">
              <div className="panel-card">
                <h2>Details</h2>
                <dl className="facts">
                  <dt>Code</dt>
                  <dd className="mono">{c.iwellCode ?? '—'}</dd>
                  <dt>Second code</dt>
                  <dd className="mono">{detail.data.iwellCode2 ?? '—'}</dd>
                  <dt>PAN</dt>
                  <dd className="mono">
                    {c.pan ?? '—'}
                    {canEdit && <PanEdit clientId={id} currentPan={c.pan} onDone={changed} />}
                  </dd>
                  <dt>Family head</dt>
                  <dd>{detail.data.familyHeadName ?? detail.data.familyHeadCode ?? '—'}</dd>
                  <dt>RM now</dt>
                  <dd>{c.rmName ? `${c.rmName}${c.branchName ? ` — ${c.branchName}` : ''}` : 'none'}</dd>
                </dl>
              </div>
              {canEdit && (
                <div className="panel-card">
                  <DatedChangeForm
                    title={c.rmId ? 'Change RM' : 'Set RM'}
                    dateLabel="From"
                    submitLabel="Save"
                    canSubmit={rmId !== ''}
                    onSubmit={(from) => api(`/api/clients/${id}/rm`, { method: 'POST', body: { rmId: Number(rmId), from } })}
                    onDone={() => {
                      setRmId('');
                      changed();
                    }}
                  >
                    <RmSelect rms={rms} value={rmId} onChange={setRmId} exclude={c.rmId} />
                  </DatedChangeForm>
                  <MergeForm client={c} onDone={() => navigate('/clients')} />
                </div>
              )}
            </div>
          )}
          {tab === 'profile' && <OpsMaster clientId={id} />}
          {tab === 'mf' && <MfBrokerage clientId={id} />}
          {tab === 'rm' && (
            <div className="panel-card">
              <h2>RM over time</h2>
              {history.data && history.data.length > 0 ? (
                <HistoryTable label="RM" rows={history.data.map((h) => ({ ...h, name: h.targetName }))} />
              ) : (
                <p className="muted">No RM yet.</p>
              )}
            </div>
          )}
          {tab === 'partners' && (
            <div className="panel-card">
              <PartnerSection clientId={id} canEdit={canEdit} onChanged={changed} />
            </div>
          )}
          {tab === 'holdings' && <Holdings clientId={id} />}
          {tab === 'changes' && (
            <div className="panel-card">
              <RecordChanges key={`${id}-${c.pan}`} clientId={id} />
            </div>
          )}
        </>
      )}
    </section>
  );
}

const RELATION_LABEL: Record<HoldingMember['relation'], string> = { THIS: 'this record', SAME_PAN: 'same PAN', MINOR: 'minor' };

/**
 * Everything the client's PAN holds (R-09): this record, any other record with the same PAN and minors mapped to it by
 * guardian PAN, across FD, bonds, AIF, loans, GIFT City, PMS, NPS, P2P and equity.
 */
function Holdings({ clientId }: { clientId: number }) {
  const { data } = useLoad<ClientHoldings>(`/api/clients/${clientId}/holdings`);
  if (!data) return <p className="muted">Loading…</p>;
  const several = data.members.length > 1;
  const products = [...new Set(data.rows.map((r) => r.product))];
  return (
    <>
      <p className="muted">
        {several ? 'Holdings of every record under this PAN: ' : 'Holdings under this PAN. '}
        {several &&
          data.members.map((m, i) => (
            <span key={m.clientId}>
              {i > 0 && ', '}
              {m.relation === 'THIS' ? m.name : <Link to={`/clients/${m.clientId}?tab=holdings`}>{m.name}</Link>}
              <span className="badge">{RELATION_LABEL[m.relation]}</span>
            </span>
          ))}
        {!several && data.members[0]?.pan == null && 'This record has no PAN, so only its own entries show.'}
      </p>
      {data.rows.length === 0 ? (
        <div className="empty">No FD, bond, AIF, loan, GIFT City, PMS, NPS, P2P or equity holdings under this PAN.</div>
      ) : (
        <>
          <p className="muted">
            {products.map((p) => `${p} ${data.rows.filter((r) => r.product === p).length}`).join(' · ')}
          </p>
          <table>
            <thead>
              <tr>
                <th>Product</th>
                <th>Month</th>
                {several && <th>Client</th>}
                <th>Holding</th>
                <th className="num">Amount</th>
                <th className="num">Gross</th>
              </tr>
            </thead>
            <tbody>
              {data.rows.map((r, i) => (
                <tr key={`${r.tab}-${r.id ?? i}`}>
                  <td>
                    <span className="badge">{r.product}</span>
                  </td>
                  <td>{formatMonth(r.month)}</td>
                  {several && <td>{r.clientName ?? <span className="muted">not a client yet</span>}</td>}
                  <td>
                    <Link to={r.id == null ? `/products?tab=${r.tab}` : `/products?tab=${r.tab}&open=${r.id}`}>{r.item}</Link>
                  </td>
                  <td className="num"><Amount value={r.amount} /></td>
                  <td className="num"><Amount value={r.gross} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </>
  );
}

/** The client's partners: a sub-broker and/or a referral partner, each with its own % and dates. */
function PartnerSection({ clientId, canEdit, onChanged }: { clientId: number; canEdit: boolean; onChanged: () => void }) {
  const history = useLoad<Share[]>(`/api/clients/${clientId}/partner`);
  const partners = useLoad<Partner[]>(canEdit ? '/api/partners' : null);
  const today = useLoad<Record<string, Share[]>>(`/api/shares?clientIds=${clientId}`);
  const refresh = () => {
    history.reload();
    today.reload();
    onChanged();
  };
  const all = history.data ?? [];
  /** Today's partners with the % that counts today, branch rule included. */
  const current = today.data?.[clientId] ?? [];

  return (
    <div className="separated">
      <h3>Partners</h3>
      <p className="muted">
        A branch partner is paid first, then the sub-broker; the referral partner&apos;s % is taken from what is left.
      </p>
      {(['SUB_BROKER', 'REFERRAL'] as const).map((role) => (
        <RoleShare
          key={role}
          clientId={clientId}
          role={role}
          now={current.find((s) => s.partnerRole === role && !s.branch) ?? all.find((s) => s.partnerRole === role && s.validTo === null)}
          branchShare={current.find((s) => s.partnerRole === role && s.branch)}
          partners={(partners.data ?? []).filter((p) => p.type === role && p.status !== 'INACTIVE')}
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
                  <td className="num">{s.clientPercent !== null ? `${s.clientPercent}% (this client)` : `${s.percent}% (partner)`}</td>
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

function RoleShare({ clientId, role, now, branchShare, partners, canEdit, onChanged }: {
  clientId: number;
  role: 'SUB_BROKER' | 'REFERRAL';
  now: Share | undefined;
  /** The branch rule's partner in this role, if any (paid before the client's own). */
  branchShare?: Share;
  partners: Partner[];
  canEdit: boolean;
  onChanged: () => void;
}) {
  const [partnerId, setPartnerId] = useState('');
  const [percent, setPercent] = useState('');
  const [own, setOwn] = useState(now?.source === 'CLIENT');
  const [lastDay, setLastDay] = useState('');
  const { busy, error, run } = useSubmit();
  const label = PARTNER_TYPE_LABEL[role];

  return (
    <div className="role-share">
      <h4>{label}</h4>
      {branchShare && (
        <p>
          <ShareText share={branchShare} /> <span className="muted">— paid first</span>
        </p>
      )}
      <p>{now ? <ShareText share={now} /> : <span className="muted">{branchShare ? 'No own ' + label.toLowerCase() : 'None'}</span>}</p>
      {canEdit && (
        <DatedChangeForm
          title={now ? `Change % or ${label.toLowerCase()}` : `Add a ${label.toLowerCase()}`}
          dateLabel="From"
          submitLabel="Save"
          canSubmit={(partnerId !== '' || now !== undefined) && (!own || percent !== '')}
          onSubmit={(from) =>
            api(`/api/clients/${clientId}/partner`, {
              method: 'POST',
              body: { partnerId: Number(partnerId || now?.partnerId), percent: own ? Number(percent) : null, from },
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
          <label className="check">
            <input type="radio" name={`pct-${role}`} checked={!own} onChange={() => setOwn(false)} />
            Use the partner’s MF %
          </label>
          <label className="check">
            <input type="radio" name={`pct-${role}`} checked={own} onChange={() => setOwn(true)} />
            A different % for this client only
          </label>
          {own && (
            <label>
              {now ? 'New sharing %' : 'Sharing %'}
              <input type="number" min="0" max="100" step="0.01" value={percent} onChange={(e) => setPercent(e.target.value)} required />
            </label>
          )}
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

/** A partner and the % that counts, saying where the % comes from. */
function ShareText({ share }: { share: Share }) {
  if (share.branch) {
    return (
      <>
        {share.partnerName} · {share.percent}% <span className="badge">all {share.branch} clients</span>
      </>
    );
  }
  switch (share.source) {
    case 'CLIENT':
      return (
        <>
          {share.partnerName} · {share.percent}% <span className="badge">this client only</span>
        </>
      );
    case 'PARTNER':
      return (
        <>
          {share.partnerName} · {share.percent}% <span className="muted">(partner’s MF %)</span>
        </>
      );
    case 'PENDING':
      return (
        <>
          {share.partnerName} · 0% <span className="badge warn-badge">partner approval pending</span>
        </>
      );
    default:
      return (
        <>
          {share.partnerName} · <span className="badge warn-badge">no MF % set for this partner</span>
        </>
      );
  }
}

const PAN_FORMAT = /^[A-Z]{5}[0-9]{4}[A-Z]$/;

/** Corrects the client's PAN (a typo in the master or in Finesse). Audited; refused if another client has the PAN. */
function PanEdit({ clientId, currentPan, onDone }: { clientId: number; currentPan: string | null; onDone: () => void }) {
  // Start from the PAN on file when it is shown in full, so a typo is fixed rather than retyped.
  const startPan = currentPan && !currentPan.includes('*') ? currentPan : '';
  const [open, setOpen] = useState(false);
  const [pan, setPan] = useState(startPan);
  const [reason, setReason] = useState('');
  const [move, setMove] = useState(false);
  const { busy, error, run } = useSubmit();
  if (!open) {
    return (
      <>
        <button
          type="button"
          className="link"
          onClick={() => {
            setPan(startPan);
            setOpen(true);
          }}
        >
          {currentPan ? 'Edit PAN' : 'Add PAN'}
        </button>
        <button
          type="button"
          className="link"
          disabled={busy}
          title="For a minor: the guardian's record keeps the PAN; this client is then matched by guardian PAN"
          onClick={async () => {
            if (!window.confirm("This PAN is the guardian's? It will be removed from this client; the guardian keeps it.")) return;
            if (await run(() => api(`/api/clients/${clientId}/pan/guardian`, { method: 'POST', body: { reason: "Guardian's PAN" } }))) onDone();
          }}
        >
          It's the guardian's PAN
        </button>
        {error && <span className="error">{error}</span>}
      </>
    );
  }
  return (
    <form
      className="inline-form"
      onSubmit={async (e: FormEvent) => {
        e.preventDefault();
        if (await run(() => api(`/api/clients/${clientId}/pan`, { method: 'POST', body: { pan, reason, takeFromOther: move } }))) {
          setOpen(false);
          setPan('');
          setReason('');
          setMove(false);
          onDone();
        }
      }}
    >
      <input value={pan} onChange={(e) => setPan(e.target.value.toUpperCase().replace(/\s/g, ''))} placeholder="ABCDE1234F" aria-label="New PAN" maxLength={10} required />
      <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Reason" aria-label="Reason for the PAN change" />
      <button type="submit" className="secondary" disabled={busy || !PAN_FORMAT.test(pan) || pan === startPan}>
        Save PAN
      </button>
      <button type="button" className="link" onClick={() => setOpen(false)}>
        Cancel
      </button>
      {pan.length === 10 && !PAN_FORMAT.test(pan) && <span className="error">A PAN looks like ABCDE1234F</span>}
      <span className="muted">The old PAN is remembered: a later Master MIS or Finesse file that still has it finds this client.</span>
      {error && <span className="error">{error}</span>}
      {error?.includes('is already on') && (
        <label className="check">
          <input type="checkbox" checked={move} onChange={(e) => setMove(e.target.checked)} />
          That record is a duplicate: move the PAN to this client (only if it has no RM)
        </label>
      )}
    </form>
  );
}

/**
 * Two records of one person (e.g. a file's PAN had swapped digits): this record is merged into the one picked and removed.
 * Its codes and unsaved upload rows move across; the PAN chosen is kept and the other is remembered (R-07).
 */
function MergeForm({ client, onDone }: { client: ClientRow; onDone: () => void }) {
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState(client.name);
  const [targetId, setTargetId] = useState<number | null>(null);
  const [keepThisPan, setKeepThisPan] = useState(false);
  const [reason, setReason] = useState('');
  const found = useLoad<Page<ClientRow>>(open && q.trim().length >= 3 ? `/api/clients?q=${encodeURIComponent(q.trim())}&size=8` : null);
  const { busy, error, run } = useSubmit();
  const others = (found.data?.items ?? []).filter((x) => x.id !== client.id);
  const target = others.find((x) => x.id === targetId) ?? null;

  if (!open) {
    return (
      <div className="separated">
        <button type="button" className="link" onClick={() => setOpen(true)}>
          Same person as another client? Merge…
        </button>
      </div>
    );
  }
  return (
    <form
      className="separated"
      onSubmit={async (e: FormEvent) => {
        e.preventDefault();
        if (!target) return;
        const kept = keepThisPan ? client.pan : target.pan;
        if (!window.confirm(`Merge ${client.name} into ${target.name} (PAN ${kept ?? 'none'})? This record will be removed.`)) return;
        if (await run(() => api(`/api/clients/${client.id}/merge`, { method: 'POST', body: { intoClientId: target.id, keepThisPan, reason } }))) onDone();
      }}
    >
      <h3>Merge into another client</h3>
      <p className="muted">
        This record is removed. Its codes and unsaved upload rows move to the client you pick, which keeps its RM history. The PAN
        not kept is remembered, so later files that still have it find the right client.
      </p>
      <label>
        Find the other record (name, code or full PAN)
        <input value={q} onChange={(e) => setQ(e.target.value)} aria-label="Find the client to merge into" />
      </label>
      {q.trim().length >= 3 && found.data && others.length === 0 && <p className="muted">No other client matches.</p>}
      {others.map((x) => (
        <label key={x.id} className="check">
          <input type="radio" name="merge-target" checked={targetId === x.id} onChange={() => setTargetId(x.id)} />
          {x.name} · <span className="mono">{x.pan ?? 'no PAN'}</span> · {x.rmName ?? 'no RM'} {x.iwellCode ? `· ${x.iwellCode}` : ''}
        </label>
      ))}
      {target && (
        <fieldset>
          <legend>PAN to keep</legend>
          <label className="check">
            <input type="radio" name="merge-pan" checked={!keepThisPan} onChange={() => setKeepThisPan(false)} />
            <span className="mono">{target.pan ?? 'none'}</span> (the client you picked)
          </label>
          <label className="check">
            <input type="radio" name="merge-pan" checked={keepThisPan} onChange={() => setKeepThisPan(true)} disabled={!client.pan} />
            <span className="mono">{client.pan ?? 'none'}</span> (this record)
          </label>
        </fieldset>
      )}
      <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Reason" aria-label="Reason for the merge" />
      {error && <p className="error">{error}</p>}
      <span className="inline-form">
        <button type="submit" className="secondary" disabled={busy || !target}>
          Merge
        </button>
        <button type="button" className="link" onClick={() => setOpen(false)}>
          Cancel
        </button>
      </span>
    </form>
  );
}
