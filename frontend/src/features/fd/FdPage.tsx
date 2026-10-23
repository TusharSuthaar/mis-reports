import { CancelEntry, ClientFieldset, MonthRmFieldset, useClientAndRm } from '../entry/ClientAndRm';
import { useEffect, useState, type FormEvent } from 'react';
import { useTableView } from '../../components/useTableView';
import { api } from '../../api/client';
import {
  CERTIFICATE_LABEL,
  COMPOUNDING_LABEL,
  INTEREST_FREQUENCY_LABEL,
  PAYMENT_LABEL,
  type FdCertificate,
  type FdCompounding,
  type FdEntry,
  type FdSuggestions,
  type FdPaymentStatus,
  type InterestFrequency,
  type TaxRate,
} from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { ListFilters, useFilterQuery } from '../../components/ListFilters';
import { GivenFiguresFields, givenBody, shownFigures, useGiven } from '../../components/GivenFigures';
import { Modal } from '../../components/Modal';
import { useEntryPanel } from '../../components/useEntryPanel';
import { formatDate, formatMoney, todayIso, formatMonth, indianDigits } from '../../format';
import { Amount } from '../../components/Amount';

const round2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100;

/** The commission as the server works it out (R-70), for the live preview while typing. */
export function fdRevenue(amount: number, margin: number, tdsApplied: boolean, tdsPct: number, gstPct: number) {
  const gross = round2((amount * margin) / 100);
  const tds = tdsApplied ? round2((gross * tdsPct) / 100) : 0;
  const gst = round2((gross * gstPct) / 100);
  return { gross, tds, gst, net: round2(gross - tds), invoice: round2(gross + gst) };
}

const COMPOUNDS_PER_YEAR: Record<FdCompounding, number> = { QUARTERLY: 4, HALF_YEARLY: 2, YEARLY: 1 };

/**
 * Maturity value as the server works it out (R-72), from the period in months. Cumulative: compounded n times a year for
 * each full period, simple interest on the months left; interest paid out: P + P × r × months/12.
 */
export function maturityValue(amount: number, ratePct: number, months: number, frequency: InterestFrequency | '', compounding: FdCompounding): number | null {
  if (!amount || !months || months < 1 || !frequency) return null;
  const r = ratePct / 100;
  if (frequency !== 'CUMULATIVE') return round2(amount + (amount * r * months) / 12);
  const n = COMPOUNDS_PER_YEAR[compounding];
  const full = Math.floor(months / (12 / n));
  const left = months - full * (12 / n);
  return round2(amount * Math.pow(1 + r / n, full) * (1 + (r * left) / 12));
}

/** Deposit date + months → maturity date (same day of the month; the last day when the month is shorter). */
export function addMonths(iso: string, months: number): string {
  if (!iso || !months) return '';
  const [y, m, d] = iso.split('-').map(Number);
  const target = new Date(Date.UTC(y, m - 1 + months, 1));
  const last = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  target.setUTCDate(Math.min(d, last));
  return target.toISOString().slice(0, 10);
}

/** Whole months from deposit to maturity (days left over are dropped, as the register does). */
export function monthsBetween(from: string, to: string): number {
  if (!from || !to || to <= from) return 0;
  const [y1, m1, d1] = from.split('-').map(Number);
  const [y2, m2, d2] = to.split('-').map(Number);
  return (y2 - y1) * 12 + (m2 - m1) - (d2 < d1 ? 1 : 0);
}


const thisMonth = () => todayIso().slice(0, 7);




