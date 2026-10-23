import { CancelEntry, ClientFieldset, MonthRmFieldset, useClientAndRm } from '../entry/ClientAndRm';
import { useEffect, useState, type FormEvent } from 'react';
import { useTableView } from '../../components/useTableView';
import { api } from '../../api/client';
import type {
  PmsCertificate,
  PmsHolding,
  PmsInvestment,
  PmsPayout,
  PmsPayoutFrequency,
  TaxRate,
} from '../../api/types';
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

/** An AMC payout's figures as the server works them out (R-85). */
export function pmsPayoutFigures(amount: number, tdsApplied: boolean, tdsPct: number, gstPct: number) {
  const a = round2(amount);
  const tds = tdsApplied ? round2((a * tdsPct) / 100) : 0;
  const gst = round2((a * gstPct) / 100);
  return { tds, gst, net: round2(a - tds), invoice: round2(a + gst) };
}

const FREQUENCY: Record<PmsPayoutFrequency, string> = { MONTHLY: 'Monthly', QUARTERLY: 'Quarterly', NONE: 'No payout' };
const CERTIFICATE: Record<PmsCertificate, string> = { PENDING: 'Pending', RECEIVED: 'Received', NOT_APPLICABLE: 'Not applicable' };
const thisMonth = () => todayIso().slice(0, 7);

/** PMS (R-85), entry only for now: client investments, and the AMC payouts per invoice period. */
export function PmsPage({ embedded = false }: { embedded?: boolean } = {}) {
  const [view, setView] = useState<'investments' | 'payouts'>('investments');
  return (
    <section>
      {!embedded && <h1>PMS</h1>}
      <div className="segmented" role="group" aria-label="PMS view">
        <button type="button" aria-pressed={view === 'investments'} className={view === 'investments' ? 'on' : undefined} onClick={() => setView('investments')}>
          Investments
        </button>
        <button type="button" aria-pressed={view === 'payouts'} className={view === 'payouts' ? 'on' : undefined} onClick={() => setView('payouts')}>
          AMC payouts
        </button>
      </div>
      <p className="muted">PMS is entry only for now: how AMC payouts reach each client and the END file is decided later.</p>
      {view === 'investments' ? <Investments /> : <Payouts />}
    </section>
  );
}

function Investments() {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const query = useFilterQuery();
  const list = useLoad<PmsInvestment[]>(`/api/pms${query}`);
  const suggestions = useLoad<{ amcs: string[]; strategies: string[] }>('/api/pms/suggestions');
  const [panel, setPanel] = useEntryPanel();
  const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
  const active = (list.data ?? []).filter((e) => e.status === 'ACTIVE' && e.holdingStatus === 'ACTIVE');
  const done = () => {
    setPanel(null);
    list.reload();
    suggestions.reload();
  };
  const view = useTableView(list.data, { month: (e) => e.month, region: (e) => e.region, client: (e) => e.clientName, rm: (e) => e.rmName, partner_amc: (e) => e.partnerName, pms: (e) => e.strategy, inv_amt: (e) => e.amount, investment_date: (e) => e.investmentDate, trail_yr: (e) => e.trailPercent, status: (e) => e.holdingStatus });
  return (
    <>
      {canEdit && (
        <div className="actions" style={{ justifyContent: 'flex-end', marginBottom: 12 }}>
          <a className="button-link secondary" href={`/api/pms/export.xlsx${query}`} download>
            Download Excel
          </a>
          <button type="button" onClick={() => setPanel('new')}>
            Add PMS investment
          </button>
        </div>
      )}
      <ListFilters
        searchHint="Client, PAN, code, AMC or strategy"
        platformKey="amc"
        platformLabel="AMC"
        platforms={(suggestions.data?.amcs ?? []).map((p) => ({ value: p, label: p }))}
        entryFilters={false}
      />
      {list.error && <p className="error">{list.error}</p>}
      {list.data && list.data.length === 0 && <p className="muted">No PMS investments match.</p>}
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
              {view.th('partner_amc', 'Partner (AMC)')}
              {view.th('pms', 'PMS')}
              {view.th('inv_amt', 'Inv amt', 'num')}
              {view.th('investment_date', 'Investment date')}
              {view.th('trail_yr', 'Trail % / yr', 'num')}
              <th>Payout</th>
              {view.th('status', 'Status')}
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
                <td>{e.amc}</td>
                <td>{e.strategy}</td>
                <td className="num">
                  <Amount value={e.amount} />
                </td>
                <td>{formatDate(e.investmentDate)}</td>
                <td className="num">{e.trailPercent ?? ''}</td>
                <td>{FREQUENCY[e.payoutFrequency]}</td>
                <td>
                  {e.holdingStatus === 'REDEEMED' ? (
                    <span className="badge">Redeemed {e.redeemedOn ? formatDate(e.redeemedOn) : ''}</span>
                  ) : (
                    <span className="badge ok">Active</span>
                  )}
                </td>
              </tr>
            ))}
            <tr className="total">
              <td colSpan={7}>Active investments ({active.length})</td>
              <td className="num">
                <Amount value={active.reduce((s, e) => s + e.amount, 0)} />
              </td>
              <td colSpan={4} />
            </tr>
          </tbody>
        </table>
