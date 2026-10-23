import { CancelEntry, ClientFieldset, MonthRmFieldset, useClientAndRm } from '../entry/ClientAndRm';
import { useEffect, useState, type FormEvent } from 'react';
import { useTableView } from '../../components/useTableView';
import { api } from '../../api/client';
import type { P2pEntry, TaxRate } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { Amount } from '../../components/Amount';
import { ListFilters, useFilterQuery } from '../../components/ListFilters';
import { Modal } from '../../components/Modal';
import { useEntryPanel } from '../../components/useEntryPanel';
import { formatDate, formatMoney, todayIso, formatMonth, indianDigits } from '../../format';

const round2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100;

/** The brokerage figures as the server works them out (R-87). */
export function p2pFigures(brokerage: number, tdsApplied: boolean, tdsPct: number, gstPct: number) {
  const gross = round2(brokerage);
  const tds = tdsApplied ? round2((gross * tdsPct) / 100) : 0;
  const gst = round2((gross * gstPct) / 100);
  return { gross, tds, gst, net: round2(gross - tds), invoice: round2(gross + gst) };
}

const thisMonth = () => todayIso().slice(0, 7);
const PAYMENTS = { PENDING: 'Brokerage not received', RECEIVED: 'Received' };

/** P2P: one row per investment, as in the P2P register; brokerage typed when known (entry only for now). */
export function P2pPage({ embedded = false }: { embedded?: boolean } = {}) {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const query = useFilterQuery();
  const list = useLoad<P2pEntry[]>(`/api/p2p${query}`);
  const suggestions = useLoad<{ platforms: string[]; schemes: string[] }>('/api/p2p/suggestions');
  const [panel, setPanel] = useEntryPanel();
  const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
  const active = (list.data ?? []).filter((e) => e.status === 'ACTIVE');
  const done = () => {
    setPanel(null);
    list.reload();
    suggestions.reload();
  };
  const view = useTableView(list.data, { month: (e) => e.month, region: (e) => e.region, client: (e) => e.clientName, rm: (e) => e.rmName, partner_platform: (e) => e.partnerName, scheme: (e) => e.scheme, roi: (e) => e.roiPercent, amount_invested: (e) => e.amount, start_date: (e) => e.startDate, brokerage: (e) => e.brokerage });
  return (
    <section>
      <div className="page-head" style={embedded ? { justifyContent: 'flex-end', marginBottom: 12 } : undefined}>
        {!embedded && <h1>P2P</h1>}
        {canEdit && (
          <div className="actions">
            <a className="button-link secondary" href={`/api/p2p/export.xlsx${query}`} download>
              Download Excel
            </a>
            <button type="button" onClick={() => setPanel('new')}>
              Add P2P investment
            </button>
          </div>
        )}
      </div>
      <p className="muted">P2P is entry only for now: its brokerage is kept here but not yet in month processing or the END file.</p>
      <ListFilters
        searchHint="Client, PAN, code or platform"
        platformKey="platform"
        platformLabel="Platform"
        platforms={(suggestions.data?.platforms ?? []).map((p) => ({ value: p, label: p }))}
        payments={PAYMENTS}
      />
      {list.error && <p className="error">{list.error}</p>}
      {list.data && list.data.length === 0 && <p className="muted">No P2P investments match.</p>}
      {list.data && list.data.length > 0 && (
        <>
<div className="table-scroll">
<table>
          <thead>
            <tr>
              <th className="num">SR.NO</th>
              {view.th('month', 'Month')}
              {view.th('region', 'Region')}
              {view.th('client', 'Client')}
              {view.th('rm', 'RM')}
              {view.th('partner_platform', 'Partner (platform)')}
              {view.th('scheme', 'Scheme')}
              {view.th('roi', 'ROI %', 'num')}
              {view.th('amount_invested', 'Amount invested', 'num')}
              {view.th('start_date', 'Start date')}
              <th>Payment status</th>
              {view.th('brokerage', 'Brokerage', 'num')}
            </tr>
          </thead>
          <tbody>
            {view.rows.map((e, i) => (
              <tr key={e.id} className={e.status === 'CANCELLED' ? 'muted' : undefined}>
                <td className="num">{i + 1}</td>
                <td>{formatMonth(e.month)}</td>
                <td>{e.region ?? <span className="muted">—</span>}</td>
                <td>
                  <button type="button" className="cell-link" onClick={() => setPanel(e.id)}>
                    {e.clientName}
                  </button>
                  {e.status === 'CANCELLED' && <span className="badge">cancelled</span>}
                  {e.partnerName && <div className="muted">{e.partnerName}</div>}
                </td>
                <td>{e.rmName}</td>
                <td>{e.platform}</td>
                <td>{e.scheme}</td>
                <td className="num">{e.roiPercent ?? ''}</td>
                <td className="num">{e.amount == null ? <span className="muted">—</span> : <Amount value={e.amount} />}</td>
                <td>{e.startDate ? formatDate(e.startDate) : ''}</td>
                <td>{e.receivedOn ? <span className="badge ok">Received {formatDate(e.receivedOn)}</span> : (e.paymentNote ?? '')}</td>
                <td className="num">{e.brokerage == null ? <span className="muted">not known</span> : formatMoney(e.gross)}</td>
              </tr>
            ))}
            <tr className="total">
              <td colSpan={8}>Total ({active.length} investments)</td>
              <td className="num">
                <Amount value={active.reduce((s, e) => s + (e.amount ?? 0), 0)} />
              </td>
              <td colSpan={2} />
              <td className="num">
                <Amount value={active.reduce((s, e) => s + e.gross, 0)} />
              </td>
            </tr>
          </tbody>
        </table>
</div>
{view.more}
</>
      )}
      {panel === 'new' && (
        <Modal title="Add P2P investment" onClose={() => setPanel(null)}>
          <P2pForm month={thisMonth()} onDone={done} />
        </Modal>
      )}
      {editing && (
        <Modal title={`${editing.clientName} — ${editing.platform}`} onClose={() => setPanel(null)}>
          {canEdit && editing.status === 'ACTIVE' ? (
            <P2pForm month={thisMonth()} entry={editing} onDone={done} />
          ) : (
            <dl className="facts">
              <dt>Platform</dt>
              <dd>
                {editing.platform} · {editing.scheme}
              </dd>
              <dt>Amount</dt>
              <dd>{editing.amount == null ? '—' : formatMoney(editing.amount)}</dd>
            </dl>
          )}
        </Modal>
      )}
    </section>
  );
}

