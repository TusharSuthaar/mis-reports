import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, errorText } from '../../api/client';
import { Fragment } from 'react';
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

/**
 * The month's files: the Finesse client master (code → PAN) and Master MIS (PAN → RM and partners) when they
 * change, and the Finesse MF brokerage file every month. Then review, save and process the month.
 */
export function MonthPage() {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const months = useLoad<MonthRow[]>('/api/months');
  const [reviewing, setReviewing] = useState<{ id: number; notes: string[] } | null>(null);

  return (
    <section>
      <h1>This month</h1>
      <div className="source-cards">
        <ClientMasterCard canEdit={canEdit} />
        <MasterMisCard canEdit={canEdit} />
        {canEdit && (
          <BrokerageCard
            onStaged={(id, notes) => {
              setReviewing({ id, notes });
              months.reload();
            }}
          />
        )}
      </div>
      {reviewing !== null && (
        <Review
          key={reviewing.id}
          uploadId={reviewing.id}
          notes={reviewing.notes}
          canEdit={canEdit}
          onDone={() => {
            setReviewing(null);
            months.reload();
          }}
        />
      )}
      <h2 className="section-title">Months</h2>
      {months.error && <p className="error">{months.error}</p>}
      {months.data && months.data.length === 0 && <p className="muted">No month uploaded yet.</p>}
      {months.data && months.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Month</th>
              <th>Status</th>
              <th>Saved file</th>
              <th className="num">Gross (incl. GST)</th>
              <th className="num">GST</th>
              <th className="num">Net</th>
              <th className="num">Clients</th>
              <th>Split</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {months.data.map((m) => (
              <MonthLine key={m.periodId} m={m} canEdit={canEdit} onReview={(id) => setReviewing({ id, notes: [] })} onChanged={months.reload} />
            ))}
          </tbody>
        </table>
      )}
    </section>
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
function BrokerageCard({ onStaged }: { onStaged: (uploadId: number, notes: string[]) => void }) {
  const [month, setMonth] = useState(lastMonth());
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
      <h2>Finesse MF brokerage</h2>
      <p className="muted">Every month: client code and brokerage (net of GST). RM and partners come from Master MIS.</p>
      <div className="upload">
        <label className="inline">
          Month
          <input type="month" value={month} onChange={(e) => setMonth(e.target.value)} required />
        </label>
        <input type="file" accept=".xlsx" aria-label="Finesse brokerage file" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        <button type="button" disabled={!file || !month || running} onClick={upload}>
          {running ? 'Reading…' : 'Upload brokerage'}
        </button>
      </div>
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
                  <td className="num">{formatMoney(n.net)}</td>
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

/** One month: its figures, its status and the actions its status and the user's role allow. */
function MonthLine({ m, canEdit, onReview, onChanged }: { m: MonthRow; canEdit: boolean; onReview: (id: number) => void; onChanged: () => void }) {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const [jobId, setJobId] = useState<number | null>(null);
  const [unlocking, setUnlocking] = useState(false);
  const [reason, setReason] = useState('');
  const { busy, error, run } = useSubmit();
  const { job, running } = useJob<RunResult>(jobId);

  useEffect(() => {
    if (job?.status === 'DONE' || job?.status === 'FAILED') onChanged();
  }, [job?.status]); // onChanged deliberately not a dependency: once per finished run

  const hasFigures = (m.saved?.lines ?? 0) > 0;
  const blocked = hasFigures && m.notInMaster > 0 && m.status !== 'LOCKED';
  const canProcess = canEdit && hasFigures && m.status !== 'LOCKED' && !blocked;
  const canLock = user?.role === 'FINANCE' && m.status === 'PROCESSED';
  const canUnlock = user?.role === 'ADMIN' && m.status === 'LOCKED';
  const canDownload = ['ADMIN', 'HO', 'FINANCE'].includes(user?.role ?? '') && m.status !== 'OPEN';

  return (
    <Fragment>
      <tr>
        <td>{monthLabel(m.month)}</td>
        <td>
          {STATUS_LABEL[m.status]}
          {m.status === 'OPEN' && hasFigures && !blocked && <div className="muted">needs processing</div>}
          {blocked && <div className="error">{m.notInMaster} not in Master MIS</div>}
          {m.status === 'LOCKED' && m.lockedBy && <div className="muted">by {m.lockedBy}</div>}
        </td>
        <td>{m.savedUpload?.fileName ?? <span className="muted">not saved yet</span>}</td>
        <td className="num">{formatMoney(m.saved?.gross)}</td>
        <td className="num">{formatMoney(m.saved?.gst)}</td>
        <td className="num">{formatMoney(m.saved?.net)}</td>
        <td className="num">{m.saved?.lines ?? ''}</td>
        <td>
          {m.status !== 'OPEN' && (
            <button type="button" className="link" onClick={() => setOpen(!open)}>
              {open ? 'Hide' : 'Show'}
            </button>
          )}
        </td>
        <td className="actions-cell">
          {m.pendingUpload && (
            <button type="button" className="link" onClick={() => onReview(m.pendingUpload!.id)}>
              Review {m.pendingUpload.fileName}
            </button>
          )}
          {blocked && m.savedUpload && (
            <button type="button" className="link" onClick={() => onReview(m.savedUpload!.id)}>
              Fix {m.notInMaster} clients, then process
            </button>
          )}
          {canProcess && (
            <button
              type="button"
              className="secondary"
              disabled={running || busy}
              onClick={async () => {
                await run(async () => setJobId((await api<{ jobId: number }>(`/api/months/${m.periodId}/process`, { method: 'POST' })).jobId));
              }}
            >
              {running ? 'Processing…' : m.status === 'PROCESSED' ? 'Process again' : 'Process'}
            </button>
          )}
          {canDownload && (
            <a className="button-link" href={`/api/months/${m.periodId}/end-file`} download>
              {m.status === 'LOCKED' ? 'END file' : 'END file (draft)'}
            </a>
          )}
          {canLock && (
            <button
              type="button"
              disabled={busy}
              onClick={async () => {
                if (await run(() => api(`/api/months/${m.periodId}/lock`, { method: 'POST' }))) onChanged();
              }}
            >
              Lock
            </button>
          )}
          {canUnlock && !unlocking && (
            <button type="button" className="secondary" onClick={() => setUnlocking(true)}>
              Unlock…
            </button>
          )}
          {canUnlock && unlocking && (
            <span className="inline-form">
              <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Reason (required)" aria-label="Reason to unlock" />
              <button
                type="button"
                className="secondary"
                disabled={busy || !reason.trim()}
                onClick={async () => {
                  if (await run(() => api(`/api/months/${m.periodId}/unlock`, { method: 'POST', body: { reason } }))) {
                    setUnlocking(false);
                    onChanged();
                  }
                }}
              >
                Unlock
              </button>
            </span>
          )}
          {job?.status === 'FAILED' && <div className="error">{jobErrorText(job)}</div>}
          {error && <div className="error">{error}</div>}
        </td>
      </tr>
      {open && (
        <tr className="detail">
          <td colSpan={9}>
            <Split periodId={m.periodId} />
          </td>
        </tr>
      )}
    </Fragment>
  );
}

/** How the month's net was split: totals, then by partner and by branch. */
function Split({ periodId }: { periodId: number }) {
  const s = useLoad<SplitSummary>(`/api/months/${periodId}/summary`);
  if (s.error) return <p className="error">{s.error}</p>;
  if (!s.data) return <p className="muted">Loading…</p>;
  const d = s.data;
  return (
    <div className="split">
      <table className="compact" aria-label="Split totals">
        <tbody>
          <tr><td>Net brokerage</td><td className="num">{formatMoney(d.net)}</td></tr>
          <tr><td>Sub-brokers</td><td className="num">{formatMoney(d.subBrokers)}</td></tr>
          <tr><td>Referral partners</td><td className="num">{formatMoney(d.referrals)}</td></tr>
          <tr><td><strong>Epsilon keeps</strong></td><td className="num"><strong>{formatMoney(d.epsilon)}</strong></td></tr>
          {d.adjustments > 0 && <tr><td>of which corrections to locked months</td><td className="num">{d.adjustments} clients</td></tr>}
        </tbody>
      </table>
      <details>
        <summary>Paid to {d.byPartner.length} partners</summary>
        <table className="compact">
          <thead>
            <tr><th>Partner</th><th>Type</th><th className="num">Clients</th><th className="num">Amount</th></tr>
          </thead>
          <tbody>
            {d.byPartner.map((p) => (
              <tr key={p.partnerId + p.role}>
                <td>{p.partnerName}</td>
                <td>{PARTNER_TYPE_LABEL[p.role]}</td>
                <td className="num">{p.clients}</td>
                <td className="num">{formatMoney(p.amount)}</td>
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
                <td className="num">{formatMoney(b.net)}</td>
                <td className="num">{formatMoney(b.partners)}</td>
                <td className="num">{formatMoney(b.epsilon)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  );
}
