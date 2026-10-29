import { Fragment, useEffect, useState, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, errorText } from '../../api/client';
import {
  PARTNER_TYPE_LABEL,
  type FinesseMasterLoad,
  type FinesseMasterStatus,
  type MonthRow,
  type RunResult,
  type SplitSummary,
  type StageResult,
  type UploadReview,
} from '../../api/types';
import { useJob, jobErrorText } from '../../api/useJob';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { formatMoney } from '../../format';
import { Amount } from '../../components/Amount';
import { JobProgressBar } from '../../components/JobProgressBar';

const MONTH_LABEL = new Intl.DateTimeFormat('en-IN', { month: 'long', year: 'numeric' });
const DATE_TIME = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });

/** "2026-08" → "August 2026" */
export function monthLabel(ym: string): string {
  const [y, m] = ym.split('-').map(Number);
  return MONTH_LABEL.format(new Date(y, m - 1, 1));
}

/** Last month as yyyy-mm: brokerage files arrive for the month that just ended. */
function lastMonth(): string {
  const d = new Date();
  d.setDate(1);
  d.setMonth(d.getMonth() - 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function nextMonth(ym: string): string {
  const [y, m] = ym.split('-').map(Number);
  return m === 12 ? `${y + 1}-01` : `${y}-${String(m + 1).padStart(2, '0')}`;
}

/** What stands between a month and processing it (R-91), as the server checks it. */
interface Readiness {
  status: 'OPEN' | 'PROCESSED' | 'LOCKED';
  mfSaved: boolean;
  mfClients: number;
  equityUploaded: boolean;
  equityClients: number;
  equityNotInClients: number;
  entries: Partial<Record<string, number>>;
  notInMaster: number;
  missingPercent: string[];
  ready: boolean;
  /** Equity clients of the month not in Clients, as the broker's file has them. */
  equityMissing?: { partyCode: string; name: string | null; pan: string | null; share: number }[];
}

/** The products entered for a month, in END order, with the Product entries tab each lives on. */
const PRODUCTS: { key: string; label: string }[] = [
  { key: 'PREIPO', label: 'Pre-IPO' }, { key: 'FD', label: 'FD' }, { key: 'BOND', label: 'Bonds' }, { key: 'LOAN', label: 'Loans' },
  { key: 'INS', label: 'Insurance' }, { key: 'PMS', label: 'PMS' }, { key: 'AIF', label: 'AIF' }, { key: 'GIFT', label: 'GIFT City' },
  { key: 'NPS', label: 'NPS' }, { key: 'P2P', label: 'P2P' },
];

type StepState = 'done' | 'todo' | 'blocked' | 'waiting' | 'optional';
const STATE_LABEL: Record<StepState, string> = { done: 'Done', todo: 'To do', blocked: 'Needs fixing', waiting: 'Not yet', optional: 'When they change' };

/**
 * Month close as one guided page: pick the month, then go down its steps — masters, the MF file, the month's other
 * products, check & process, END & lock. Each step says whether it is done and has its own action; the step to do next
 * is open.
 */
export function MonthPage() {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const months = useLoad<MonthRow[]>('/api/months');
  const [params, setParams] = useSearchParams();
  const list = [...(months.data ?? [])].sort((a, b) => a.month.localeCompare(b.month));
  const latest = list.length ? list[list.length - 1].month : null;
  const upcoming = latest ? nextMonth(latest) : lastMonth();
  // The month to work on: the one picked, else the first month not locked yet, else the latest month, else a new one.
  const selected = params.get('m') ?? list.find((m) => m.status !== 'LOCKED')?.month ?? latest ?? upcoming;
  const row = list.find((m) => m.month === selected) ?? null;
  const pick = (ym: string) => {
    const next = new URLSearchParams(params);
    next.set('m', ym);
    setParams(next, { replace: true });
  };

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Month close</h1>
          <p className="muted" style={{ margin: 0 }}>
            Pick the month, then go down the steps. The step to do next is open; each one shows whether it is done.
          </p>
        </div>
      </div>
      {months.error && <p className="error">{months.error}</p>}
      <div className="month-chips" role="tablist" aria-label="Months">
        {list.map((m) => (
          <button key={m.month} type="button" role="tab" aria-selected={m.month === selected} className={m.month === selected ? 'chip active' : 'chip'} onClick={() => pick(m.month)}>
            {monthLabel(m.month)} <span className={m.status === 'LOCKED' ? 'badge ok' : m.status === 'PROCESSED' ? 'badge' : 'badge warn'}>{STATUS_LABEL[m.status]}</span>
          </button>
        ))}
        {canEdit && (
          <button type="button" role="tab" aria-selected={selected === upcoming && !row} className={selected === upcoming && !row ? 'chip active' : 'chip'} onClick={() => pick(upcoming)}>
            + {monthLabel(upcoming)}
          </button>
        )}
      </div>
      {months.data && <MonthWizard key={selected} month={selected} row={row} canEdit={canEdit} onChanged={months.reload} />}
    </section>
  );
}

/** One step of the month: number (✓ when done), title, state and a one-line summary; its body when open. */
function Step({ n, title, state, summary, open: openFirst, children }: { n: number; title: string; state: StepState; summary?: string; open: boolean; children: ReactNode }) {
  const [open, setOpen] = useState(openFirst);
  // A step that becomes the next one (the month's state loaded or changed) opens; the user can still close it.
  useEffect(() => {
    if (openFirst) setOpen(true);
  }, [openFirst]);
  return (
    <li className={`wstep ${state}${openFirst ? ' current' : ''}`}>
      <button type="button" className="wstep-head" aria-expanded={open} onClick={() => setOpen(!open)}>
        <span className="wstep-num" aria-hidden="true">{state === 'done' ? '✓' : n}</span>
        <span className="wstep-title">{title}</span>
        <span className={`wstep-state ${state}`}>{STATE_LABEL[state]}</span>
        {summary && <span className="wstep-summary muted">{summary}</span>}
      </button>
      {open && <div className="wstep-body">{children}</div>}
    </li>
  );
}

function MonthWizard({ month, row, canEdit, onChanged }: { month: string; row: MonthRow | null; canEdit: boolean; onChanged: () => void }) {
  const { user } = useAuth();
  const master = useLoad<FinesseMasterStatus>('/api/finesse/client-master');
  const ready = useLoad<Readiness>(row ? `/api/months/${row.periodId}/readiness` : null);
  const [reviewing, setReviewing] = useState<{ id: number; notes: string[] } | null>(null);
  const [replacing, setReplacing] = useState(false);
  const r = ready.data;
  const saved = (row?.saved?.lines ?? 0) > 0;
  const pending = row?.pendingUpload ?? null;
  const status = row?.status ?? 'OPEN';
  const reviewId = reviewing?.id ?? pending?.id ?? null;
  const changed = () => {
    onChanged();
    ready.reload();
  };

  const masterState: StepState = master.data && master.data.codes === 0 ? 'todo' : 'optional';
  const mfState: StepState = reviewId ? 'todo' : saved ? (row!.notInMaster > 0 ? 'blocked' : 'done') : 'todo';
  const productsState: StepState = !row ? 'waiting' : r && r.equityNotInClients > 0 ? 'blocked' : 'optional';
  const checkState: StepState = !row || !saved ? 'waiting' : status !== 'OPEN' ? 'done' : r && !r.ready ? 'blocked' : 'todo';
  const endState: StepState = status === 'LOCKED' ? 'done' : status === 'PROCESSED' ? 'todo' : 'waiting';
  const current = masterState === 'todo' ? 1 : mfState !== 'done' ? 2 : status === 'OPEN' ? 4 : 5;
  const entryCount = r ? PRODUCTS.reduce((s, p) => s + (r.entries[p.key] ?? 0), 0) : 0;

  return (
    <ol className="wizard">
      <Step n={1} title="Masters" state={masterState} open={current === 1}
        summary={master.data && master.data.codes > 0 ? `Client master ${master.data.codes.toLocaleString('en-IN')} codes${master.data.updatedAt ? `, updated ${DATE_TIME.format(new Date(master.data.updatedAt))}` : ''}` : undefined}>
        <p className="muted">Only when they change: the Finesse client master (code → PAN) and Master MIS (PAN → RM, partners and %).</p>
        <div className="source-cards">
          <ClientMasterCard canEdit={canEdit} />
          <MasterMisCard canEdit={canEdit} />
        </div>
      </Step>

      <Step n={2} title={`MF brokerage for ${monthLabel(month)}`} state={mfState} open={current === 2}
        summary={saved ? `${row!.savedUpload?.fileName ?? 'Saved'} · ${row!.saved!.lines.toLocaleString('en-IN')} clients · net ${formatMoney(row!.saved!.net)}` : undefined}>
        {reviewId ? (
          <Review key={reviewId} uploadId={reviewId} notes={reviewing?.notes ?? []} canEdit={canEdit} onDone={() => { setReviewing(null); setReplacing(false); changed(); }} />
        ) : saved && !replacing ? (
          <>
            {row!.notInMaster > 0 ? (
              <p className="error">
                {row!.notInMaster} clients are not in Master MIS: the month cannot be processed until each has an RM.{' '}
                {row!.savedUpload && (
                  <button type="button" className="link" onClick={() => setReviewing({ id: row!.savedUpload!.id, notes: [] })}>
                    Fix {row!.notInMaster} clients, then process
                  </button>
                )}
              </p>
            ) : (
              <p className="ok">Saved: {row!.saved!.lines.toLocaleString('en-IN')} clients, net {formatMoney(row!.saved!.net)}. Every client is in Master MIS.</p>
            )}
            {canEdit && status !== 'LOCKED' && (
              <button type="button" className="link" onClick={() => setReplacing(true)}>
                Upload a corrected file instead
              </button>
            )}
          </>
        ) : canEdit ? (
          <BrokerageCard month={month} onStaged={(id, notes) => { setReviewing({ id, notes }); onChanged(); }} />
        ) : (
          <p className="muted">Not uploaded yet.</p>
        )}
      </Step>

      <Step n={3} title="Other products this month" state={productsState} open={(!!row && saved && status === 'OPEN') || productsState === 'blocked'}
        summary={r ? `${entryCount} entries · equity ${r.equityUploaded ? `${r.equityClients} clients` : 'not uploaded'}` : undefined}>
        {!row ? (
          <p className="muted">
            Pre-IPO, FD, bonds, loans, insurance, PMS, AIF, GIFT City, NPS and P2P can be entered for {monthLabel(month)} any time on{' '}
            <Link to={`/products?from=${month}&to=${month}`}>Product entries</Link>; once the MF file is saved, their counts show here.
          </p>
        ) : !r ? (
          <p className="muted">Checking the month…</p>
        ) : (
          <>
            <p className="muted">Everything entered for {monthLabel(month)} is included when the month is processed. Add or correct entries before you process.</p>
            <div className="product-tally">
              {PRODUCTS.map((p) => (
                <Link key={p.key} className={(r.entries[p.key] ?? 0) > 0 ? 'tally has' : 'tally'} to={`/products?tab=${p.key}&from=${month}&to=${month}`}>
                  <span>{p.label}</span>
                  <b>{r.entries[p.key] ?? 0}</b>
                </Link>
              ))}
              <Link className={r.equityUploaded ? 'tally has' : 'tally'} to="/products?tab=EQUITY">
                <span>Equity file</span>
                <b>{r.equityUploaded ? `${r.equityClients} clients` : 'not uploaded'}</b>
              </Link>
            </div>
            {r.equityNotInClients > 0 && (
              <MissingEquity month={month} list={r.equityMissing ?? []} count={r.equityNotInClients} canEdit={canEdit} onAdded={changed} />
            )}
            <p className="muted">Monthly files: the equity brokerage report on the Equity tab, the insurance policy sheet on the Insurance tab.</p>
          </>
        )}
      </Step>

      <Step n={4} title="Check & process" state={checkState} open={current === 4}
        summary={status !== 'OPEN' && row?.processedBy ? `Processed by ${row.processedBy}${row.processedAt ? `, ${DATE_TIME.format(new Date(row.processedAt))}` : ''}` : undefined}>
        {!row || !saved ? (
          <p className="muted">Save the MF file first (step 2).</p>
        ) : (
          <ProcessStep row={row} r={r} canEdit={canEdit} onChanged={changed} recheck={ready.reload} />
        )}
      </Step>

      <Step n={5} title="END file & lock" state={endState} open={current === 5}
        summary={status === 'LOCKED' ? `Locked${row?.lockedBy ? ` by ${row.lockedBy}` : ''}` : undefined}>
        {!row || status === 'OPEN' ? (
          <p className="muted">Process the month first (step 4); then download the END file and Finance locks it.</p>
        ) : (
          <EndStep row={row} role={user?.role} onChanged={changed} />
        )}
      </Step>
    </ol>
  );
}

/**
 * The month's equity clients that are not in Clients, as the broker's file has them (party code, name, PAN if any, share),
 * each with "Add client": the name and PAN from the file, an RM, from the month's first day. The trade finds it at once.
 */
function MissingEquity({ month, list, count, canEdit, onAdded }: {
  month: string; list: NonNullable<Readiness['equityMissing']>; count: number; canEdit: boolean; onAdded: () => void;
}) {
  const [adding, setAdding] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [pan, setPan] = useState('');
  const [rmId, setRmId] = useState('');
  const employees = useLoad<{ id: number; name: string; branchName: string | null; status: string }[]>(adding ? '/api/employees' : null);
  const { busy, error, run } = useSubmit();
  const open = (m: (typeof list)[number]) => {
    setAdding(m.partyCode);
    setName(m.name ?? '');
    setPan(m.pan && !m.pan.includes('*') ? m.pan : '');
    setRmId('');
  };
  return (
    <div className="card-block" style={{ marginTop: 8 }}>
      <p className="error" style={{ marginTop: 0 }}>
        {count} equity client(s) of {monthLabel(month)} are not in Clients. Add each one (or correct its name on the Clients screen); the month can be processed once all are found.
      </p>
      <table className="compact highlight-rows">
        <thead>
          <tr>
            <th>Party code</th>
            <th>Name in the broker's file</th>
            <th>PAN</th>
            <th className="num">Share</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {list.map((m) => (
            <Fragment key={m.partyCode + (m.pan ?? '')}>
              <tr>
                <td className="mono">{m.partyCode}</td>
                <td>{m.name ?? '—'}</td>
                <td className="mono">{m.pan ?? <span className="muted">not in the file</span>}</td>
                <td className="num"><Amount value={m.share} /></td>
                <td>
                  {canEdit && adding !== m.partyCode && (
                    <button type="button" className="secondary" onClick={() => open(m)}>
                      Add client
                    </button>
                  )}
                  {m.name && (
                    <Link to={`/clients?q=${encodeURIComponent(m.name.split(' ')[0])}`} style={{ marginLeft: 8 }}>
                      Look in Clients
                    </Link>
                  )}
                </td>
              </tr>
              {adding === m.partyCode && (
                <tr>
                  <td colSpan={5}>
                    <form
                      className="inline-form"
                      onSubmit={async (e) => {
                        e.preventDefault();
                        const ok = await run(() => api('/api/clients', {
                          method: 'POST',
                          body: { name, pan: pan || null, rmId: Number(rmId), from: `${month}-01`, reason: `Equity client (party ${m.partyCode}) of ${monthLabel(month)}` },
                        }));
                        if (ok) {
                          setAdding(null);
                          onAdded();
                        }
                      }}
                    >
                      <label>
                        Client name
                        <input value={name} onChange={(e) => setName(e.target.value)} required />
                      </label>
                      <label>
                        PAN {m.pan ? '' : '(optional)'}
                        <input value={pan} onChange={(e) => setPan(e.target.value.toUpperCase().replace(/\s/g, ''))} maxLength={10} placeholder="ABCDE1234F" />
                      </label>
                      <label>
                        RM
                        <select value={rmId} onChange={(e) => setRmId(e.target.value)} required>
                          <option value="">Choose…</option>
                          {(employees.data ?? []).filter((x) => x.status === 'ACTIVE').map((x) => (
                            <option key={x.id} value={x.id}>
                              {x.branchName ? `${x.name} — ${x.branchName}` : x.name}
                            </option>
                          ))}
                        </select>
                      </label>
                      <button type="submit" disabled={busy}>
                        Add to Clients
                      </button>
                      <button type="button" className="link" onClick={() => setAdding(null)}>
                        Cancel
                      </button>
                    </form>
                    {!m.pan && <p className="muted">The file has no PAN for this client: it is matched by this exact name. Add the PAN when you have it.</p>}
                    {error && <p className="error">{error}</p>}
                  </td>
                </tr>
              )}
            </Fragment>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Step 4: the pre-check (what blocks Process, with where to fix it), Process, and the split once done. */
function ProcessStep({ row, r, canEdit, onChanged, recheck }: { row: MonthRow; r: Readiness | null; canEdit: boolean; onChanged: () => void; recheck: () => void }) {
  const [jobId, setJobId] = useState<number | null>(null);
  const { busy, error, run } = useSubmit();
  const { job, running } = useJob<RunResult>(jobId);
  useEffect(() => {
    if (job?.status === 'DONE' || job?.status === 'FAILED') onChanged();
  }, [job?.status]); // onChanged deliberately not a dependency: once per finished run

  const checks: { ok: boolean; text: ReactNode; to: string; link: string }[] = r
    ? [
        { ok: r.notInMaster === 0, text: r.notInMaster === 0 ? 'Every client has an RM' : `${r.notInMaster} client(s) with business this month have no RM`, to: '/clients?show=NO_RM', link: 'Give them an RM' },
        {
          ok: r.missingPercent.length === 0,
          text: r.missingPercent.length === 0 ? 'Every partner has its % for the products it has business in' : <>Partner % missing: <span className="muted">{r.missingPercent.join(', ')}</span></>,
          to: '/setup/partners',
          link: 'Set %',
        },
        { ok: r.equityNotInClients === 0, text: r.equityNotInClients === 0 ? 'Every equity PAN is a client' : `${r.equityNotInClients} equity PAN(s) not in Clients`, to: '/products?tab=EQUITY', link: 'See' },
      ]
    : [];
  const processed = row.status !== 'OPEN';
  const canRun = canEdit && row.status !== 'LOCKED' && (r?.ready || processed) && row.notInMaster === 0;

  return (
    <>
      {!r ? (
        <p className="muted">Checking the month…</p>
      ) : (
        <ul className="todo">
          {checks.map((c, i) => (
            <li key={i} className={c.ok ? 'ok' : 'blocker'}>
              <span className="what">
                <span className="mark" aria-label={c.ok ? 'done' : 'needs fixing'}>{c.ok ? '✓' : '!'}</span>
                <span>{c.text}</span>
              </span>
              {!c.ok && <Link to={c.to}>{c.link}</Link>}
            </li>
          ))}
        </ul>
      )}
      <div className="actions" style={{ marginTop: 10 }}>
        {canRun && (
          <button
            type="button"
            className={processed ? 'secondary' : undefined}
            disabled={running || busy}
            onClick={async () => {
              await run(async () => setJobId((await api<{ jobId: number }>(`/api/months/${row.periodId}/process`, { method: 'POST' })).jobId));
            }}
          >
            {running ? 'Processing…' : processed ? 'Process again' : `Process ${monthLabel(row.month)}`}
          </button>
        )}
        {!processed && r && !r.ready && <span className="muted">Fix the items above, then process.</span>}
        {processed && canEdit && row.status !== 'LOCKED' && <span className="muted">Process again after changing any entry of the month.</span>}
        <button type="button" className="link" onClick={recheck}>
          Check again
        </button>
      </div>
      {running && <JobProgressBar job={job} label="Processing…" />}
      {job?.status === 'FAILED' && <p className="error">{jobErrorText(job)}</p>}
      {error && <p className="error">{error}</p>}
      {processed && (
        <>
          <h3>How the month was split</h3>
          <Split periodId={row.periodId} />
        </>
      )}
    </>
  );
}

/** Step 5: the END file (a draft until locked), Finance's lock, and an admin's unlock with a reason. */
function EndStep({ row, role, onChanged }: { row: MonthRow; role?: string; onChanged: () => void }) {
  const [unlocking, setUnlocking] = useState(false);
  const [reason, setReason] = useState('');
  const { busy, error, run } = useSubmit();
  const locked = row.status === 'LOCKED';
  const canDownload = ['ADMIN', 'HO', 'FINANCE'].includes(role ?? '');
  return (
    <>
      <p className="muted">
        {locked ? `${monthLabel(row.month)} is locked: the END file is final. Corrections go into the next open month as adjustments.`
          : 'Download the END file to check it. Finance locks the month when it is right; until then it is a draft and can be processed again.'}
      </p>
      <div className="actions">
        {canDownload && (
          <a className="button-link" href={`/api/months/${row.periodId}/end-file`} download>
            {locked ? 'END file' : 'END file (draft)'}
          </a>
        )}
        {(role === 'FINANCE' || role === 'ADMIN') && !locked && (
          <button type="button" disabled={busy} onClick={async () => { if (await run(() => api(`/api/months/${row.periodId}/lock`, { method: 'POST' }))) onChanged(); }}>
            Lock
          </button>
        )}
        {role !== 'FINANCE' && role !== 'ADMIN' && !locked && <span className="muted">Finance locks the month.</span>}
        {role === 'ADMIN' && locked && !unlocking && (
          <button type="button" className="secondary" onClick={() => setUnlocking(true)}>
            Unlock…
          </button>
        )}
        {role === 'ADMIN' && locked && unlocking && (
          <span className="inline-form">
            <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Reason (required)" aria-label="Reason to unlock" />
            <button type="button" className="secondary" disabled={busy || !reason.trim()}
              onClick={async () => { if (await run(() => api(`/api/months/${row.periodId}/unlock`, { method: 'POST', body: { reason } }))) { setUnlocking(false); onChanged(); } }}>
              Unlock
            </button>
          </span>
        )}
      </div>
      {error && <p className="error">{error}</p>}
    </>
  );
}

/** Finesse client master: which PAN (or guardian PAN) each Finesse client code belongs to. Upload when it changes. */
function ClientMasterCard({ canEdit }: { canEdit: boolean }) {
  const status = useLoad<FinesseMasterStatus>('/api/finesse/client-master');
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<FinesseMasterLoad | null>(null);
  const { busy, error, run } = useSubmit();
  const s = status.data;

  async function upload() {
    if (!file) return;
    const form = new FormData();
    form.append('file', file);
    const ok = await run(async () => setResult(await api<FinesseMasterLoad>('/api/finesse/client-master', { method: 'POST', body: form })));
    if (ok) status.reload();
  }

  return (
    <div className="source-card">
      <h2>Finesse client master</h2>
      <p className="muted">Client code → PAN and guardian PAN. Upload when it changes.</p>
      {status.error && <p className="error">{status.error}</p>}
      {s && s.codes === 0 && <p className="warn-note">Not loaded yet. Upload it before the brokerage file.</p>}
      {s && s.codes > 0 && (
        <p>
          {s.codes.toLocaleString('en-IN')} codes · {s.withPan.toLocaleString('en-IN')} with PAN · {s.withGuardianPan} with guardian PAN
          {s.updatedAt && <span className="muted"> · updated {DATE_TIME.format(new Date(s.updatedAt))}</span>}
        </p>
      )}
      {canEdit && (
        <div className="upload">
          <input type="file" accept=".xlsx" aria-label="Finesse client master file" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          <button type="button" className="secondary" disabled={!file || busy} onClick={upload}>
            {busy ? 'Reading…' : 'Upload client master'}
          </button>
        </div>
      )}
      {error && <p className="error">{error}</p>}
      {result && (
        <p className="ok" role="status">
          {result.rows.toLocaleString('en-IN')} codes read: {result.added} new, {result.updated} changed, {result.unchanged} unchanged.
          {result.withoutAnyPan > 0 && ` ${result.withoutAnyPan} have no PAN or guardian PAN.`}
          {result.invalidPans > 0 && ` ${result.invalidPans} PANs were not valid and were left out.`}
        </p>
      )}
    </div>
  );
}

/** Master MIS: PAN → RM, sub broker, referral partner and %. */
function MasterMisCard({ canEdit }: { canEdit: boolean }) {
  return (
    <div className="source-card">
      <h2>Master MIS</h2>
      <p className="muted">PAN → RM, sub broker, referral partner and %. Upload when it changes, or edit a client on the Clients screen.</p>
      <div className="upload">
        {canEdit && (
          <Link className="button-link" to="/setup/load-shares">
            Upload Master MIS
          </Link>
        )}
        <Link className="button-link" to="/clients">
          Clients
        </Link>
      </div>
    </div>
  );
}

/** The month's Finesse MF brokerage file. */
function BrokerageCard({ month, onStaged }: { month: string; onStaged: (uploadId: number, notes: string[]) => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [jobId, setJobId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { job, error: jobError, running } = useJob<StageResult>(jobId);

  useEffect(() => {
    if (job?.status === 'DONE' && job.result?.uploadId) onStaged(job.result.uploadId, job.result.notes ?? []);
  }, [job?.status, job?.result?.uploadId]); // onStaged deliberately not a dependency: run once per finished upload

  async function upload() {
    if (!file) return;
    setError(null);
    setJobId(null);
    const form = new FormData();
    form.append('file', file);
    form.append('month', month);
    try {
      const { jobId: id } = await api<{ jobId: number }>('/api/uploads', { method: 'POST', body: form });
      setJobId(id);
    } catch (e) {
      setError(errorText(e));
    }
  }

  const fileErrors = job?.status === 'DONE' && job.result && !job.result.uploadId ? job.result.errors : [];
  return (
    <div className="source-card accent">
      <h2>Finesse MF brokerage for {monthLabel(month)}</h2>
      <p className="muted">The month's file: client code and brokerage (net of GST). RM and partners come from Master MIS. Next: review and save.</p>
      <div className="upload">
        <input type="file" accept=".xlsx" aria-label="Finesse brokerage file" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        <button type="button" disabled={!file || !month || running} onClick={upload}>
          {running ? 'Reading…' : 'Upload brokerage'}
        </button>
      </div>
      {running && <JobProgressBar job={job} label="Reading the file…" />}
      {error && <p className="error">{error}</p>}
      {jobError && <p className="error">{jobError}</p>}
      {job?.status === 'FAILED' && <p className="error">The file could not be read: {jobErrorText(job)}</p>}
      {fileErrors.length > 0 && (
        <div className="result">
          <p className="error" role="status">The file was not accepted:</p>
          <ul>
            {fileErrors.slice(0, 50).map((e, i) => (
              <li key={i}>
                {e.row ? `Row ${e.row}: ` : ''}
                {e.message}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function Review({ uploadId, notes, canEdit, onDone }: { uploadId: number; notes: string[]; canEdit: boolean; onDone: () => void }) {
  const review = useLoad<UploadReview>(`/api/uploads/${uploadId}/review`);
  const [saved, setSaved] = useState<string | null>(null);
  const { busy, error, run } = useSubmit();
  const r = review.data;
  if (review.error) return <p className="error">{review.error}</p>;
  if (!r) return <p className="muted">Loading review…</p>;
  const u = r.upload;
  const missingNet = r.notInMaster.reduce((sum, n) => sum + n.net, 0);

  return (
    <div className="card-block">
      <h2>
        Review: {u.fileName} for {monthLabel(u.month)}
      </h2>
      <p>
        {r.codes.toLocaleString('en-IN')} client codes → {r.clients.toLocaleString('en-IN')} clients · net <strong>{formatMoney(u.fileTotals.net)}</strong> · GST 18%{' '}
        {formatMoney(u.fileTotals.gst)} · MF trail {formatMoney(u.fileTotals.gross)}
      </p>
      <div className="stat-row">
        <div className="stat">
          <span className="muted">Matched by PAN</span>
          <strong>{r.byPan.toLocaleString('en-IN')}</strong>
        </div>
        <div className="stat">
          <span className="muted">Matched by guardian PAN</span>
          <strong>{r.byGuardianPan.toLocaleString('en-IN')}</strong>
        </div>
        <div className="stat">
          <span className="muted">Matched by name (no PAN)</span>
          <strong>{r.byName.toLocaleString('en-IN')}</strong>
        </div>
        <div className={r.notInMaster.length > 0 ? 'stat danger' : 'stat'}>
          <span>Not in Master MIS</span>
          <strong>{r.notInMaster.length}</strong>
        </div>
      </div>
      {notes.length > 0 && (
        <ul className="notes">
          {notes.map((n, i) => (
            <li key={i}>{n}</li>
          ))}
        </ul>
      )}

      {r.notInMaster.length > 0 ? (
        <>
          <h3 className="error">
            {r.notInMaster.length} clients are not in Master MIS (net {formatMoney(missingNet)}). The month can be saved, but not processed until they
            are added.
          </h3>
          <table className="compact highlight-rows">
            <thead>
              <tr>
                <th>Finesse code</th>
                <th>Client</th>
                <th>PAN</th>
                <th>Why</th>
                <th className="num">Net</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {r.notInMaster.map((n) => (
                <tr key={n.clientId}>
                  <td className="mono">{n.codes}</td>
                  <td>{n.name}</td>
                  <td className="mono">{n.pan ?? '—'}</td>
                  <td>{n.reason}</td>
                  <td className="num"><Amount value={n.net} /></td>
                  <td>
                    {canEdit && (
                      <Link className="button-link" to={`/clients?q=${encodeURIComponent(n.name)}`}>
                        Give an RM
                      </Link>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="upload">
            <a className="button-link" href={`/api/uploads/${uploadId}/not-in-master.xlsx`} download>
              Download this list (Excel)
            </a>
            <span className="muted">Add them to Master MIS and upload it again, or give each an RM on the Clients screen.</span>
            <button type="button" className="link" onClick={review.reload}>
              Check again
            </button>
          </p>
        </>
      ) : (
        <p className="ok">Every client is in Master MIS.</p>
      )}

      <h3>Save the month</h3>
      {r.correction && (
        <p className="warn-note">
          {monthLabel(u.month)} is locked. Saving does not change it: the difference for each client is posted into the next open month as
          an adjustment.
        </p>
      )}
      {u.status !== 'STAGED' ? (
        <p className="muted">This upload is {u.status.toLowerCase()}.</p>
      ) : (
        canEdit && (
          <div className="upload">
            <button
              type="button"
              disabled={busy}
              onClick={async () => {
                const ok = await run(async () => {
                  const res = await api<{ lines: { lines: number; net: number } }>(`/api/uploads/${uploadId}/commit`, { method: 'POST' });
                  setSaved(`Saved ${res.lines.lines} clients, net ${formatMoney(res.lines.net)}.`);
                });
                if (ok) onDone();
              }}
            >
              {r.correction ? 'Post corrections' : `Save ${monthLabel(u.month)}`}
            </button>
            <button
              type="button"
              className="secondary"
              disabled={busy}
              onClick={async () => {
                if (await run(() => api(`/api/uploads/${uploadId}/discard`, { method: 'POST' }))) onDone();
              }}
            >
              Discard this upload
            </button>
          </div>
        )
      )}
      {error && <p className="error">{error}</p>}
      {saved && <p className="ok" role="status">{saved}</p>}
    </div>
  );
}

const STATUS_LABEL: Record<MonthRow['status'], string> = { OPEN: 'Open', PROCESSED: 'Processed', LOCKED: 'Locked' };

/** How the month's net was split: totals, then by partner and by branch. */
function Split({ periodId }: { periodId: number }) {
  const s = useLoad<SplitSummary>(`/api/months/${periodId}/summary`);
  if (s.error) return <p className="error">{s.error}</p>;
  if (!s.data) return <p className="muted">Loading…</p>;
  const d = s.data;
  // Partners whose share came to 0 this month are not listed (as in the END, R-83).
  const paid = d.byPartner.filter((p) => Math.abs(p.amount) >= 0.005);
  return (
    <div className="split">
      <table className="compact" aria-label="Split totals">
        <tbody>
          <tr><td>Net brokerage</td><td className="num"><Amount value={d.net} /></td></tr>
          <tr><td>Sub-brokers</td><td className="num"><Amount value={d.subBrokers} /></td></tr>
          <tr><td>Referral partners</td><td className="num"><Amount value={d.referrals} /></td></tr>
          <tr><td><strong>Epsilon keeps</strong></td><td className="num"><strong><Amount value={d.epsilon} /></strong></td></tr>
          {d.adjustments > 0 && <tr><td>of which corrections to locked months</td><td className="num">{d.adjustments} clients</td></tr>}
        </tbody>
      </table>
      <details>
        <summary>Paid to {paid.length} partners</summary>
        <table className="compact">
          <thead>
            <tr><th>Partner</th><th>Type</th><th className="num">Clients</th><th className="num">Amount</th></tr>
          </thead>
          <tbody>
            {paid.map((p) => (
              <tr key={p.partnerId + p.role}>
                <td>{p.partnerName}</td>
                <td>{PARTNER_TYPE_LABEL[p.role]}</td>
                <td className="num">{p.clients}</td>
                <td className="num"><Amount value={p.amount} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
      <details>
        <summary>By branch</summary>
        <table className="compact">
          <thead>
            <tr><th>Region</th><th>Branch</th><th className="num">Net</th><th className="num">Partners</th><th className="num">Epsilon</th></tr>
          </thead>
          <tbody>
            {d.byBranch.map((b) => (
              <tr key={`${b.region}|${b.branch}`}>
                <td>{b.region ?? '—'}</td>
                <td>{b.branch ?? '—'}</td>
                <td className="num"><Amount value={b.net} /></td>
                <td className="num"><Amount value={b.partners} /></td>
                <td className="num"><Amount value={b.epsilon} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  );
}
