import { CancelEntry, ClientFieldset, MonthRmFieldset, useClientAndRm } from '../entry/ClientAndRm';
import { useState, type FormEvent } from 'react';
import { useTableView } from '../../components/useTableView';
import { api } from '../../api/client';
import type { NpsCertificate, NpsEntry, TaxRate } from '../../api/types';
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

/** The revenue as the server works it out (R-86): POP charges × sharing % when both are given, else as typed. */
export function npsFigures(popCharges: number | null, sharePct: number | null, typed: number, gstPct: number) {
  const revenue = popCharges != null && sharePct != null ? round2((popCharges * sharePct) / 100) : round2(typed);
  const gst = round2((revenue * gstPct) / 100);
  const cgst = round2(gst / 2);
  return { revenue, cgst, sgst: round2(gst - cgst), gst, invoice: round2(revenue + gst) };
}

const CERTIFICATE: Record<NpsCertificate, string> = { PENDING: 'Pending', RECEIVED: 'Received', NOT_APPLICABLE: 'Not applicable' };
const thisMonth = () => todayIso().slice(0, 7);
const PAYMENTS = { PENDING: 'Revenue not received', RECEIVED: 'Received' };

/** NPS: one row per investment, as in the NPS register, with its revenue when there is one (entry only for now). */
export function NpsPage({ embedded = false }: { embedded?: boolean } = {}) {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const query = useFilterQuery();
  const list = useLoad<NpsEntry[]>(`/api/nps${query}`);
  const pops = useLoad<string[]>('/api/nps/pops');
  const [panel, setPanel] = useEntryPanel();
  const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
  const active = (list.data ?? []).filter((e) => e.status === 'ACTIVE');
  const done = () => {
    setPanel(null);
    list.reload();
    pops.reload();
  };
  const view = useTableView(list.data, { month: (e) => e.month, region: (e) => e.region, client: (e) => e.clientName, rm: (e) => e.rmName, partner_pop: (e) => e.pop, inv_date: (e) => e.investmentDate, investment_amt: (e) => e.amount, invoice_w_o_gst: (e) => e.revenue, invoice_with_gst: (e) => e.invoice, amt_received: (e) => e.receivedOn });
  return (
    <section>
      <div className="page-head" style={embedded ? { justifyContent: 'flex-end', marginBottom: 12 } : undefined}>
        {!embedded && <h1>NPS</h1>}
        {canEdit && (
          <div className="actions">
            <a className="button-link secondary" href={`/api/nps/export.xlsx${query}`} download>
              Download Excel
            </a>
            <button type="button" onClick={() => setPanel('new')}>
              Add NPS investment
            </button>
          </div>
        )}
      </div>
      <p className="muted">NPS is entry only for now: its revenue is kept here but not yet in month processing or the END file.</p>
      <ListFilters
        searchHint="Client, PAN, code or POP"
        platformKey="pop"
        platformLabel="POP"
        platforms={(pops.data ?? []).map((p) => ({ value: p, label: p }))}
        payments={PAYMENTS}
      />
      {list.error && <p className="error">{list.error}</p>}
      {list.data && list.data.length === 0 && <p className="muted">No NPS investments match.</p>}
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
              {view.th('partner_pop', 'Partner (POP)')}
              {view.th('inv_date', 'INV date')}
              {view.th('investment_amt', 'Investment amt', 'num')}
              <th>Revenue remarks</th>
              {view.th('invoice_w_o_gst', 'Invoice w/o GST', 'num')}
              {view.th('invoice_with_gst', 'Invoice with GST', 'num')}
              {view.th('amt_received', 'Amt received')}
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
                </td>
                <td>{e.rmName}</td>
                <td>{e.pop}</td>
                <td>{formatDate(e.investmentDate)}</td>
                <td className="num">
                  <Amount value={e.amount} />
                </td>
                <td className="muted">{e.revenueRemarks ?? ''}</td>
                <td className="num">{e.revenue > 0 ? formatMoney(e.revenue) : ''}</td>
                <td className="num">{e.revenue > 0 ? formatMoney(e.invoice) : ''}</td>
                <td>{e.receivedOn ? formatDate(e.receivedOn) : e.revenue > 0 ? <span className="badge warn">Pending</span> : ''}</td>
              </tr>
            ))}
            <tr className="total">
              <td colSpan={7}>Total ({active.length} investments)</td>
              <td className="num">
                <Amount value={active.reduce((s, e) => s + e.amount, 0)} />
              </td>
              <td />
              <td className="num">
                <Amount value={active.reduce((s, e) => s + e.revenue, 0)} />
              </td>
              <td className="num">
                <Amount value={active.reduce((s, e) => s + e.invoice, 0)} />
              </td>
              <td />
            </tr>
          </tbody>
        </table>
