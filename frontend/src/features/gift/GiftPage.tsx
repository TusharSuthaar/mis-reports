import { CancelEntry, ClientFieldset, MonthRmFieldset, useClientAndRm } from '../entry/ClientAndRm';
import { useEffect, useState, type FormEvent } from 'react';
import { useTableView } from '../../components/useTableView';
import { api } from '../../api/client';
import {
  GIFT_PAYMENT_LABEL,
  type ExchangeRate,
  type GiftEntry,
  type GiftPaymentStatus,
  type GiftTrxnStatus,
  type TaxRate,
} from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { Amount } from '../../components/Amount';
import { ListFilters, useFilterQuery } from '../../components/ListFilters';
import { Modal } from '../../components/Modal';
import { useEntryPanel } from '../../components/useEntryPanel';
import { formatMoney, todayIso, formatMonth, indianDigits } from '../../format';

const round2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100;

/** The rupee amount and revenue as the server works them out (R-82), for the live preview while typing. */
export function giftFigures(usd: number, rate: number, inrTyped: number, brokerage: number, tdsApplied: boolean, tdsPct: number, gstPct: number) {
  const inr = usd > 0 && rate > 0 ? round2(usd * rate) : inrTyped;
  const gross = round2(brokerage);
  const tds = tdsApplied ? round2((gross * tdsPct) / 100) : 0;
  const gst = round2((gross * gstPct) / 100);
  return { inr, gross, tds, gst, net: round2(gross - tds), invoice: round2(gross + gst) };
}


/** The day a new entry's rate is for: today in the current month, else the month's last day. */
export function defaultRateDay(month: string, today: string): string {
  const ym = month.slice(0, 7);
  if (today.slice(0, 7) === ym) return today;
  const [y, m] = ym.split('-').map(Number);
  const last = new Date(y, m, 0).getDate();
  return `${ym}-${String(last).padStart(2, '0')}`;
}
const thisMonth = () => todayIso().slice(0, 7);
const usd = (n: number | null) => (n == null ? '' : `USD ${n.toLocaleString('en-IN')}`);