/** Add or edit one P2P investment, in the register's order, with a Brokerage section. */
function P2pForm({ month: listMonth, entry, onDone }: { month: string; entry?: P2pEntry; onDone: () => void }) {
  const party = useClientAndRm(entry, entry ? entry.month : listMonth);
  const { month, monthDay, clientMode } = party;
  const [platform, setPlatform] = useState(entry?.platform ?? '');
  const [scheme, setScheme] = useState(entry?.scheme ?? '');
  const [roi, setRoi] = useState(entry?.roiPercent != null ? String(entry.roiPercent) : '');
  const [amount, setAmount] = useState(entry?.amount != null ? indianDigits(String(entry.amount)) : '');
  const [fundsOn, setFundsOn] = useState(entry?.fundsInitiatedOn ?? '');
  const [startDate, setStartDate] = useState(entry?.startDate ?? '');
  const [quarterEnd, setQuarterEnd] = useState(entry?.quarterEndDate ?? '');
  const [onPortal, setOnPortal] = useState(entry?.onPortal ?? false);
  const [brokerage, setBrokerage] = useState(entry?.brokerage != null ? String(entry.brokerage) : '');
  const [tdsApplied, setTdsApplied] = useState(entry?.tdsApplied ?? false);
  const [tdsPct, setTdsPct] = useState(entry ? String(entry.tdsPercent) : '');
  const [paymentNote, setPaymentNote] = useState(entry?.paymentNote ?? '');
  const [receivedOn, setReceivedOn] = useState(entry?.receivedOn ?? '');
  const [revSharing, setRevSharing] = useState(entry?.revSharingStatus ?? '');
  const [remarks, setRemarks] = useState(entry?.remarks ?? '');
  const { busy, error, run } = useSubmit();

  const suggestions = useLoad<{ platforms: string[]; schemes: string[] }>('/api/p2p/suggestions');
  const tax = useLoad<TaxRate>(`/api/product-taxes/P2P/on?date=${monthDay}`);

  useEffect(() => {
    if (!entry && tax.data) {
      setTdsApplied(tax.data.tdsByDefault);
      setTdsPct(String(tax.data.tdsPercent));
    }
  }, [entry, tax.data]);

  const num = (s: string) => Number(s.replace(/,/g, '')) || 0;
  const gstPct = entry && entry.month === month.slice(0, 7) ? entry.gstPercent : (tax.data?.gstPercent ?? 0);
  const fig = p2pFigures(num(brokerage), tdsApplied, num(tdsPct), gstPct);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const body = {
    ...party.body(),
      platform,
      scheme,
      roiPercent: roi === '' ? null : num(roi),
      amount: amount === '' ? null : num(amount),
      fundsInitiatedOn: fundsOn || null,
      startDate: startDate || null,
      quarterEndDate: quarterEnd || null,
      onPortal,
      brokerage: brokerage === '' ? null : num(brokerage),
      tdsApplied,
      tdsPercent: tdsApplied ? num(tdsPct) : 0,
      paymentNote,
      receivedOn: receivedOn || null,
      revSharingStatus: revSharing,
      remarks,
    };
    const ok = await run(() => (entry ? api(`/api/p2p/${entry.id}`, { method: 'PUT', body }) : api('/api/p2p', { method: 'POST', body })));
    if (ok) onDone();
  }

  return (
    <form onSubmit={submit} className="fd-form">
      {tax.error && <p className="error">{tax.error}</p>}
      <ClientFieldset party={party} radioName="p2p-client" />

      <MonthRmFieldset party={party} />

      <fieldset>
        <legend>Investment</legend>
        <label>
          Partner (platform)
          <input value={platform} onChange={(e) => setPlatform(e.target.value)} list="p2p-platforms" placeholder="e.g. INDIA MONEY MART" required />
        </label>
        <datalist id="p2p-platforms">
          {(suggestions.data?.platforms ?? []).map((n) => (
            <option key={n} value={n} />
          ))}
        </datalist>
        <label>
          P2P scheme
          <input value={scheme} onChange={(e) => setScheme(e.target.value)} list="p2p-schemes" placeholder="e.g. YEARLY" required />
        </label>
        <datalist id="p2p-schemes">
          {['YEARLY', 'QUARTERLY', 'MONTHLY', ...(suggestions.data?.schemes ?? [])]
            .filter((v, i, a) => a.findIndex((x) => x.toLowerCase() === v.toLowerCase()) === i)
            .map((n) => (
              <option key={n} value={n} />
            ))}
        </datalist>
        <label>
          ROI %
          <input type="number" min={0} max={100} step="0.01" value={roi} onChange={(e) => setRoi(e.target.value)} placeholder="e.g. 13" />
        </label>
        <label>
          Amount invested (₹)
          <input value={amount} onChange={(e) => setAmount(indianDigits(e.target.value))} inputMode="decimal" />
        </label>
        <label>
          Funds initiated date
          <input type="date" value={fundsOn} onChange={(e) => setFundsOn(e.target.value)} />
        </label>
        <label>
          Start date
          <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
        </label>
        <label>
          Quarterly end date
          <input type="date" value={quarterEnd} onChange={(e) => setQuarterEnd(e.target.value)} />
        </label>
        <label className="check">
          <input type="checkbox" checked={onPortal} onChange={(e) => setOnPortal(e.target.checked)} />
          Entry status: updated on app
        </label>
      </fieldset>

      <fieldset>
        <legend>Brokerage</legend>
        <p className="muted" style={{ marginTop: 0 }}>
          The P2P register has no revenue figures: type the brokerage (excl. GST) when it is known.
        </p>
        <label>
          Brokerage (₹, excl. GST)
          <input type="number" min={0} step="0.01" value={brokerage} onChange={(e) => setBrokerage(e.target.value)} placeholder="not known yet" />
        </label>
        <span className="inline-form">
          <label className="check">
            <input type="checkbox" checked={tdsApplied} onChange={(e) => setTdsApplied(e.target.checked)} />
            TDS
          </label>
          {tdsApplied && (
            <label>
              TDS %
              <input type="number" min={0} max={100} step="0.01" value={tdsPct} onChange={(e) => setTdsPct(e.target.value)} aria-label="TDS %" required />
            </label>
          )}
        </span>
        <dl className="facts">
          <dt>TDS</dt>
          <dd>{formatMoney(fig.tds)}</dd>
          <dt>GST ({gstPct}%)</dt>
          <dd>{formatMoney(fig.gst)}</dd>
          <dt>Net revenue</dt>
          <dd>
            <strong>{formatMoney(fig.net)}</strong>
          </dd>
          <dt>Invoice amount</dt>
          <dd>{formatMoney(fig.invoice)}</dd>
        </dl>
        <label>
          Payment status (as in the register)
          <input value={paymentNote} onChange={(e) => setPaymentNote(e.target.value)} placeholder="e.g. BILL RAISED -13 OCT" />
        </label>
        <label>
          Amt received date
          <input type="date" value={receivedOn} onChange={(e) => setReceivedOn(e.target.value)} />
        </label>
        <label>
          Rev sharing status
          <input value={revSharing} onChange={(e) => setRevSharing(e.target.value)} />
        </label>
        <label>
          Remarks
          <textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} rows={2} />
        </label>
      </fieldset>

      {error && <p className="error">{error}</p>}
      <span className="inline-form">
        <button type="submit" disabled={busy || clientMode === 'lookup' || clientMode === 'pick'}>
          {entry ? 'Save changes' : 'Add P2P investment'}
        </button>
        {entry && <CancelEntry url={`/api/p2p/${entry.id}/cancel`} question="Why cancel this P2P entry?" onDone={onDone} />}
      </span>
    </form>
  );
}