/** FD: one row per FD booked, entered by hand (Phase 2, entry only). */
export function FdPage({ embedded = false }: { embedded?: boolean } = {}) {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const month = thisMonth();   // the month a new FD starts in
  const query = useFilterQuery();
  const list = useLoad<FdEntry[]>(`/api/fd${query}`);
  const suggestions = useLoad<FdSuggestions>('/api/fd/suggestions');
  const [panel, setPanel] = useEntryPanel();
  const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
  const active = (list.data ?? []).filter((e) => e.status === 'ACTIVE');
  const total = (k: 'amount' | 'gross' | 'net' | 'invoice') => active.reduce((s, e) => s + e[k], 0);
  const done = () => {
    setPanel(null);
    list.reload();
  };

  const view = useTableView(list.data, { month: (e) => e.month, deposit: (e) => e.depositDate, client: (e) => e.clientName, fd: (e) => e.fdName, rm: (e) => e.rmName, amount: (e) => e.amount, gross: (e) => e.gross, net: (e) => e.net, invoice: (e) => e.invoice, certificate: (e) => e.certificate, payment: (e) => e.paymentStatus });
  return (
    <section>
      <div className="page-head" style={embedded ? { justifyContent: 'flex-end', marginBottom: 12 } : undefined}>
        {!embedded && <h1>FD</h1>}
        {canEdit && (
          <div className="actions">
            <a className="button-link secondary" href={`/api/fd/export.xlsx${query}`} download>
              Download Excel
            </a>
            <button type="button" onClick={() => setPanel('new')}>
              Add FD
            </button>
          </div>
        )}
      </div>
      <ListFilters
        searchHint="Client, PAN, code, FD company or FD no."
        platformKey="platform"
        platforms={(suggestions.data?.platforms ?? []).map((p) => ({ value: p, label: p }))}
      />
      {list.error && <p className="error">{list.error}</p>}
      {list.data && list.data.length === 0 && <p className="muted">No FDs match.</p>}
      {list.data && list.data.length > 0 && (
        <>
<div className="table-scroll">
<table>
          <thead>
            <tr>
              <th className="num">SR.NO</th>
              {view.th('month', 'Month')}
              {view.th('deposit', 'Deposit')}
              {view.th('client', 'Client')}
              {view.th('fd', 'FD')}
              {view.th('rm', 'RM')}
              {view.th('amount', 'Amount', 'num')}
              {view.th('gross', 'Gross', 'num')}
              {view.th('net', 'Net', 'num')}
              {view.th('invoice', 'Invoice', 'num')}
              {view.th('certificate', 'Certificate')}
              {view.th('payment', 'Payment')}
            </tr>
          </thead>
          <tbody>
            {view.rows.map((e, i) => (
              <tr key={e.id} className={e.status === 'CANCELLED' ? 'muted' : undefined}>
                <td className="num">{i + 1}</td>
                <td>{formatMonth(e.month)}</td>
                <td>{formatDate(e.depositDate)}</td>
                <td>
                  <button type="button" className="cell-link" onClick={() => setPanel(e.id)}>
                    {e.clientName}
                  </button>
                  {e.status === 'CANCELLED' && <span className="badge">cancelled</span>}
                </td>
                <td>
                  {e.fdName}
                  {e.fdNo ? <span className="muted"> #{e.fdNo}</span> : null}
                </td>
                <td>{e.rmName}</td>
                <td className="num"><Amount value={e.amount} /></td>
                <td className="num"><Amount value={e.gross} /></td>
                <td className="num"><Amount value={e.net} /></td>
                <td className="num"><Amount value={e.invoice} /></td>
                <td>{CERTIFICATE_LABEL[e.certificate]}</td>
                <td>{e.paymentStatus === 'RECEIVED' ? PAYMENT_LABEL[e.paymentStatus] : <span className="error">{PAYMENT_LABEL[e.paymentStatus]}</span>}</td>
              </tr>
            ))}
            <tr className="total">
              <td colSpan={6}>Total ({active.length} FDs)</td>
              <td className="num"><Amount value={total('amount')} /></td>
              <td className="num"><Amount value={total('gross')} /></td>
              <td className="num"><Amount value={total('net')} /></td>
              <td className="num"><Amount value={total('invoice')} /></td>
              <td colSpan={2} />
            </tr>
          </tbody>
        </table>
</div>
{view.more}
</>
      )}
      {panel === 'new' && (
        <Modal title="Add FD" onClose={() => setPanel(null)}>
          <FdForm month={month} onDone={done} />
        </Modal>
      )}
      {editing && (
        <Modal title={`${editing.clientName} — ${editing.fdName}`} onClose={() => setPanel(null)}>
          {canEdit && editing.status === 'ACTIVE' ? <FdForm month={month} entry={editing} onDone={done} /> : <FdFacts entry={editing} />}
        </Modal>
      )}
    </section>
  );
}

function FdFacts({ entry: e }: { entry: FdEntry }) {
  return (
    <dl className="facts">
      <dt>Client</dt>
      <dd>
        {e.clientName} <span className="mono">{e.pan}</span>
      </dd>
      <dt>FD</dt>
      <dd>
        {e.fdName} {e.fdNo}
      </dd>
      <dt>Amount</dt>
      <dd>{formatMoney(e.amount)}</dd>
      <dt>Gross / Net / Invoice</dt>
      <dd>
        {formatMoney(e.gross)} / {formatMoney(e.net)} / {formatMoney(e.invoice)}
      </dd>
      <dt>Status</dt>
      <dd>{e.status === 'CANCELLED' ? 'Cancelled' : PAYMENT_LABEL[e.paymentStatus]}</dd>
    </dl>
  );
}

/** Add or edit one FD, laid out as in the FD mapping sheet. */
function FdForm({ month: listMonth, entry, onDone }: { month: string; entry?: FdEntry; onDone: () => void }) {
  const party = useClientAndRm(entry, entry ? entry.month : listMonth);
  const { month, monthDay, clientMode } = party;
  const [fdName, setFdName] = useState(entry?.fdName ?? '');
  const [fdNo, setFdNo] = useState(entry?.fdNo ?? '');
  const [frequency, setFrequency] = useState<InterestFrequency | ''>(entry?.interestFrequency ?? '');
  const [platform, setPlatform] = useState(entry?.platform ?? '');
  const [depositDate, setDepositDate] = useState(entry?.depositDate ?? '');
  const [maturityDate, setMaturityDate] = useState(entry?.maturityDate ?? '');
  const [rate, setRate] = useState(entry ? String(entry.ratePercent) : '');
  const [amount, setAmount] = useState(entry ? indianDigits(String(entry.amount)) : '');
  const [compounding, setCompounding] = useState<FdCompounding>(entry?.compounding ?? 'YEARLY');
  const [months, setMonths] = useState(entry ? String(entry.periodMonths) : '');
  const [marginTouched, setMarginTouched] = useState(!!entry);
  const [certificate, setCertificate] = useState<FdCertificate>(entry?.certificate ?? 'PENDING');
  const [margin, setMargin] = useState(entry ? String(entry.marginPercent) : '');
  const [tdsApplied, setTdsApplied] = useState(entry?.tdsApplied ?? false);
  const [tdsPct, setTdsPct] = useState(entry ? String(entry.tdsPercent) : '');
  const [invoiceRaised, setInvoiceRaised] = useState(entry?.invoiceRaised ?? false);
  const [onPortal, setOnPortal] = useState(entry?.onPortal ?? false);
  const [payment, setPayment] = useState<FdPaymentStatus>(entry?.paymentStatus ?? 'PENDING');
  const [remarks, setRemarks] = useState(entry?.remarks ?? '');
  const { busy, error, run } = useSubmit();

  const suggestions = useLoad<FdSuggestions>('/api/fd/suggestions');
  const tax = useLoad<TaxRate>(`/api/product-taxes/FD/on?date=${monthDay}`);

  // A new entry starts with the product's TDS default; a client's own partner and RM are suggested.
  useEffect(() => {
    if (!entry && tax.data) {
      setTdsApplied(tax.data.tdsByDefault);
      setTdsPct(String(tax.data.tdsPercent));
    }
  }, [entry, tax.data]);
  // One match: that is the client. Its RM comes from the Clients screen; its own partner is suggested.


  const num = (s: string) => Number(s.replace(/,/g, '')) || 0;
  const gstPct = entry && entry.month === month.slice(0, 7) ? entry.gstPercent : (tax.data?.gstPercent ?? 0);
  const [given, setGiven] = useGiven(entry);
  const worked = fdRevenue(num(amount), num(margin), tdsApplied, num(tdsPct), gstPct);
  const rev = shownFigures(worked, given);
  const maturity = maturityValue(num(amount), num(rate), num(months), frequency, compounding);
  // Company + platform + period known → the margin last used for them, until the margin is typed by hand.
  const lastMargin = suggestions.data?.margins.find(
    (m) => m.fdName.toLowerCase() === fdName.trim().toLowerCase() && (m.platform ?? '').toLowerCase() === platform.trim().toLowerCase() && m.periodMonths === num(months),
  );
  useEffect(() => {
    if (!marginTouched && lastMargin) setMargin(String(lastMargin.marginPercent));
  }, [lastMargin, marginTouched]);
  const changeDeposit = (d: string) => {
    setDepositDate(d);
    if (num(months) > 0) setMaturityDate(addMonths(d, num(months)));
  };
  const changeMonths = (m: string) => {
    setMonths(m);
    if (depositDate && Number(m) > 0) setMaturityDate(addMonths(depositDate, Number(m)));
  };
  const changeMaturity = (d: string) => {
    setMaturityDate(d);
    const m = monthsBetween(depositDate, d);
    if (m > 0) setMonths(String(m));
  };

  async function submit(e: FormEvent) {
    e.preventDefault();
    const body = {
    ...party.body(),
      fdName,
      fdNo,
      interestFrequency: frequency || null,
      platform,
      depositDate: depositDate || null,
      periodMonths: months === '' ? null : num(months),
      maturityDate: maturityDate || null,
      ratePercent: rate === '' ? null : num(rate),
      amount: amount === '' ? null : num(amount),
      compounding: frequency === 'CUMULATIVE' ? compounding : null,
      certificate,
      marginPercent: margin === '' ? null : num(margin),
      tdsApplied,
      tdsPercent: tdsApplied ? num(tdsPct) : 0,
      invoiceRaised,
      onPortal,
      paymentStatus: payment,
      remarks,
      ...givenBody(given),
    };
    const ok = await run(() => (entry ? api(`/api/fd/${entry.id}`, { method: 'PUT', body }) : api('/api/fd', { method: 'POST', body })));
    if (ok) onDone();
  }

  return (
    <form onSubmit={submit} className="fd-form">
      {tax.error && <p className="error">{tax.error}</p>}
      <ClientFieldset party={party} radioName="fd-client" mobileLabel="Mobile" emailLabel="Email" />

      <MonthRmFieldset party={party} partnerLabel="Referral / sub-broker" />

      <fieldset>
        <legend>FD</legend>
        <label>
          FD company (FD name)
          <input value={fdName} onChange={(e) => setFdName(e.target.value)} list="fd-companies" placeholder="e.g. BAJAJ FINANCE LIMITED" required />
        </label>
        <datalist id="fd-companies">
          {(suggestions.data?.fdNames ?? []).map((n) => (
            <option key={n} value={n} />
          ))}
        </datalist>
        <label>
          FD no.
          <input value={fdNo} onChange={(e) => setFdNo(e.target.value)} />
        </label>
        <label>
          Partner (platform)
          <input value={platform} onChange={(e) => setPlatform(e.target.value)} list="fd-platforms" placeholder="e.g. J M Finance" />
        </label>
        <datalist id="fd-platforms">
          {(suggestions.data?.platforms ?? []).map((n) => (
            <option key={n} value={n} />
          ))}
        </datalist>
        <label>
          Interest frequency
          <select value={frequency} onChange={(e) => setFrequency(e.target.value as InterestFrequency)} required>
            <option value="">Choose…</option>
            {Object.entries(INTEREST_FREQUENCY_LABEL).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <label>
          Deposit date
          <input type="date" value={depositDate} onChange={(e) => changeDeposit(e.target.value)} required />
        </label>
        <label>
          Deposit period (months)
          <input type="number" min={1} max={600} step={1} value={months} onChange={(e) => changeMonths(e.target.value)} required />
        </label>
        <label>
          Maturity date
          <input type="date" value={maturityDate} onChange={(e) => changeMaturity(e.target.value)} required />
        </label>
        <label>
          Interest rate %
          <input type="number" min={0} max={100} step="0.01" value={rate} onChange={(e) => setRate(e.target.value)} required />
        </label>
        <label>
          Investment amount (₹)
          <input value={amount} onChange={(e) => setAmount(indianDigits(e.target.value))} inputMode="decimal" required />
        </label>
        {frequency === 'CUMULATIVE' && (
          <label>
            Compounding
            <select value={compounding} onChange={(e) => setCompounding(e.target.value as FdCompounding)}>
              {Object.entries(COMPOUNDING_LABEL).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
        )}
        <label>
          Maturity value (₹)
          <input value={maturity == null ? '' : formatMoney(maturity)} readOnly placeholder="worked out from amount, rate and dates" />
        </label>
        <label>
          Certificate
          <select value={certificate} onChange={(e) => setCertificate(e.target.value as FdCertificate)}>
            {Object.entries(CERTIFICATE_LABEL).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
      </fieldset>

      <fieldset>
        <legend>Commission</legend>
        <GivenFiguresFields value={given} onChange={setGiven} />
        <label>
          Margin %
          <input
            type="number"
            min={0}
            max={100}
            step="0.001"
            value={margin}
            onChange={(e) => {
              setMarginTouched(true);
              setMargin(e.target.value);
            }}
            required
          />
        </label>
        {lastMargin && <p className="muted">Last used for {lastMargin.fdName} · {lastMargin.platform ?? 'no platform'} · {lastMargin.periodMonths} months: {lastMargin.marginPercent}%</p>}
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
          <dt>Gross revenue</dt>
          <dd>{formatMoney(rev.gross)}</dd>
          <dt>TDS</dt>
          <dd>{formatMoney(rev.tds)}</dd>
          <dt>GST ({gstPct}%)</dt>
          <dd>{formatMoney(rev.gst)}</dd>
          <dt>Net revenue</dt>
          <dd>
            <strong>{formatMoney(rev.net)}</strong>
          </dd>
          <dt>Invoice amount</dt>
          <dd>{formatMoney(rev.invoice)}</dd>
        </dl>
      </fieldset>

      <fieldset>
        <legend>Follow-up</legend>
        <label className="check">
          <input type="checkbox" checked={invoiceRaised} onChange={(e) => setInvoiceRaised(e.target.checked)} />
          Invoice raised by accounts
        </label>
        <label className="check">
          <input type="checkbox" checked={onPortal} onChange={(e) => setOnPortal(e.target.checked)} />
          Updated on Epsilon portal
        </label>
        <label>
          Payment status
          <select value={payment} onChange={(e) => setPayment(e.target.value as FdPaymentStatus)}>
            {Object.entries(PAYMENT_LABEL).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <label>
          Remarks
          <textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} rows={2} />
        </label>
      </fieldset>

      {error && <p className="error">{error}</p>}
      <span className="inline-form">
        <button type="submit" disabled={busy || clientMode === 'lookup' || clientMode === 'pick'}>
          {entry ? 'Save changes' : 'Add FD'}
        </button>
        {entry && <CancelEntry url={`/api/fd/${entry.id}/cancel`} question="Why cancel this FD entry?" label="Cancel this FD" onDone={onDone} />}
      </span>
    </form>
  );
}