/** GIFT CITY: one row per investment, as in the GIFT CITY register; brokerage typed for now. */
export function GiftPage({ embedded = false }: { embedded?: boolean } = {}) {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const query = useFilterQuery();
  const list = useLoad<GiftEntry[]>(`/api/gift${query}`);
  const funds = useLoad<string[]>('/api/gift/funds');
  const [panel, setPanel] = useEntryPanel();
  const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
  const active = (list.data ?? []).filter((e) => e.status === 'ACTIVE');
  const total = (k: 'amountInr' | 'gross' | 'net' | 'invoice') => active.reduce((s, e) => s + (e[k] ?? 0), 0);
  const done = () => {
    setPanel(null);
    list.reload();
    funds.reload();
  };

  const view = useTableView(list.data, { month: (e) => e.month, region: (e) => e.region, client: (e) => e.clientName, rm: (e) => e.rmName, partner_fund: (e) => e.partnerName, amt: (e) => e.amountUsd, amt_in_rs: (e) => e.amountInr, trxn_status: (e) => e.trxnStatus, brokerage: (e) => e.brokerage, invoice: (e) => e.invoice, payment: (e) => e.paymentStatus });
  return (
    <section>
      <div className="page-head" style={embedded ? { justifyContent: 'flex-end', marginBottom: 12 } : undefined}>
        {!embedded && <h1>GIFT CITY</h1>}
        {canEdit && (
          <div className="actions">
            <a className="button-link secondary" href={`/api/gift/export.xlsx${query}`} download>
              Download Excel
            </a>
            <button type="button" onClick={() => setPanel('new')}>
              Add GIFT City entry
            </button>
          </div>
        )}
      </div>
      <ListFilters
        searchHint="Client, PAN, code or fund"
        platformKey="fund"
        platformLabel="Fund"
        platforms={(funds.data ?? []).map((p) => ({ value: p, label: p }))}
        payments={GIFT_PAYMENT_LABEL}
      />
      {list.error && <p className="error">{list.error}</p>}
      {list.data && list.data.length === 0 && <p className="muted">No GIFT City entries match.</p>}
      {list.data && list.data.length > 0 && (
        <>
<div className="table-scroll">
<table>
          <thead>
            <tr>
              <th className="num">SR .NO</th>
              {view.th('month', 'Month')}
              {view.th('region', 'Region')}
              {view.th('client', 'Client')}
              {view.th('rm', 'RM')}
              {view.th('partner_fund', 'Partner (fund)')}
              {view.th('amt', 'AMT', 'num')}
              {view.th('amt_in_rs', 'AMT in Rs.', 'num')}
              <th>Entry status</th>
              {view.th('trxn_status', 'Trxn status')}
              {view.th('brokerage', 'Brokerage', 'num')}
              {view.th('invoice', 'Invoice', 'num')}
              {view.th('payment', 'Payment')}
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
                <td>{e.fund}</td>
                <td className="num">{usd(e.amountUsd)}</td>
                <td className="num">{e.amountInr == null ? '' : <Amount value={e.amountInr} />}</td>
                <td>{e.onPortal ? <span className="badge ok">Updated</span> : <span className="badge warn">Not yet</span>}</td>
                <td>{e.trxnStatus === 'DONE' ? <span className="badge ok">Done</span> : <span className="badge warn">Pending</span>}</td>
                <td className="num">{e.brokerage == null ? <span className="muted">not known</span> : <Amount value={e.gross} />}</td>
                <td className="num">{e.brokerage == null ? '' : <Amount value={e.invoice} />}</td>
                <td>
                  <span className={e.paymentStatus === 'RECEIVED' ? 'badge ok' : 'badge warn'}>{GIFT_PAYMENT_LABEL[e.paymentStatus]}</span>
                </td>
              </tr>
            ))}
            <tr className="total">
              <td colSpan={7}>Total ({active.length} entries)</td>
              <td className="num">
                <Amount value={total('amountInr')} />
              </td>
              <td colSpan={2} />
              <td className="num">
                <Amount value={total('gross')} />
              </td>
              <td className="num">
                <Amount value={total('invoice')} />
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
        <Modal title="Add GIFT City entry" onClose={() => setPanel(null)}>
          <GiftForm month={thisMonth()} onDone={done} />
        </Modal>
      )}
      {editing && (
        <Modal title={`${editing.clientName} — ${editing.fund}`} onClose={() => setPanel(null)}>
          {canEdit && editing.status === 'ACTIVE' ? (
            <GiftForm month={thisMonth()} entry={editing} onDone={done} />
          ) : (
            <dl className="facts">
              <dt>Fund</dt>
              <dd>{editing.fund}</dd>
              <dt>Amount</dt>
              <dd>
                {usd(editing.amountUsd)} {editing.amountInr != null && `· ${formatMoney(editing.amountInr)}`}
              </dd>
              <dt>Brokerage / Invoice</dt>
              <dd>{editing.brokerage == null ? 'not known yet' : `${formatMoney(editing.gross)} / ${formatMoney(editing.invoice)}`}</dd>
            </dl>
          )}
        </Modal>
      )}
    </section>
  );
}