</div>
{view.more}
</>
      )}
      {panel === 'new' && (
        <Modal title="Add PMS investment" onClose={() => setPanel(null)}>
          <InvestmentForm month={thisMonth()} onDone={done} />
        </Modal>
      )}
      {editing && (
        <Modal title={`${editing.clientName} — ${editing.strategy}`} onClose={() => setPanel(null)}>
          {canEdit && editing.status === 'ACTIVE' ? (
            <InvestmentForm month={thisMonth()} entry={editing} onDone={done} />
          ) : (
            <dl className="facts">
              <dt>PMS</dt>
              <dd>
                {editing.amc} · {editing.strategy}
              </dd>
              <dt>Amount</dt>
              <dd>{formatMoney(editing.amount)}</dd>
            </dl>
          )}
        </Modal>
      )}
    </>
  );
}

/** Add or edit one PMS investment, in the register's order. */
function InvestmentForm({ month: listMonth, entry, onDone }: { month: string; entry?: PmsInvestment; onDone: () => void }) {
  const party = useClientAndRm(entry, entry ? entry.month : listMonth);
  const { clientMode } = party;
  const [amc, setAmc] = useState(entry?.amc ?? '');
  const [strategy, setStrategy] = useState(entry?.strategy ?? '');
  const [amount, setAmount] = useState(entry ? indianDigits(String(entry.amount)) : '');
  const [investmentDate, setInvestmentDate] = useState(entry?.investmentDate ?? '');
  const [onPortal, setOnPortal] = useState(entry?.onPortal ?? false);
  const [trail, setTrail] = useState(entry?.trailPercent != null ? String(entry.trailPercent) : '');
  const [frequency, setFrequency] = useState<PmsPayoutFrequency>(entry?.payoutFrequency ?? 'QUARTERLY');
  const [qty, setQty] = useState(entry?.qty != null ? String(entry.qty) : '');
  const [price, setPrice] = useState(entry?.price != null ? String(entry.price) : '');
  const [consideration, setConsideration] = useState(entry?.consideration != null && entry.qty == null ? indianDigits(String(entry.consideration)) : '');
  const [certificate, setCertificate] = useState<PmsCertificate>(entry?.certificate ?? 'PENDING');
  const [investmentMail, setInvestmentMail] = useState(entry?.investmentMail ?? false);
  const [holding, setHolding] = useState<PmsHolding>(entry?.holdingStatus ?? 'ACTIVE');
  const [redeemedOn, setRedeemedOn] = useState(entry?.redeemedOn ?? '');
  const [remarks, setRemarks] = useState(entry?.remarks ?? '');
  const { busy, error, run } = useSubmit();

  const suggestions = useLoad<{ amcs: string[]; strategies: string[] }>('/api/pms/suggestions');


  const num = (s: string) => Number(s.replace(/,/g, '')) || 0;
  const stockValue = num(qty) > 0 && num(price) > 0 ? round2(num(qty) * num(price)) : null;

  async function submit(e: FormEvent) {
    e.preventDefault();
    const body = {
    ...party.body(),
      amc,
      strategy,
      amount: amount === '' ? null : num(amount),
      investmentDate: investmentDate || null,
      onPortal,
      trailPercent: trail === '' ? null : num(trail),
      payoutFrequency: frequency,
      qty: qty === '' ? null : num(qty),
      price: price === '' ? null : num(price),
      consideration: stockValue != null || consideration === '' ? null : num(consideration),
      certificate,
      investmentMail,
      holdingStatus: holding,
      redeemedOn: holding === 'REDEEMED' ? redeemedOn || null : null,
      remarks,
    };
    const ok = await run(() => (entry ? api(`/api/pms/${entry.id}`, { method: 'PUT', body }) : api('/api/pms', { method: 'POST', body })));
    if (ok) onDone();
  }

  return (
    <form onSubmit={submit} className="fd-form">
      <ClientFieldset party={party} radioName="pms-client" />

      <MonthRmFieldset party={party} />

      <fieldset>
        <legend>PMS</legend>
        <label>
          Partner (AMC)
          <input value={amc} onChange={(e) => setAmc(e.target.value)} list="pms-amcs" placeholder="e.g. ICICI PRU" required />
        </label>
        <datalist id="pms-amcs">
          {(suggestions.data?.amcs ?? []).map((n) => (
            <option key={n} value={n} />
          ))}
        </datalist>
        <label>
          PMS (strategy)
          <input value={strategy} onChange={(e) => setStrategy(e.target.value)} list="pms-strategies" placeholder="e.g. ICICI PIPE" required />
        </label>
        <datalist id="pms-strategies">
          {(suggestions.data?.strategies ?? []).map((n) => (
            <option key={n} value={n} />
          ))}
        </datalist>
        <label>
          Inv amt (₹)
          <input value={amount} onChange={(e) => setAmount(indianDigits(e.target.value))} inputMode="decimal" required />
        </label>
        <label>
          Investment date
          <input type="date" value={investmentDate} onChange={(e) => setInvestmentDate(e.target.value)} required />
        </label>
        <label>
          Mark up % (trail per year)
          <input type="number" min={0} max={100} step="0.001" value={trail} onChange={(e) => setTrail(e.target.value)} placeholder="e.g. 1.25" />
        </label>
        <label>
          Payout
          <select value={frequency} onChange={(e) => setFrequency(e.target.value as PmsPayoutFrequency)}>
            {Object.entries(FREQUENCY).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <label className="check">
          <input type="checkbox" checked={onPortal} onChange={(e) => setOnPortal(e.target.checked)} />
          Entry: updated on app
        </label>
      </fieldset>

      <fieldset>
        <legend>Stock transfer (if any)</legend>
        <label>
          Qty
          <input type="number" min={0} step="0.0001" value={qty} onChange={(e) => setQty(e.target.value)} />
        </label>
        <label>
          Price (₹)
          <input type="number" min={0} step="0.0001" value={price} onChange={(e) => setPrice(e.target.value)} />
        </label>
        <label>
          Consideration investment amount (₹)
          {stockValue != null ? (
            <input value={formatMoney(stockValue)} readOnly />
          ) : (
            <input value={consideration} onChange={(e) => setConsideration(indianDigits(e.target.value))} inputMode="decimal" placeholder="or qty × price" />
          )}
        </label>
      </fieldset>

      <fieldset>
        <legend>Status</legend>
        <label>
          Certificate status
          <select value={certificate} onChange={(e) => setCertificate(e.target.value as PmsCertificate)}>
            {Object.entries(CERTIFICATE).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <label className="check">
          <input type="checkbox" checked={investmentMail} onChange={(e) => setInvestmentMail(e.target.checked)} />
          Investment mail sent
        </label>
        <label>
          Holding
          <select value={holding} onChange={(e) => setHolding(e.target.value as PmsHolding)}>
            <option value="ACTIVE">Active</option>
            <option value="REDEEMED">Redeemed</option>
          </select>
        </label>
        {holding === 'REDEEMED' && (
          <label>
            Redeemed on
            <input type="date" value={redeemedOn} onChange={(e) => setRedeemedOn(e.target.value)} required />
          </label>
        )}
        <label>
          Remarks
          <textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} rows={2} placeholder="e.g. Commission will be received in Aug" />
        </label>
      </fieldset>

      {error && <p className="error">{error}</p>}
      <span className="inline-form">
        <button type="submit" disabled={busy || clientMode === 'lookup' || clientMode === 'pick'}>
          {entry ? 'Save changes' : 'Add PMS investment'}
        </button>
        {entry && <CancelEntry url={`/api/pms/${entry.id}/cancel`} question="Why cancel this PMS investment?" label="Cancel this PMS investment" onDone={onDone} />}
      </span>
    </form>
  );
}

function Payouts() {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const list = useLoad<PmsPayout[]>('/api/pms/payouts');
  const [open, setOpen] = useState<PmsPayout | 'new' | null>(null);
  const active = (list.data ?? []).filter((p) => p.status === 'ACTIVE');
  const done = () => {
    setOpen(null);
    list.reload();
  };
  const period = (p: PmsPayout) => (p.periodFrom === p.periodTo ? formatMonth(p.periodFrom) : `${formatMonth(p.periodFrom)} to ${formatMonth(p.periodTo)}`);
  return (
    <>
      {canEdit && (
        <div className="actions" style={{ justifyContent: 'flex-end', marginBottom: 12 }}>
          <button type="button" onClick={() => setOpen('new')}>
            Add AMC payout
          </button>
        </div>
      )}
      {list.error && <p className="error">{list.error}</p>}
      {list.data && list.data.length === 0 && <p className="muted">No AMC payouts yet.</p>}
      {list.data && list.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>AMC</th>
              <th>Invoice period</th>
              <th className="num">Amt</th>
              <th className="num">Less TDS</th>
              <th className="num">GST</th>
              <th className="num">Net</th>
              <th className="num">Invoice</th>
              <th>Amt received</th>
            </tr>
          </thead>
          <tbody>
            {list.data.map((p) => (
              <tr key={p.id}>
                <td>
                  <button type="button" className="cell-link" onClick={() => setOpen(p)}>
                    {p.amc}
                  </button>
                </td>
                <td>{period(p)}</td>
                <td className="num">{formatMoney(p.amount)}</td>
                <td className="num">{formatMoney(p.tds)}</td>
                <td className="num">{formatMoney(p.gst)}</td>
                <td className="num">{formatMoney(p.net)}</td>
                <td className="num">{formatMoney(p.invoice)}</td>
                <td>{p.receivedOn ? formatDate(p.receivedOn) : <span className="badge warn">Pending</span>}</td>
              </tr>
            ))}
            <tr className="total">
              <td colSpan={2}>Total ({active.length} payouts)</td>
              <td className="num">
                <Amount value={active.reduce((s, p) => s + p.amount, 0)} />
              </td>
              <td className="num">
                <Amount value={active.reduce((s, p) => s + p.tds, 0)} />
              </td>
              <td className="num">
                <Amount value={active.reduce((s, p) => s + p.gst, 0)} />
              </td>
              <td className="num">
                <Amount value={active.reduce((s, p) => s + p.net, 0)} />
              </td>
              <td className="num">
                <Amount value={active.reduce((s, p) => s + p.invoice, 0)} />
              </td>
              <td />
            </tr>
          </tbody>
        </table>
      )}
      {open && (
        <Modal title={open === 'new' ? 'Add AMC payout' : `${open.amc} — ${period(open)}`} onClose={() => setOpen(null)} size="narrow">
          {canEdit ? <PayoutForm payout={open === 'new' ? undefined : open} onDone={done} /> : <p className="muted">View only.</p>}
        </Modal>
      )}
    </>
  );
}