</div>
{view.more}
</>
      )}
      {panel === 'new' && (
        <Modal title="Add NPS investment" onClose={() => setPanel(null)}>
          <NpsForm month={thisMonth()} onDone={done} />
        </Modal>
      )}
      {editing && (
        <Modal title={`${editing.clientName} — ${editing.pop}`} onClose={() => setPanel(null)}>
          {canEdit && editing.status === 'ACTIVE' ? (
            <NpsForm month={thisMonth()} entry={editing} onDone={done} />
          ) : (
            <dl className="facts">
              <dt>POP</dt>
              <dd>{editing.pop}</dd>
              <dt>Amount</dt>
              <dd>{formatMoney(editing.amount)}</dd>
            </dl>
          )}
        </Modal>
      )}
    </section>
  );
}

/** Add or edit one NPS investment, in the register's order, with a Revenue section. */
function NpsForm({ month: listMonth, entry, onDone }: { month: string; entry?: NpsEntry; onDone: () => void }) {
  const party = useClientAndRm(entry, entry ? entry.month : listMonth);
  const { month, monthDay, clientMode } = party;
  const [pop, setPop] = useState(entry?.pop ?? '');
  const [investmentDate, setInvestmentDate] = useState(entry?.investmentDate ?? '');
  const [amount, setAmount] = useState(entry ? indianDigits(String(entry.amount)) : '');
  const [welcomeMail, setWelcomeMail] = useState(entry?.welcomeMail ?? false);
  const [investmentMail, setInvestmentMail] = useState(entry?.investmentMail ?? false);
  const [certificate, setCertificate] = useState<NpsCertificate>(entry?.certificate ?? 'PENDING');
  const [onPortal, setOnPortal] = useState(entry?.onPortal ?? false);
  const [revenueRemarks, setRevenueRemarks] = useState(entry?.revenueRemarks ?? '');
  const [popCharges, setPopCharges] = useState(entry?.popCharges != null ? String(entry.popCharges) : '');
  const [sharePct, setSharePct] = useState(entry?.sharePercent != null ? String(entry.sharePercent) : '');
  const [revenue, setRevenue] = useState(entry && entry.popCharges == null && entry.revenue > 0 ? String(entry.revenue) : '');
  const [receivedOn, setReceivedOn] = useState(entry?.receivedOn ?? '');
  const [remarks, setRemarks] = useState(entry?.remarks ?? '');
  const { busy, error, run } = useSubmit();

  const pops = useLoad<string[]>('/api/nps/pops');
  const tax = useLoad<TaxRate>(`/api/product-taxes/NPS/on?date=${monthDay}`);


  const num = (s: string) => Number(s.replace(/,/g, '')) || 0;
  const fromCharges = popCharges !== '' && sharePct !== '';
  const gstPct = entry && entry.month === month.slice(0, 7) ? entry.gstPercent : (tax.data?.gstPercent ?? 0);
  const fig = npsFigures(fromCharges ? num(popCharges) : null, fromCharges ? num(sharePct) : null, num(revenue), gstPct);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const body = {
    ...party.body(),
      pop,
      investmentDate: investmentDate || null,
      amount: amount === '' ? null : num(amount),
      welcomeMail,
      investmentMail,
      certificate,
      onPortal,
      revenueRemarks,
      popCharges: popCharges === '' ? null : num(popCharges),
      sharePercent: sharePct === '' ? null : num(sharePct),
      revenue: fromCharges || revenue === '' ? null : num(revenue),
      receivedOn: receivedOn || null,
      remarks,
    };
    const ok = await run(() => (entry ? api(`/api/nps/${entry.id}`, { method: 'PUT', body }) : api('/api/nps', { method: 'POST', body })));
    if (ok) onDone();
  }

  return (
    <form onSubmit={submit} className="fd-form">
      {tax.error && <p className="error">{tax.error}</p>}
      <ClientFieldset party={party} radioName="nps-client" />

      <MonthRmFieldset party={party} />

      <fieldset>
        <legend>Investment</legend>
        <label>
          Partner (POP)
          <input value={pop} onChange={(e) => setPop(e.target.value)} list="nps-pops" placeholder="e.g. STOCK HOLDING" required />
        </label>
        <datalist id="nps-pops">
          {['STOCK HOLDING', 'CAMS', 'ICICI NPS', ...(pops.data ?? [])]
            .filter((v, i, a) => a.findIndex((x) => x.toLowerCase() === v.toLowerCase()) === i)
            .map((n) => (
              <option key={n} value={n} />
            ))}
        </datalist>
        <label>
          INV date
          <input type="date" value={investmentDate} onChange={(e) => setInvestmentDate(e.target.value)} required />
        </label>
        <label>
          Investment amt (₹)
          <input value={amount} onChange={(e) => setAmount(indianDigits(e.target.value))} inputMode="decimal" required />
        </label>
        <label className="check">
          <input type="checkbox" checked={welcomeMail} onChange={(e) => setWelcomeMail(e.target.checked)} />
          Welcome mail
        </label>
        <label className="check">
          <input type="checkbox" checked={investmentMail} onChange={(e) => setInvestmentMail(e.target.checked)} />
          Investment mail
        </label>
        <label>
          Certificate status
          <select value={certificate} onChange={(e) => setCertificate(e.target.value as NpsCertificate)}>
            {Object.entries(CERTIFICATE).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <label className="check">
          <input type="checkbox" checked={onPortal} onChange={(e) => setOnPortal(e.target.checked)} />
          Entry status: updated on app
        </label>
      </fieldset>

      <fieldset>
        <legend>Revenue</legend>
        <label>
          Revenue remarks
          <input value={revenueRemarks} onChange={(e) => setRevenueRemarks(e.target.value)} placeholder="e.g. sharing will 60% of POP Charges / NO REVENUE" />
        </label>
        <span className="inline-form">
          <label>
            POP charges (₹)
            <input type="number" min={0} step="0.01" value={popCharges} onChange={(e) => setPopCharges(e.target.value)} />
          </label>
          <label>
            Sharing %
            <input type="number" min={0} max={100} step="0.01" value={sharePct} onChange={(e) => setSharePct(e.target.value)} />
          </label>
        </span>
        <label>
          Invoice amt without GST (₹)
          {fromCharges ? (
            <input value={formatMoney(fig.revenue)} readOnly />
          ) : (
            <input type="number" min={0} step="0.01" value={revenue} onChange={(e) => setRevenue(e.target.value)} placeholder="or POP charges × sharing %" />
          )}
        </label>
        <dl className="facts">
          <dt>CGST ({gstPct / 2}%)</dt>
          <dd>{formatMoney(fig.cgst)}</dd>
          <dt>SGST ({gstPct / 2}%)</dt>
          <dd>{formatMoney(fig.sgst)}</dd>
          <dt>Invoice amt with GST</dt>
          <dd>
            <strong>{formatMoney(fig.invoice)}</strong>
          </dd>
        </dl>
        <label>
          Amt received date
          <input type="date" value={receivedOn} onChange={(e) => setReceivedOn(e.target.value)} />
        </label>
        <label>
          Remarks
          <textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} rows={2} />
        </label>
      </fieldset>

      {error && <p className="error">{error}</p>}
      <span className="inline-form">
        <button type="submit" disabled={busy || clientMode === 'lookup' || clientMode === 'pick'}>
          {entry ? 'Save changes' : 'Add NPS investment'}
        </button>
        {entry && <CancelEntry url={`/api/nps/${entry.id}/cancel`} question="Why cancel this NPS entry?" onDone={onDone} />}
      </span>
    </form>
  );
}

