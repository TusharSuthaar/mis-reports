import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { Employee } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { api, errorText } from '../../api/client';
import { PARTNER_TYPE_LABEL, type Partner, type PartnerType, type ShareLoadPreview, type ShareLoadResult } from '../../api/types';
import { useJob, jobErrorText } from '../../api/useJob';
import { JobProgressBar } from '../../components/JobProgressBar';

/**
 * Master MIS upload (Master_MIS.xlsx): adds the clients it has and the app does not (by PAN; by name for a row without
 * a PAN), sets each client's RM, and the partner shares. Check shows what would change from the chosen date (nothing
 * saved); Load applies it. Re-loading an updated master applies only the differences.
 */
export function LoadSharesPage() {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ShareLoadPreview | null>(null);
  const [from, setFrom] = useState('2026-08-01');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [jobId, setJobId] = useState<number | null>(null);
  const { job, error: jobError, running } = useJob<ShareLoadResult>(jobId);

  async function check() {
    if (!file) return;
    setError(null);
    setBusy(true);
    setJobId(null);
    const form = new FormData();
    form.append('file', file);
    form.append('from', from);
    try {
      setPreview(await api<ShareLoadPreview>('/api/share-load/preview', { method: 'POST', body: form }));
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  }

  async function load() {
    if (!preview) return;
    setError(null);
    try {
      const { jobId: id } = await api<{ jobId: number }>('/api/share-load', {
        method: 'POST',
        body: { token: preview.token, fileName: preview.fileName, from: preview.from },
      });
      setJobId(id);
    } catch (e) {
      setError(errorText(e));
    }
  }

  const p = preview;
  const done = job?.status === 'DONE' && job.result ? job.result : null;
  const reset = () => {
    setPreview(null);
    setJobId(null);
    setFile(null);
    setError(null);
  };
  // Once loaded, the page says so and offers the next step, instead of leaving the check and its Apply button on screen.
  if (done) {
    const leftOut = (p?.unknownRms ?? []).reduce((s, u) => s + u.clients, 0);
    return (
      <section>
        <h1>Upload Master MIS</h1>
        <div className="card-block" role="status">
          <h2 className="ok" style={{ marginTop: 0 }}>✓ Master MIS loaded{p ? `: ${p.fileName}` : ''}</h2>
          <p>Changes count from {p?.from ?? from}.</p>
          <div className="figures">
            {(
              [
                ['Clients added', done.clientsCreated],
                ['RMs changed', done.rmChanged],
                ['Shares set', done.sharesSet],
                ['Shares ended', done.sharesEnded],
                ['Partners created', done.partnersCreated],
                ['Partner product %s set', done.productPercentsSet],
                ['Clients with their own %', done.clientExceptions],
              ] as const
            ).map(([label, n]) => (
              <div key={label} className="figure">
                <span>{label}</span>
                <b>{n.toLocaleString('en-IN')}</b>
              </div>
            ))}
          </div>
          {leftOut > 0 && (
            <p className="warn-note">
              {leftOut} client(s) were left unchanged because their RM name in Master MIS is not known ({(p?.unknownRms ?? []).map((u) => u.spelling).join(', ')}).
              Map those names (or add the RM on the Organisation screen), then upload the file again: only the differences are applied.
            </p>
          )}
          <div className="actions" style={{ marginTop: 12 }}>
            <Link className="button-link" to="/month">
              Back to Month close
            </Link>
            <button type="button" className="secondary" onClick={reset}>
              Upload another file
            </button>
          </div>
        </div>
      </section>
    );
  }
  return (
    <section>
      <h1>Upload Master MIS</h1>
      <p className="muted">
        Master MIS decides each client&apos;s RM, sub broker and referral partner. Clients are keyed by PAN (by name for a row without a PAN);
        clients the app does not have yet are added. Only clients with a partner in the master share brokerage; a partner without a % gets
        0%. A second partner goes in the columns BROKER 2 | TYPE 2 | % 2.
      </p>
      <div className="upload">
        <label className="inline">
          Changes count from
          <input type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPreview(null); }} required />
        </label>
        <input type="file" accept=".xlsx" aria-label="Client master" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        <button type="button" className="secondary" disabled={!file || !from || busy} onClick={check}>
          {busy ? 'Checking…' : 'Check file'}
        </button>
      </div>
      {error && <p className="error">{error}</p>}

      {p && p.error && <p className="error" role="status">{p.error}</p>}
      {p && !p.error && (
        <>
          <h2>{p.fileName}</h2>
          <table className="compact summary" aria-label="Summary">
            <tbody>
              <tr><td>Rows in the master</td><td className="num">{p.rows}</td></tr>
              <tr><td><strong>New clients to add</strong></td><td className="num"><strong>{p.newClients}</strong></td></tr>
              <tr><td>… of which without a PAN (kept by name)</td><td className="num">{p.newClientsWithoutPan}</td></tr>
              <tr><td><strong>RM changes from {p.from}</strong></td><td className="num"><strong>{p.rmChangeCount}</strong></td></tr>
              <tr><td>RM already as in the master</td><td className="num">{p.rmUnchanged}</td></tr>
              <tr><td>Clients without an RM in the master</td><td className="num">{p.clientsWithoutRm}</td></tr>
              <tr><td>Rows with a partner</td><td className="num">{p.rowsWithPartner}</td></tr>
              <tr><td>Clients matched in the system</td><td className="num">{p.matchedClients}</td></tr>
              <tr><td><strong>Shares to set or change</strong></td><td className="num"><strong>{p.toSet}</strong></td></tr>
              <tr><td>Shares to end (no partner in the master any more)</td><td className="num">{p.toEnd}</td></tr>
              <tr><td>Already as in the master</td><td className="num">{p.unchanged}</td></tr>
              <tr><td>Client % of 0 (this client gets nothing)</td><td className="num">{p.zeroPct}</td></tr>
              {p.partnerRates && (
                <>
                  <tr><td>Partners in the partner sheets</td><td className="num">{p.partnerRates.sheetPartners}</td></tr>
                  <tr><td><strong>Partner product %s to set or change</strong></td><td className="num"><strong>{p.partnerRates.percentChanges}</strong></td></tr>
                  <tr><td>Client links that use the partner’s %</td><td className="num">{p.partnerRates.followsPartner}</td></tr>
                  <tr><td>Client links with their own % (differs from the partner’s)</td><td className="num">{p.partnerRates.clientExceptions}</td></tr>
                </>
              )}
              <tr><td>Clients with both a sub-broker and a referral partner</td><td className="num">{p.bothRoles}</td></tr>
              <tr><td>Matched by PAN, name spelt differently</td><td className="num">{p.matchedByPanOnly}</td></tr>
              <tr><td>Partner rows not matched to a client</td><td className="num">{p.unmatchedCount}</td></tr>
            </tbody>
          </table>

          {p.unknownRms.length > 0 && (
            <details open>
              <summary className="error">{p.unknownRms.length} RM names are not known (those clients are left unchanged)</summary>
              <p className="muted">
                Map a name to the RM it means (remembered for every later upload), or add the RM on the Org screen. Then check the file again.
              </p>
              <table className="compact">
                <thead>
                  <tr><th>RM in Master MIS</th><th>Why</th><th className="num">Clients</th><th>Means</th></tr>
                </thead>
                <tbody>
                  {p.unknownRms.map((u) => (
                    <UnknownRmRow key={u.spelling} spelling={u.spelling} note={u.note} clients={u.clients} onMapped={check} />
                  ))}
                </tbody>
              </table>
            </details>
          )}

          {p.rmChanges.length > 0 && (
            <details>
              <summary>
                {p.rmChangeCount} RM changes from {p.from}
                {p.rmChanges.length < p.rmChangeCount ? ` (first ${p.rmChanges.length} shown)` : ''}
              </summary>
              <table className="compact">
                <thead>
                  <tr><th>Client</th><th>RM now</th><th>RM from {p.from}</th></tr>
                </thead>
                <tbody>
                  {p.rmChanges.map((c) => (
                    <tr key={c.clientId}>
                      <td>{c.client}</td>
                      <td>{c.fromRm ?? <span className="muted">none</span>}</td>
                      <td>{c.toRm}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </details>
          )}

          {p.newPartners.length > 0 && (
            <details open>
              <summary>{p.newPartners.length} partners will be created</summary>
              <table className="compact">
                <thead>
                  <tr><th>Partner</th><th>Type</th><th>Clients</th><th>Or is it…</th></tr>
                </thead>
                <tbody>
                  {p.newPartners.map((np) => (
                    <NewPartnerRow key={np.type + np.name} name={np.name} type={np.type} clients={np.clients} onMapped={check} />
                  ))}
                </tbody>
              </table>
            </details>
          )}

          {p.partnerRates && (p.partnerRates.pending.length > 0 || p.partnerRates.approved.length > 0) && (
            <div className="warn-note">
              {p.partnerRates.pending.length > 0 && (
                <p>
                  Under process, so paid nothing until approved: <strong>{p.partnerRates.pending.join(', ')}</strong>
                </p>
              )}
              {p.partnerRates.approved.length > 0 && (
                <p>
                  No longer under process, so paid again: <strong>{p.partnerRates.approved.join(', ')}</strong>
                </p>
              )}
            </div>
          )}

          {p.problems.length > 0 && (
            <details>
              <summary>{p.problems.length} rows need a look (left unchanged)</summary>
              <ul>
                {p.problems.map((m, i) => (
                  <li key={i}>{m}</li>
                ))}
              </ul>
            </details>
          )}

          {p.unmatchedCount > 0 && (
            <details>
              <summary>
                {p.unmatchedCount} partner rows not matched to a client
                {p.unmatched.length < p.unmatchedCount ? ` (first ${p.unmatched.length} shown)` : ''}
              </summary>
              <p className="muted">Usually clients not in any loaded MF file yet. Load the master again after they appear.</p>
              <table className="compact">
                <thead>
                  <tr><th>Row</th><th>Client</th><th>PAN</th><th>Why</th></tr>
                </thead>
                <tbody>
                  {p.unmatched.map((u) => (
                    <tr key={u.row}>
                      <td>{u.row}</td>
                      <td>{u.clientName}</td>
                      <td className="mono">{u.maskedPan}</td>
                      <td>{u.reason}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </details>
          )}

          <div className="upload">
            <button
              type="button"
              disabled={running || p.newClients + p.rmChangeCount + p.toSet + p.toEnd + (p.partnerRates?.percentChanges ?? 0) === 0}
              onClick={load}
            >
              Apply from {p.from}: {p.newClients} new clients, {p.rmChangeCount} RM changes, {p.toSet + p.toEnd} share changes
              {p.partnerRates ? `, ${p.partnerRates.percentChanges} partner %s` : ''}
            </button>
          </div>
          <p className="muted">Partners of the new clients are set right after they are added.</p>
        </>
      )}

      {running && <JobProgressBar job={job} label="Loading the Master MIS…" />}
      {jobError && <p className="error">{jobError}</p>}
      {job?.status === 'FAILED' && <p className="error">Nothing was loaded: {jobErrorText(job)}</p>}
      {job?.status === 'DONE' && job.result && (
        <p className="ok" role="status">
          Done: {job.result.clientsCreated} clients added, {job.result.rmChanged} RMs changed, {job.result.sharesSet} shares set,{' '}
          {job.result.sharesEnded} ended, {job.result.partnersCreated} partners created, {job.result.productPercentsSet} partner product %s
          set, {job.result.clientExceptions} clients with their own %.
        </p>
      )}
    </section>
  );
}

/** One RM spelling Master MIS uses that the app does not know: choose the RM it means. */
/** A partner the master names that the app does not have: create it, or say it is an existing one spelt differently. */
function NewPartnerRow({ name, type, clients, onMapped }: { name: string; type: PartnerType; clients: number; onMapped: () => void }) {
  const partners = useLoad<Partner[]>('/api/partners');
  const [partnerId, setPartnerId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const same = (partners.data ?? []).filter((x) => x.type === type && x.status !== 'INACTIVE');
  return (
    <tr>
      <td>{name}</td>
      <td>{PARTNER_TYPE_LABEL[type]}</td>
      <td className="num">{clients}</td>
      <td>
        <span className="inline-form">
          <select aria-label={`Existing partner meant by ${name}`} value={partnerId} onChange={(e) => setPartnerId(e.target.value)}>
            <option value="">New partner</option>
            {same.map((x) => (
              <option key={x.id} value={x.id}>
                {x.name}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="secondary"
            disabled={!partnerId}
            onClick={async () => {
              setError(null);
              try {
                await api('/api/share-load/partner-spelling', { method: 'POST', body: { spelling: name, partnerId: Number(partnerId) } });
                onMapped();
              } catch (e) {
                setError(errorText(e));
              }
            }}
          >
            Same partner, check again
          </button>
          {error && <span className="error">{error}</span>}
        </span>
      </td>
    </tr>
  );
}

function UnknownRmRow({ spelling, note, clients, onMapped }: { spelling: string; note: string | null; clients: number; onMapped: () => void }) {
  const employees = useLoad<Employee[]>('/api/employees');
  const [rmId, setRmId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const active = (employees.data ?? []).filter((e) => e.status === 'ACTIVE');
  return (
    <tr>
      <td>{spelling}</td>
      <td className="muted">{note}</td>
      <td className="num">{clients}</td>
      <td>
        <span className="inline-form">
          <select aria-label={`RM meant by ${spelling}`} value={rmId} onChange={(e) => setRmId(e.target.value)}>
            <option value="">Choose…</option>
            {active.map((e) => (
              <option key={e.id} value={e.id}>
                {e.branchName ? `${e.name} — ${e.branchName}` : e.name}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="secondary"
            disabled={!rmId}
            onClick={async () => {
              setError(null);
              try {
                await api('/api/share-load/rm-spelling', { method: 'POST', body: { spelling, rmId: Number(rmId) } });
                onMapped();
              } catch (e) {
                setError(errorText(e));
              }
            }}
          >
            Map and check again
          </button>
          {error && <span className="error">{error}</span>}
        </span>
      </td>
    </tr>
  );
}