/** Add or edit one AMC payout: AMC, invoice period, amount, TDS, received date. */
function PayoutForm({ payout, onDone }: { payout?: PmsPayout; onDone: () => void }) {
  const [amc, setAmc] = useState(payout?.amc ?? '');
  const [from, setFrom] = useState(payout?.periodFrom ?? thisMonth());
  const [to, setTo] = useState(payout?.periodTo ?? thisMonth());
  const [amount, setAmount] = useState(payout ? String(payout.amount) : '');
  const [tdsApplied, setTdsApplied] = useState(payout?.tdsApplied ?? true);
  const [tdsPct, setTdsPct] = useState(payout ? String(payout.tdsPercent) : '');
  const [invoiceRaised, setInvoiceRaised] = useState(payout?.invoiceRaised ?? false);
  const [receivedOn, setReceivedOn] = useState(payout?.receivedOn ?? '');
  const [remarks, setRemarks] = useState(payout?.remarks ?? '');
  const suggestions = useLoad<{ amcs: string[]; strategies: string[] }>('/api/pms/suggestions');
  const tax = useLoad<TaxRate>(`/api/product-taxes/PMS/on?date=${to || from}-01`);
  const { busy, error, run } = useSubmit();
  useEffect(() => {
    if (!payout && tax.data) {
      setTdsApplied(tax.data.tdsByDefault);
      setTdsPct(String(tax.data.tdsPercent));
    }
  }, [payout, tax.data]);
  const gstPct = payout && payout.periodTo === to ? payout.gstPercent : (tax.data?.gstPercent ?? 0);
  const fig = pmsPayoutFigures(Number(amount) || 0, tdsApplied, Number(tdsPct) || 0, gstPct);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const body = {
      amc,
      periodFrom: `${from}-01`,
      periodTo: `${to || from}-01`,
      amount: amount === '' ? null : Number(amount),
      tdsApplied,
      tdsPercent: tdsApplied ? Number(tdsPct) : 0,
      invoiceRaised,
      receivedOn: receivedOn || null,
      remarks,
    };
    if (await run(() => (payout ? api(`/api/pms/payouts/${payout.id}`, { method: 'PUT', body }) : api('/api/pms/payouts', { method: 'POST', body })))) onDone();
  };

  return (
    <form onSubmit={submit} className="fd-form">
      <label>
        AMC
        <input value={amc} onChange={(e) => setAmc(e.target.value)} list="pms-payout-amcs" placeholder="e.g. Motilal AMC" required />
      </label>
      <datalist id="pms-payout-amcs">
        {(suggestions.data?.amcs ?? []).map((n) => (
          <option key={n} value={n} />
        ))}
      </datalist>
      <span className="inline-form">
        <label>
          Invoice period from
          <input type="month" value={from} onChange={(e) => setFrom(e.target.value)} required />
        </label>
        <label>
          to
          <input type="month" value={to} onChange={(e) => setTo(e.target.value)} />
        </label>
      </span>
      <label>
        Amt (₹, excl. GST)
        <input type="number" min={0} step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required />
      </label>
      <span className="inline-form">
        <label className="check">
          <input type="checkbox" checked={tdsApplied} onChange={(e) => setTdsApplied(e.target.checked)} />
          Less TDS
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
        <dt>Net</dt>
        <dd>
          <strong>{formatMoney(fig.net)}</strong>
        </dd>
        <dt>Invoice amount</dt>
        <dd>{formatMoney(fig.invoice)}</dd>
      </dl>
      <label className="check">
        <input type="checkbox" checked={invoiceRaised} onChange={(e) => setInvoiceRaised(e.target.checked)} />
        Invoice raised
      </label>
      <label>
        Amt received date
        <input type="date" value={receivedOn} onChange={(e) => setReceivedOn(e.target.value)} />
      </label>
      <label>
        Remarks
        <textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} rows={2} />
      </label>
      {error && <p className="error">{error}</p>}
      <span className="inline-form">
        <button type="submit" disabled={busy}>
          {payout ? 'Save changes' : 'Add AMC payout'}
        </button>
        {payout && <CancelEntry url={`/api/pms/payouts/${payout.id}/cancel`} question="Why cancel this payout?" label="Cancel this payout" onDone={onDone} />}
      </span>
    </form>
  );
}

