import { useState } from 'react';
import { api, errorText } from '../../api/client';
import type { ImportResult } from '../../api/types';
import { useJob, jobErrorText } from '../../api/useJob';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { JobProgressBar } from '../../components/JobProgressBar';

/** Upload the masters workbook: "Check file" is a dry run, "Import" applies it (all or nothing). */
export function ImportPage() {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const [file, setFile] = useState<File | null>(null);
  const [jobId, setJobId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { job, error: jobError, running } = useJob<ImportResult>(jobId);

  async function start(dryRun: boolean) {
    if (!file) return;
    setError(null);
    setJobId(null);
    const form = new FormData();
    form.append('file', file);
    form.append('dryRun', String(dryRun));
    try {
      const { jobId: id } = await api<{ jobId: number }>('/api/imports/masters', { method: 'POST', body: form });
      setJobId(id);
    } catch (e) {
      setError(errorText(e));
    }
  }

  const result = job?.result ?? null;

  return (
    <section>
      <h1>Import masters</h1>
      <ol className="steps">
        <li>
          <a href="/api/imports/masters/template" download>
            Download the template
          </a>{' '}
          and fill in the sheets you need (Regions, Branches, Employees, RD, RM-Branch, Partners, Partner-RM). The first sheet explains each column.
        </li>
        <li>Choose the file and press <strong>Check file</strong>: nothing is saved, you see what would change and any problems.</li>
        <li>When the check is clean, press <strong>Import</strong>. The whole file is applied, or nothing is.</li>
      </ol>
      {canEdit ? (
        <div className="upload">
          <input type="file" accept=".xlsx" aria-label="Masters workbook" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          <button type="button" className="secondary" disabled={!file || running} onClick={() => start(true)}>
            Check file
          </button>
          <button type="button" disabled={!file || running} onClick={() => start(false)}>
            Import
          </button>
        </div>
      ) : (
        <p className="muted">Only ADMIN and HO can import.</p>
      )}
      {error && <p className="error">{error}</p>}
      {jobError && <p className="error">{jobError}</p>}
      {running && <JobProgressBar job={job} label="Importing…" />}
      {job?.status === 'FAILED' && <p className="error">The import failed: {jobErrorText(job)}</p>}
      {result && <ImportResultView result={result} />}
    </section>
  );
}

function ImportResultView({ result }: { result: ImportResult }) {
  const ok = result.errors.length === 0;
  let headline: string;
  if (!ok) headline = `${result.errors.length} problem(s) found. Nothing was saved.`;
  else if (result.dryRun) headline = 'The file is clean. Nothing was saved yet: press Import to apply it.';
  else headline = 'Imported.';

  return (
    <div className="result">
      <p className={ok ? 'ok' : 'error'} role="status">
        <strong>{result.fileName}:</strong> {headline}
      </p>
      {result.sheets.length > 0 && (
        <table className="compact">
          <thead>
            <tr>
              <th>Sheet</th>
              <th>{result.applied ? 'Created' : 'Would create'}</th>
              <th>{result.applied ? 'Updated' : 'Would update'}</th>
              <th>Unchanged</th>
            </tr>
          </thead>
          <tbody>
            {result.sheets.map((s) => (
              <tr key={s.sheet}>
                <td>{s.sheet}</td>
                <td>{s.created}</td>
                <td>{s.updated}</td>
                <td>{s.unchanged}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {!ok && (
        <table className="compact">
          <thead>
            <tr>
              <th>Sheet</th>
              <th>Row</th>
              <th>Problem</th>
            </tr>
          </thead>
          <tbody>
            {result.errors.map((e, i) => (
              <tr key={i}>
                <td>{e.sheet}</td>
                <td>{e.row || ''}</td>
                <td>{e.message}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
