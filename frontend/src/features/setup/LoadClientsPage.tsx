import { useState } from 'react';
import { api, errorText } from '../../api/client';
import type { LoadPreview, LoadResult, MatchStatus } from '../../api/types';
import { useJob } from '../../api/useJob';
import { formatMoney } from '../../format';

const STATUS_TEXT: Record<MatchStatus, string> = {
  MATCHED: 'same name',
  REMEMBERED: 'remembered',
  SEVERAL: 'several RMs have this name: choose',
  UNKNOWN: 'not found: choose',
};

/**
 * Load clients and their starting RM from the Investwell MF file. Step 1 shows what would happen (nothing saved);
 * step 2 loads it in one go.
 */
export function LoadClientsPage() {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<LoadPreview | null>(null);
  const [from, setFrom] = useState('2026-08-01');
  const [rmChoices, setRmChoices] = useState<Record<string, number>>({});
  const [partnerNames, setPartnerNames] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [jobId, setJobId] = useState<number | null>(null);
  const { job, error: jobError, running } = useJob<LoadResult>(jobId);

  async function check() {
    if (!file) return;
    setError(null);
    setBusy(true);
    setJobId(null);
    const form = new FormData();
    form.append('file', file);
    try {
      const p = await api<LoadPreview>('/api/client-load/preview', { method: 'POST', body: form });
      setPreview(p);
      setRmChoices({});
      setPartnerNames({});
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
      const body = { token: preview.token, fileName: preview.fileName, from, rmChoices, partnerNames };
      const { jobId: id } = await api<{ jobId: number }>('/api/client-load', { method: 'POST', body });
      setJobId(id);
    } catch (e) {
      setError(errorText(e));
    }
  }

  const unresolved = preview ? preview.rms.filter((r) => r.rmId === null && !rmChoices[r.spelling]) : [];
  const waiting = unresolved.reduce((n, r) => n + r.clients, 0);

  return (
    <section>
      <h1>Load clients</h1>
      <p className="muted">Upload the Investwell MF income file. Clients are created with the RM shown in the file, from the date you choose.</p>
      <div className="upload">
        <input type="file" accept=".xlsx" aria-label="Investwell MF file" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        <button type="button" className="secondary" disabled={!file || busy} onClick={check}>
          {busy ? 'Checking…' : 'Check file'}
        </button>
      </div>
      {error && <p className="error">{error}</p>}

      {preview && (
        <>
          <h2>{preview.fileName}</h2>
          {preview.errors.length > 0 ? (
            <div className="result">
              <p className="error" role="status">
                The file has problems and cannot be loaded.
              </p>
              <ul>
                {preview.errors.map((e, i) => (
                  <li key={i}>
                    {e.row ? `Row ${e.row}: ` : ''}
                    {e.message}
                  </li>
                ))}
              </ul>
            </div>
          ) : (
            <p className="ok" role="status">
              {preview.rows} client rows add up to the file's Total: gross {formatMoney(preview.totals.gross)}, GST{' '}
              {formatMoney(preview.totals.gst)}, net {formatMoney(preview.totals.net)}.
            </p>
          )}
          <p>
            <strong>{preview.newClients}</strong> new clients, {preview.existingClients} already loaded. {preview.withoutPan} without PAN.
            {preview.warningCount > 0 && ` ${preview.warningCount} GST warnings.`}
          </p>

          <h3>RMs in the file</h3>
          <table className="compact">
            <thead>
              <tr>
                <th>In the file</th>
                <th>Clients</th>
                <th>Net</th>
                <th>Goes to</th>
              </tr>
            </thead>
            <tbody>
              {preview.rms.map((r) => (
                <tr key={r.spelling}>
                  <td>{r.spelling || <span className="muted">(blank)</span>}</td>
                  <td>{r.clients}</td>
                  <td className="num">{formatMoney(r.net)}</td>
                  <td>
                    {r.rmId !== null ? (
                      <>
                        {r.rmLabel} <span className="muted">({STATUS_TEXT[r.status]})</span>
                      </>
                    ) : (
                      <select
                        aria-label={`RM for ${r.spelling}`}
                        value={rmChoices[r.spelling] ?? ''}
                        onChange={(e) => setRmChoices({ ...rmChoices, [r.spelling]: Number(e.target.value) })}
                      >
                        <option value="">Leave without RM for now</option>
                        {(r.candidates.length > 0 ? r.candidates : preview.allRms).map((c) => (
                          <option key={c.id} value={c.id}>
                            {c.label}
                          </option>
                        ))}
                      </select>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {preview.subBrokers.length > 0 && (
            <>
              <h3>Sub-brokers in the file</h3>
              <p className="muted">Each becomes a sub-broker partner. To merge a spelling into another, type the other name.</p>
              <datalist id="sub-broker-names">
                {preview.subBrokers.map((s) => (
                  <option key={s.spelling} value={s.partnerName ?? s.spelling} />
                ))}
              </datalist>
              <table className="compact">
                <thead>
                  <tr>
                    <th>In the file</th>
                    <th>Clients</th>
                    <th>Net</th>
                    <th>Partner</th>
                  </tr>
                </thead>
                <tbody>
                  {preview.subBrokers.map((s) => (
                    <tr key={s.spelling}>
                      <td>{s.spelling}</td>
                      <td>{s.clients}</td>
                      <td className="num">{formatMoney(s.net)}</td>
                      <td>
                        <input
                          list="sub-broker-names"
                          aria-label={`Partner for ${s.spelling}`}
                          value={partnerNames[s.spelling] ?? s.partnerName ?? s.spelling}
                          onChange={(e) => setPartnerNames({ ...partnerNames, [s.spelling]: e.target.value })}
                        />
                        {s.partnerId !== null && <span className="muted"> ({STATUS_TEXT[s.status]})</span>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}

          {preview.errors.length === 0 && (
            <div className="upload">
              <label className="inline">
                RM from
                <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} required />
              </label>
              <button type="button" disabled={running || !from} onClick={load}>
                Load {preview.newClients} clients
              </button>
              {waiting > 0 && <span className="muted">{waiting} clients will wait as "needs RM".</span>}
            </div>
          )}
        </>
      )}

      {running && <p className="muted">Loading… this can take a minute.</p>}
      {jobError && <p className="error">{jobError}</p>}
      {job?.status === 'FAILED' && <p className="error">Nothing was loaded: {job.error}</p>}
      {job?.status === 'DONE' && job.result && (
        <div className="result">
          <p className="ok" role="status">
            Loaded {job.result.clientsCreated} clients ({job.result.clientsAlreadyThere} were already there). {job.result.rmAssigned} got their RM;{' '}
            {job.result.needsRm} need an RM. {job.result.partnersCreated} sub-brokers created, {job.result.spellingsRemembered} spellings remembered.
          </p>
        </div>
      )}
    </section>
  );
}