/** Add or edit one GIFT City entry, in the register's order, with a Brokerage section. */
function GiftForm({ month: listMonth, entry, onDone }: { month: string; entry?: GiftEntry; onDone: () => void }) {
  const party = useClientAndRm(entry, entry ? entry.month : listMonth);
  const { month, monthDay, clientMode } = party;
  const [fund, setFund] = useState(entry?.fund ?? '');
  const [amountUsd, setAmountUsd] = useState(entry?.amountUsd != null ? indianDigits(String(entry.amountUsd)) : '');
  const [fxRate, setFxRate] = useState(entry?.fxRate != null ? String(entry.fxRate) : '');
  const [rateDay, setRateDay] = useState(entry?.fxRateDate ?? defaultRateDay(entry ? entry.month : listMonth, todayIso()));
  const [fxSource, setFxSource] = useState(entry?.fxSource ?? '');
  const [fxNote, setFxNote] = useState('');
  const [fetching, setFetching] = useState(false);
  const [amountInr, setAmountInr] = useState(entry?.amountInr != null && entry.fxRate == null ? indianDigits(String(entry.amountInr)) : '');
  const [onPortal, setOnPortal] = useState(entry?.onPortal ?? false);
  const [trxn, setTrxn] = useState<GiftTrxnStatus>(entry?.trxnStatus ?? 'PENDING');
  const [brokerage, setBrokerage] = useState(entry?.brokerage != null ? String(entry.brokerage) : '');
  const [tdsApplied, setTdsApplied] = useState(entry?.tdsApplied ?? false);
  const [tdsPct, setTdsPct] = useState(entry ? String(entry.tdsPercent) : '');
  const [invoiceRaised, setInvoiceRaised] = useState(entry?.invoiceRaised ?? false);
  const [payment, setPayment] = useState<GiftPaymentStatus>(entry?.paymentStatus ?? 'PENDING');
  const [receivedOn, setReceivedOn] = useState(entry?.receivedOn ?? '');
  const [remarks, setRemarks] = useState(entry?.remarks ?? '');
  const { busy, error, run } = useSubmit();

  const funds = useLoad<string[]>('/api/gift/funds');
  const tax = useLoad<TaxRate>(`/api/product-taxes/GIFT/on?date=${monthDay}`);

  useEffect(() => {
    if (!entry && tax.data) {
      setTdsApplied(tax.data.tdsByDefault);
      setTdsPct(String(tax.data.tdsPercent));
    }
  }, [entry, tax.data]);

  // The USD → ₹ rate for the day: from the app's list, or fetched from Frankfurter (ECB) and kept (R-84).
  const fetchRate = async (day: string) => {
    if (!day) return;
    setFetching(true);
    setFxNote('');
    try {
      const r = await api<ExchangeRate>(`/api/exchange-rates/USD/lookup?date=${day}`, { method: 'POST' });
      if (r.rate != null) {
        setFxRate(String(r.rate));
        setFxSource(r.source === 'MANUAL' ? `set by hand for ${r.day}` : `ECB rate of ${r.rateDate}`);
        setFxNote(r.note);
      } else {
        setFxNote(r.note);
      }
    } catch {
      setFxNote('Could not fetch the rate: type it');
    } finally {
      setFetching(false);
    }
  };
  // A new entry with a USD amount and no rate yet gets the day's rate by itself.
  useEffect(() => {
    if (!entry && amountUsd && !fxRate && !fetching && !fxNote) void fetchRate(rateDay);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [amountUsd]);


  const num = (s: string) => Number(s.replace(/,/g, '')) || 0;
  const gstPct = entry && entry.month === month.slice(0, 7) ? entry.gstPercent : (tax.data?.gstPercent ?? 0);
  const fig = giftFigures(num(amountUsd), num(fxRate), num(amountInr), num(brokerage), tdsApplied, num(tdsPct), gstPct);
  const rateGiven = num(amountUsd) > 0 && num(fxRate) > 0;

  async function submit(e: FormEvent) {
    e.preventDefault();
    const body = {
    ...party.body(),
      fund,
      amountUsd: amountUsd === '' ? null : num(amountUsd),
      fxRate: fxRate === '' ? null : num(fxRate),
      fxRateDate: fxRate === '' ? null : rateDay || null,
      fxSource: fxRate === '' ? null : fxSource || 'entered by hand',
      amountInr: rateGiven || amountInr === '' ? null : num(amountInr),
      onPortal,
      trxnStatus: trxn,
      brokerage: brokerage === '' ? null : num(brokerage),
      tdsApplied,
      tdsPercent: tdsApplied ? num(tdsPct) : 0,
      invoiceRaised,
      paymentStatus: payment,
      receivedOn: payment === 'RECEIVED' && receivedOn ? receivedOn : null,
      remarks,
    };
    const ok = await run(() => (entry ? api(`/api/gift/${entry.id}`, { method: 'PUT', body }) : api('/api/gift', { method: 'POST', body })));
    if (ok) onDone();
  }

  return (
    <form onSubmit={submit} className="fd-form">
      {tax.error && <p className="error">{tax.error}</p>}
      <ClientFieldset party={party} radioName="gift-client" />

      <MonthRmFieldset party={party} partnerLabel="Referral partner / sub-broker" />

      <fieldset>
        <legend>Investment</legend>
        <label>
          Partner (fund)
          <input value={fund} onChange={(e) => setFund(e.target.value)} list="gift-funds" placeholder="e.g. DSP GIFT CITY" required />
        </label>
        <datalist id="gift-funds">
          {(funds.data ?? []).map((n) => (
            <option key={n} value={n} />
          ))}
        </datalist>
        <label>
          AMT (USD)
          <input value={amountUsd} onChange={(e) => setAmountUsd(indianDigits(e.target.value))} inputMode="decimal" placeholder="e.g. 10,000" />
        </label>
        <label>
          Rate date
          <input
            type="date"
            value={rateDay}
            onChange={(e) => {
              setRateDay(e.target.value);
              if (e.target.value && amountUsd) void fetchRate(e.target.value);
            }}
          />
        </label>
        <span className="inline-form">
          <label>
            Exchange rate (₹ per USD)
            <input
              type="number"
              min={0}
              step="0.0001"
              value={fxRate}
              onChange={(e) => {
                setFxRate(e.target.value);
                setFxSource('entered by hand');
                setFxNote('');
              }}
              placeholder="e.g. 95.97"
            />
          </label>
          <button type="button" className="secondary" disabled={!rateDay || fetching} onClick={() => fetchRate(rateDay)}>
            {fetching ? 'Fetching…' : 'Fetch rate'}
          </button>
        </span>
        {(fxSource || fxNote) && (
          <p className="muted" style={{ marginTop: 0 }}>
            {fxRate && fxSource ? `Rate: ${fxSource}` : fxNote}
            {fxRate && fxSource !== 'entered by hand' && ' · type over it if the fund statement shows another rate'}
          </p>
        )}
        <label>
          AMT in Rs.
          {rateGiven ? (
            <input value={formatMoney(fig.inr)} readOnly />
          ) : (
            <input value={amountInr} onChange={(e) => setAmountInr(indianDigits(e.target.value))} inputMode="decimal" placeholder="or USD × rate" />
          )}
        </label>
        <label className="check">
          <input type="checkbox" checked={onPortal} onChange={(e) => setOnPortal(e.target.checked)} />
          Entry status: updated on portal
        </label>
        <label>
          Trxn status
          <select value={trxn} onChange={(e) => setTrxn(e.target.value as GiftTrxnStatus)}>
            <option value="PENDING">Pending</option>
            <option value="DONE">Done</option>
          </select>
        </label>
      </fieldset>

      <fieldset>
        <legend>Brokerage</legend>
        <p className="muted" style={{ marginTop: 0 }}>
          How GIFT City pays is not settled yet: type the brokerage (excl. GST) when it is known. TDS and GST are worked out from it.
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
          <dt>Brokerage</dt>
          <dd>{formatMoney(fig.gross)}</dd>
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
      </fieldset>

      <fieldset>
        <legend>Payment</legend>
        <label className="check">
          <input type="checkbox" checked={invoiceRaised} onChange={(e) => setInvoiceRaised(e.target.checked)} />
          Invoice raised by accounts
        </label>
        <label>
          Payment status
          <select value={payment} onChange={(e) => setPayment(e.target.value as GiftPaymentStatus)}>
            {Object.entries(GIFT_PAYMENT_LABEL).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        {payment === 'RECEIVED' && (
          <label>
            Amt received date
            <input type="date" value={receivedOn} onChange={(e) => setReceivedOn(e.target.value)} />
          </label>
        )}
        <label>
          Remarks
          <textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} rows={2} />
        </label>
      </fieldset>

      {error && <p className="error">{error}</p>}
      <span className="inline-form">
        <button type="submit" disabled={busy || clientMode === 'lookup' || clientMode === 'pick'}>
          {entry ? 'Save changes' : 'Add GIFT City entry'}
        </button>
        {entry && <CancelEntry url={`/api/gift/${entry.id}/cancel`} question="Why cancel this GIFT City entry?" onDone={onDone} />}
      </span>
    </form>
  );
}

