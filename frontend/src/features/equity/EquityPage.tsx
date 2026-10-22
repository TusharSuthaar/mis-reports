import { useState } from 'react';
import { useTableView } from '../../components/useTableView';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { Amount } from '../../components/Amount';
import { ListFilters, useFilterQuery } from '../../components/ListFilters';
import { Modal } from '../../components/Modal';
import { formatDate, formatDateTime, formatMoney } from '../../format';
import { formatMonth } from '../fd/FdPage';

/** One client in one month, from the month's broker report (R-89). clientId null = PAN not in Clients yet. */
export interface EquityClientMonth {
  month: string;
  clientId: number | null;
  clientName: string | null;
  pan: string;
  partyCode: string;
  rmName: string | null;
  branchName: string | null;
  trades: number;
  exchanges: string | null;
  gross: number;
  share: number;
  sharePercent: number | null;
}

export interface EquityUpload {
  id: number;
  month: string;
  fileName: string;
  broker: string | null;
  trades: number;
  clients: number;
  grossTotal: number;
  shareTotal: number;
  status: 'ACTIVE' | 'REPLACED';
  uploadedBy: string;
  uploadedAt: string;
}

interface UploadResult {
  month: string;
  trades: number;
  clients: number;
  clientsFound: number;
  clientsNotFound: number;
  grossTotal: number;
  shareTotal: number;
  replacedUploadId: number | null;
}

interface Trade {
  date: string;
  exchange: string | null;
  gross: number;
  share: number;
}

const EXCHANGE: Record<string, string> = { N: 'NSE', B: 'BSE' };

/** Equity: the broker's monthly Brokerage Due report (SMC), kept trade by trade and shown per client per month (R-89). */
export function EquityPage() {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const query = useFilterQuery();
  const list = useLoad<EquityClientMonth[]>(`/api/equity${query}`);
  const uploads = useLoad<EquityUpload[]>('/api/equity/uploads');
  const [open, setOpen] = useState<EquityClientMonth | null>(null);
  const rows = list.data ?? [];
  const notFound = rows.filter((r) => r.clientId == null);
  const sum = (k: 'gross' | 'share') => rows.reduce((s, r) => s + r[k], 0);
  const reload = () => {
    list.reload();
    uploads.reload();
  };
  const view = useTableView(rows, { month: (r) => r.month, client: (r) => r.clientName, party_code: (r) => r.partyCode, rm_branch: (r) => r.rmName, trades: (r) => r.trades, gross_recd: (r) => r.gross, franchise_share: (r) => r.share, share: (r) => r.sharePercent });
  return (
    <section>
      <p className="muted">
        Equity brokerage comes from the broker&rsquo;s monthly <strong>Brokerage Due</strong> report (SMC). Epsilon&rsquo;s revenue is the{' '}
        <strong>franchise share</strong>; it goes into the END&rsquo;s Equity column when the month is processed, shared with the client&rsquo;s own
        partner %.
      </p>
      {canEdit && <EquityUploadForm onDone={reload} />}
      <ListFilters searchHint="Client name, full PAN or party code" entryFilters={false} partnerFilter={false} />
      {list.error && <p className="error">{list.error}</p>}
      {list.data && (
        <div className="cards">
          <div className="stat-card">
            <span className="label">Franchise share (Epsilon)</span>
            <span className="value">
              ₹ <Amount value={sum('share')} />
            </span>
            <span className="hint">{rows.length} client-months</span>
          </div>
          <div className="stat-card">
            <span className="label">Gross brokerage</span>
            <span className="value">
              ₹ <Amount value={sum('gross')} />
            </span>
            <span className="hint">{rows.reduce((s, r) => s + r.trades, 0)} trades</span>
          </div>
        </div>
      )}
      {notFound.length > 0 && (
        <p className="error">
          {notFound.length} PAN{notFound.length === 1 ? ' is' : 's are'} not in Clients yet ({notFound.map((r) => r.pan).join(', ')}). Add them on the{' '}
          <Link to="/clients">Clients</Link> screen: the month cannot be processed until then.
        </p>
      )}
      {list.data && rows.length === 0 && <div className="empty">No equity brokerage yet. Upload the broker&rsquo;s Brokerage Due report above.</div>}
      {rows.length > 0 && (
        <>
<div className="table-scroll">
<table>
          <thead>
            <tr>
              {view.th('month', 'Month')}
              {view.th('client', 'Client')}
              {view.th('party_code', 'Party code')}
              {view.th('rm_branch', 'RM / branch')}
              {view.th('trades', 'Trades', 'num')}
              <th>Exchange</th>
              {view.th('gross_recd', 'Gross recd (₹)', 'num')}
              {view.th('franchise_share', 'Franchise share (₹)', 'num')}
              {view.th('share', 'Share %', 'num')}
            </tr>
          </thead>
          <tbody>
            {view.rows.map((r) => (
              <tr key={`${r.month}-${r.partyCode}-${r.pan}`}>
                <td>{formatMonth(r.month)}</td>
                <td>
                  {r.clientId != null ? (
                    <Link to={`/clients/${r.clientId}`}>{r.clientName}</Link>
                  ) : (
                    <>
                      {r.clientName} <span className="badge warn">not in Clients</span>
                    </>
                  )}
                  <div className="muted mono">{r.pan}</div>
                </td>
                <td>
                  <button type="button" className="cell-link mono" onClick={() => setOpen(r)}>
                    {r.partyCode}
                  </button>
                </td>
                <td>{r.rmName ? `${r.rmName}${r.branchName ? ` — ${r.branchName}` : ''}` : <span className="muted">—</span>}</td>
                <td className="num">{r.trades}</td>
                <td>{(r.exchanges ?? '').split('+').map((x) => EXCHANGE[x] ?? x).join(' + ')}</td>
                <td className="num">{formatMoney(r.gross)}</td>
                <td className="num">{formatMoney(r.share)}</td>
                <td className="num">{r.sharePercent ?? ''}</td>
              </tr>
            ))}
            <tr className="total">
              <td colSpan={6}>Total</td>
              <td className="num">
                <Amount value={sum('gross')} />
              </td>
              <td className="num">
                <Amount value={sum('share')} />
              </td>
              <td />
            </tr>
          </tbody>
        </table>
</div>
{view.more}
</>
      )}

      <h2 style={{ marginTop: 24 }}>Uploads</h2>
      {uploads.data && uploads.data.length === 0 && <p className="muted">No report uploaded yet.</p>}
      {uploads.data && uploads.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Month</th>
              <th>File</th>
              <th className="num">Trades</th>
              <th className="num">Clients</th>
              <th className="num">Gross (₹)</th>
              <th className="num">Franchise share (₹)</th>
              <th>Status</th>
              <th>Uploaded</th>
            </tr>
          </thead>
          <tbody>
            {uploads.data.map((u) => (
              <tr key={u.id} className={u.status === 'REPLACED' ? 'muted' : undefined}>
                <td>{formatMonth(u.month)}</td>
                <td>
                  {u.fileName}
                  {u.broker && <div className="muted">{u.broker}</div>}
                </td>
                <td className="num">{u.trades}</td>
                <td className="num">{u.clients}</td>
                <td className="num">{formatMoney(u.grossTotal)}</td>
                <td className="num">{formatMoney(u.shareTotal)}</td>
                <td>{u.status === 'ACTIVE' ? <span className="badge ok">In use</span> : <span className="badge">Replaced</span>}</td>
                <td className="muted">
                  {u.uploadedBy} · {formatDateTime(u.uploadedAt)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {open && (
        <Modal title={`${open.clientName ?? open.pan} — ${formatMonth(open.month)}`} onClose={() => setOpen(null)} size="narrow">
          <Trades month={open.month} party={open.partyCode} />
        </Modal>
      )}
    </section>
  );
}

function Trades({ month, party }: { month: string; party: string }) {
  const trades = useLoad<Trade[]>(`/api/equity/trades?month=${month}&party=${encodeURIComponent(party)}`);
  if (trades.error) return <p className="error">{trades.error}</p>;
  if (!trades.data) return <p className="muted">Loading…</p>;
  return (
    <table>
      <thead>
        <tr>
          <th>Date</th>
          <th>Exchange</th>
          <th className="num">Gross recd</th>
          <th className="num">Franchise share</th>
        </tr>
      </thead>
      <tbody>
        {trades.data.map((t, i) => (
          <tr key={i}>
            <td>{formatDate(t.date)}</td>
            <td>{EXCHANGE[t.exchange ?? ''] ?? t.exchange}</td>
            <td className="num">{formatMoney(t.gross)}</td>
            <td className="num">{formatMoney(t.share)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/** Upload the broker's report: the month comes from its trade dates; a later upload of a month replaces the earlier one. */
function EquityUploadForm({ onDone }: { onDone: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<UploadResult | null>(null);
  const { busy, error, run } = useSubmit();
  const upload = async () => {
    if (!file) return;
    const form = new FormData();
    form.append('file', file);
    const out: { r?: UploadResult } = {};
    if (await run(async () => { out.r = await api<UploadResult>('/api/equity/upload', { method: 'POST', body: form }); })) {
      setResult(out.r ?? null);
      onDone();
    }
  };
  return (
    <div className="panel-card" style={{ marginBottom: 16 }}>
      <h2 style={{ marginTop: 0 }}>Upload the broker&rsquo;s Brokerage Due report</h2>
      <span className="inline-form">
        <label>
          Equity source file (.xlsx)
          <input type="file" accept=".xlsx,.xls" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        </label>
        <button type="button" disabled={!file || busy} onClick={upload}>
          {busy ? 'Uploading…' : 'Upload'}
        </button>
      </span>
      {error && <p className="error">{error}</p>}
      {result && (
        <p role="status" className="success">
          {formatMonth(result.month)}: {result.trades} trades, {result.clients} clients ({result.clientsFound} found in Clients
          {result.clientsNotFound > 0 ? `, ${result.clientsNotFound} not found` : ''}) · gross {formatMoney(result.grossTotal)} · franchise share{' '}
          <strong>{formatMoney(result.shareTotal)}</strong>
          {result.replacedUploadId != null && ' · replaced the month’s earlier upload'}.
        </p>
      )}
    </div>
  );
}
