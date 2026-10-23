import { CancelEntry, ClientFieldset, MonthRmFieldset, useClientAndRm } from '../entry/ClientAndRm';
import { useEffect, useState, type FormEvent } from 'react';
import { useTableView } from '../../components/useTableView';
import { api } from '../../api/client';
import {
  LOAN_PAYMENT_LABEL,
  type LoanEntry,
  type LoanPaymentStatus,
  type LoanPayoutKind,
  type LoanPayoutOn,
  type LoanSuggestions,
  type TaxRate,
} from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { Amount } from '../../components/Amount';
import { GivenFiguresFields, givenBody, shownFigures, useGiven } from '../../components/GivenFigures';
import { ListFilters, useFilterQuery } from '../../components/ListFilters';
import { Modal } from '../../components/Modal';
import { useEntryPanel } from '../../components/useEntryPanel';
import { formatDate, formatMoney, todayIso, formatMonth, indianDigits } from '../../format';

const round2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100;

/** The payout as the server works it out (R-80), for the live preview while typing. */
export function loanRevenue(base: number, payoutPct: number, fixed: number, tdsApplied: boolean, tdsPct: number, gstPct: number) {
  const gross = round2(round2((base * payoutPct) / 100) + fixed);
  const tds = tdsApplied ? round2((gross * tdsPct) / 100) : 0;
  const gst = round2((gross * gstPct) / 100);
  return { gross, tds, gst, net: round2(gross - tds), invoice: round2(gross + gst) };
}

/** "2026-08" + 3 → "2026-11". */
export function monthPlus(ym: string, n: number): string {
  const [y, m] = ym.split('-').map(Number);
  const d = new Date(y, m - 1 + n, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

const thisMonth = () => todayIso().slice(0, 7);

/** The register's Payment Status column, as it reads. */
export function paymentText(e: LoanEntry): string {
  if (e.paymentNote) return e.paymentNote;
  if (e.loanClosed) return 'Loan Closed';
  return e.paymentStatus === 'RECEIVED' ? 'CREDITED' : 'Pending';
}

/** Loans: one row per payout (a disbursal payout, or an LAS trail payout each quarter), as in the LOANS register. */
export function LoansPage({ embedded = false }: { embedded?: boolean } = {}) {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const query = useFilterQuery();
  const list = useLoad<LoanEntry[]>(`/api/loans${query}`);
  const suggestions = useLoad<LoanSuggestions>('/api/loans/suggestions');
  const [panel, setPanel] = useEntryPanel();
  const [nextOf, setNextOf] = useState<LoanEntry | null>(null);
  const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
  // As in the register: open loans first, then a Closed section.
  const openLoans = (list.data ?? []).filter((e) => !e.loanClosed);
  const closedLoans = (list.data ?? []).filter((e) => e.loanClosed);
  const done = () => {
    setPanel(null);
    setNextOf(null);
    list.reload();
    suggestions.reload();
  };

  return (
    <section>
      <div className="page-head" style={embedded ? { justifyContent: 'flex-end', marginBottom: 12 } : undefined}>
        {!embedded && <h1>Loans</h1>}
        {canEdit && (
          <div className="actions">
            <a className="button-link secondary" href={`/api/loans/export.xlsx${query}`} download>
              Download Excel
            </a>
            <button type="button" onClick={() => setPanel('new')}>
              Add loan payout
            </button>
          </div>
        )}
      </div>
      <ListFilters
        searchHint="Client, PAN, code, lender or loan type"
        platformKey="lender"
        platformLabel="Lender"
        platforms={(suggestions.data?.lenders ?? []).map((p) => ({ value: p, label: p }))}
        payments={LOAN_PAYMENT_LABEL}
      />
      {list.error && <p className="error">{list.error}</p>}
      {list.data && list.data.length === 0 && <p className="muted">No loan payouts match.</p>}
      {list.data && list.data.length > 0 && (
        <>
          {openLoans.length > 0 ? <LoanTable rows={openLoans} onOpen={setPanel} /> : <p className="muted">No open loans match.</p>}
          {closedLoans.length > 0 && (
            <div className="loan-closed">
              <h2>
                Closed <span className="badge">{closedLoans.length}</span>
              </h2>
              <p className="muted" style={{ marginTop: 0 }}>
                Loans marked closed (tick &ldquo;Loan closed&rdquo; on the entry), as in the register&rsquo;s Closed section. They stay in their months&rsquo;
                figures.
              </p>
              <LoanTable rows={closedLoans} onOpen={setPanel} />
            </div>
          )}
        </>
      )}
      {(panel === 'new' || nextOf) && (
        <Modal
          title={nextOf ? `Next payout — ${nextOf.clientName}` : 'Add loan payout'}
          onClose={() => {
            setPanel(null);
            setNextOf(null);
          }}
        >
          <LoanForm month={thisMonth()} copyOf={nextOf ?? undefined} onDone={done} />
        </Modal>
      )}
      {editing && !nextOf && (
        <Modal title={`${editing.clientName} — ${editing.lender} ${editing.loanType}`} onClose={() => setPanel(null)}>
          {canEdit && editing.status === 'ACTIVE' ? (
            <>
              {editing.loanClosed && <p className="muted">This loan is closed: untick &ldquo;Loan closed&rdquo; below to move it back to the open loans.</p>}
              <p hidden={editing.loanClosed}>
                <button
                  type="button"
                  className="secondary"
                  onClick={() => {
                    setNextOf(editing);
                    setPanel(null);
                  }}
                >
                  Next payout for this loan…
                </button>{' '}
                <span className="muted">e.g. the next quarter&rsquo;s LAS trail: a new entry with the same client, lender and amounts.</span>
              </p>
              <LoanForm month={thisMonth()} entry={editing} onDone={done} />
            </>
          ) : (
            <LoanFacts entry={editing} />
          )}
        </Modal>
      )}
    </section>
  );
}

type Sum = 'sanctionedAmount' | 'gross' | 'net' | 'invoice';

/** One section of the Loans list (open or closed), numbered as the register does, with its totals. */
function LoanTable({ rows, onOpen }: { rows: LoanEntry[]; onOpen: (id: number) => void }) {
  const active = rows.filter((e) => e.status === 'ACTIVE');
  const total = (k: Sum) => active.reduce((s, e) => s + e[k], 0);
  const view = useTableView(rows, { month: (e) => e.month, region: (e) => e.region, client: (e) => e.clientName, rm: (e) => e.rmName, lender: (e) => e.lender, loan_type: (e) => e.loanType, invt_date: (e) => e.disbursalDate, loan_sanctioned: (e) => e.sanctionedAmount, payout: (e) => e.payoutPercent, without_gst: (e) => e.gross, net_revenue: (e) => e.net, invoice: (e) => e.invoice, payment_status: (e) => e.paymentStatus });
  return (
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
          {view.th('lender', 'Lender')}
          {view.th('loan_type', 'Loan type')}
          {view.th('invt_date', 'Invt date')}
          {view.th('loan_sanctioned', 'Loan / sanctioned', 'num')}
          {view.th('payout', 'Payout %', 'num')}
          {view.th('without_gst', 'Without GST', 'num')}
          {view.th('net_revenue', 'Net revenue', 'num')}
          {view.th('invoice', 'Invoice', 'num')}
          {view.th('payment_status', 'Payment status')}
        </tr>
      </thead>
      <tbody>
        {view.rows.map((e, i) => (
          <tr key={e.id} className={e.status === 'CANCELLED' ? 'muted' : undefined}>
            <td className="num">{i + 1}</td>
            <td>{formatMonth(e.month)}</td>
            <td>{e.region ?? <span className="muted">—</span>}</td>
            <td>
              <button type="button" className="cell-link" onClick={() => onOpen(e.id)}>
                {e.clientName}
              </button>
              {e.status === 'CANCELLED' && <span className="badge">cancelled</span>}
              {e.partnerName && <div className="muted">{e.partnerName}</div>}
            </td>
            <td>{e.rmName}</td>
            <td>{e.lender}</td>
            <td>
              {e.loanType}
              {e.payoutKind === 'TRAIL' && <span className="badge">trail</span>}
            </td>
            <td>{formatDate(e.disbursalDate)}</td>
            <td className="num">
              <Amount value={e.sanctionedAmount} />
            </td>
            <td className="num">{e.payoutPercent ?? ''}</td>
            <td className="num">
              <Amount value={e.gross} />
            </td>
            <td className="num">
              <Amount value={e.net} />
            </td>
            <td className="num">
              <Amount value={e.invoice} />
            </td>
            <td>
              <span className={e.paymentStatus === 'RECEIVED' ? 'badge ok' : e.loanClosed ? 'badge' : 'badge warn'}>{paymentText(e)}</span>
              {e.receivedOn && <div className="muted">on {formatDate(e.receivedOn)}</div>}
            </td>
          </tr>
        ))}
        <tr className="total">
          <td colSpan={8}>Total ({active.length} payouts)</td>
          <td className="num">
            <Amount value={total('sanctionedAmount')} />
          </td>
          <td />
          <td className="num">
            <Amount value={total('gross')} />
          </td>
          <td className="num">
            <Amount value={total('net')} />
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
  );
}

function LoanFacts({ entry: e }: { entry: LoanEntry }) {
  return (
    <dl className="facts">
      <dt>Client</dt>
      <dd>
        {e.clientName} <span className="mono">{e.pan}</span>
      </dd>
      <dt>Loan</dt>
      <dd>
        {e.lender} · {e.loanType} · {formatMoney(e.sanctionedAmount)}
      </dd>
      <dt>Without GST / Net / Invoice</dt>
      <dd>
        {formatMoney(e.gross)} / {formatMoney(e.net)} / {formatMoney(e.invoice)}
      </dd>
      <dt>Status</dt>
      <dd>{e.status === 'CANCELLED' ? 'Cancelled' : paymentText(e)}</dd>
    </dl>
  );
}

/**
 * Add or edit one loan payout, in the LOANS register's order. {@code copyOf}: a new payout for the same loan (the next
 * quarter's trail): same client, RM, partner, lender, type and amounts, three months on, as a trail on the utilised amount.
 */
function LoanForm({ month: listMonth, entry, copyOf, onDone }: { month: string; entry?: LoanEntry; copyOf?: LoanEntry; onDone: () => void }) {
  const src = entry ?? copyOf;
  const party = useClientAndRm(src, entry ? entry.month : copyOf ? monthPlus(copyOf.month, 3) : listMonth);
  const { month, monthDay, clientMode } = party;
  const [lender, setLender] = useState(src?.lender ?? '');
  const [loanType, setLoanType] = useState(src?.loanType ?? '');
  const [disbursalDate, setDisbursalDate] = useState(src?.disbursalDate ?? '');
  const [sanctioned, setSanctioned] = useState(src ? indianDigits(String(src.sanctionedAmount)) : '');
  const [utilised, setUtilised] = useState(src?.utilisedAmount != null ? indianDigits(String(src.utilisedAmount)) : '');
  const [rate, setRate] = useState(src?.ratePercent != null ? String(src.ratePercent) : '');
  const [kind, setKind] = useState<LoanPayoutKind>(entry ? entry.payoutKind : copyOf ? 'TRAIL' : 'DISBURSAL');
  const [on, setOn] = useState<LoanPayoutOn>(entry ? entry.payoutOn : copyOf ? 'UTILISED' : 'SANCTIONED');
  const [payoutPct, setPayoutPct] = useState(entry?.payoutPercent != null ? String(entry.payoutPercent) : copyOf?.payoutKind === 'TRAIL' && copyOf.payoutPercent != null ? String(copyOf.payoutPercent) : '');
  const [payoutTouched, setPayoutTouched] = useState(!!entry);
  const [fixed, setFixed] = useState(entry?.fixedAmount != null ? String(entry.fixedAmount) : '');
  const [tdsApplied, setTdsApplied] = useState(entry?.tdsApplied ?? true);
  const [tdsPct, setTdsPct] = useState(entry ? String(entry.tdsPercent) : '');
  const [invoiceRaised, setInvoiceRaised] = useState(entry?.invoiceRaised ?? false);
  const [payment, setPayment] = useState<LoanPaymentStatus>(entry?.paymentStatus ?? 'PENDING');
  const [paymentNote, setPaymentNote] = useState(entry?.paymentNote ?? '');
  const [receivedOn, setReceivedOn] = useState(entry?.receivedOn ?? '');
  const [loanClosed, setLoanClosed] = useState(entry?.loanClosed ?? false);
  const [remarks, setRemarks] = useState(entry?.remarks ?? '');
  const { busy, error, run } = useSubmit();

  const suggestions = useLoad<LoanSuggestions>('/api/loans/suggestions');
  const tax = useLoad<TaxRate>(`/api/product-taxes/LOAN/on?date=${monthDay}`);

  // A new payout starts with the month's Loans TDS % (5% until Sep 2024, 2% after); it can be changed for this entry.
  useEffect(() => {
    if (!entry && tax.data) {
      setTdsApplied(tax.data.tdsByDefault || tax.data.tdsPercent > 0);
      setTdsPct(String(tax.data.tdsPercent));
    }
  }, [entry, tax.data]);


  const num = (s: string) => Number(s.replace(/,/g, '')) || 0;
  const gstPct = entry && entry.month === month.slice(0, 7) ? entry.gstPercent : (tax.data?.gstPercent ?? 0);
  const [given, setGiven] = useGiven(entry);
  const base = on === 'UTILISED' ? num(utilised) : num(sanctioned);
  const worked = loanRevenue(base, num(payoutPct), num(fixed), tdsApplied, num(tdsPct), gstPct);
  const rev = shownFigures(worked, given);
  // Lender + type + kind known → the payout % last used for them, until it is typed by hand.
  const lastPayout = suggestions.data?.payouts.find(
    (p) => p.lender.toLowerCase() === lender.trim().toLowerCase() && p.loanType.toLowerCase() === loanType.trim().toLowerCase() && p.payoutKind === kind,
  );
  useEffect(() => {
    if (!payoutTouched && lastPayout) {
      setPayoutPct(String(lastPayout.payoutPercent));
      setOn(lastPayout.payoutOn);
    }
  }, [lastPayout, payoutTouched]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const body = {
    ...party.body(),
      lender,
      loanType,
      disbursalDate: disbursalDate || null,
      sanctionedAmount: sanctioned === '' ? null : num(sanctioned),
      utilisedAmount: utilised === '' ? null : num(utilised),
      ratePercent: rate === '' ? null : num(rate),
      payoutKind: kind,
      payoutOn: on,
      payoutPercent: payoutPct === '' ? null : num(payoutPct),
      fixedAmount: fixed === '' ? null : num(fixed),
      tdsApplied,
      tdsPercent: tdsApplied ? num(tdsPct) : 0,
      invoiceRaised,
      paymentStatus: payment,
      paymentNote,
      receivedOn: payment === 'RECEIVED' && receivedOn ? receivedOn : null,
      loanClosed,
      remarks,
      ...givenBody(given),
    };
    const ok = await run(() => (entry ? api(`/api/loans/${entry.id}`, { method: 'PUT', body }) : api('/api/loans', { method: 'POST', body })));
    if (ok) onDone();
  }

  return (
    <form onSubmit={submit} className="fd-form">
      {tax.error && <p className="error">{tax.error}</p>}
      <ClientFieldset party={party} radioName="loan-client" />

      <MonthRmFieldset party={party} partnerLabel="Referral partner / sub-broker" />

      <fieldset>
        <legend>Loan</legend>
        <label>
          Partner (lender)
          <input value={lender} onChange={(e) => setLender(e.target.value)} list="loan-lenders" placeholder="e.g. TATA" required />
        </label>
        <datalist id="loan-lenders">
          {(suggestions.data?.lenders ?? []).map((n) => (
            <option key={n} value={n} />
          ))}
        </datalist>
        <label>
          Loan type
          <input value={loanType} onChange={(e) => setLoanType(e.target.value)} list="loan-types" placeholder="Home Loan, LAS, Personal Loan…" required />
        </label>
        <datalist id="loan-types">
          {['Home Loan', 'LAS', 'Personal Loan', 'Business Loan', 'Loan Against Property', ...(suggestions.data?.loanTypes ?? [])]
            .filter((v, i, a) => a.findIndex((x) => x.toLowerCase() === v.toLowerCase()) === i)
            .map((n) => (
              <option key={n} value={n} />
            ))}
        </datalist>
        <label>
          Invt date (disbursal)
          <input type="date" value={disbursalDate} onChange={(e) => setDisbursalDate(e.target.value)} required />
        </label>
        <label>
          Loan amt / sanctioned amt (₹)
          <input value={sanctioned} onChange={(e) => setSanctioned(indianDigits(e.target.value))} inputMode="decimal" required />
        </label>
        <label>
          Utilised amt (₹)
          <input value={utilised} onChange={(e) => setUtilised(indianDigits(e.target.value))} inputMode="decimal" required={on === 'UTILISED'} />
        </label>
        <label>
          Rate of interest %
          <input type="number" min={0} max={100} step="0.01" value={rate} onChange={(e) => setRate(e.target.value)} />
        </label>
      </fieldset>

      <fieldset>
        <legend>Revenue</legend>
        <GivenFiguresFields value={given} onChange={setGiven} />
        <label>
          Payout
          <select value={kind} onChange={(e) => setKind(e.target.value as LoanPayoutKind)}>
            <option value="DISBURSAL">On disbursal (one-time)</option>
            <option value="TRAIL">Trail (e.g. LAS, each quarter)</option>
          </select>
        </label>
        <label>
          Revenue sharing % (payout %)
          <input
            type="number"
            min={0}
            max={100}
            step="0.001"
            value={payoutPct}
            onChange={(e) => {
              setPayoutTouched(true);
              setPayoutPct(e.target.value);
            }}
          />
        </label>
        <label>
          On
          <select value={on} onChange={(e) => setOn(e.target.value as LoanPayoutOn)}>
            <option value="SANCTIONED">Loan / sanctioned amount</option>
            <option value="UTILISED">Utilised amount</option>
          </select>
        </label>
        <label>
          Fixed amount (₹, optional)
          <input type="number" min={0} step="0.01" value={fixed} onChange={(e) => setFixed(e.target.value)} placeholder="added to the payout, or the whole payout when there is no %" />
        </label>
        {lastPayout && (
          <p className="muted">
            Last used for {lastPayout.lender} · {lastPayout.loanType}: {lastPayout.payoutPercent}% on the {lastPayout.payoutOn === 'UTILISED' ? 'utilised' : 'sanctioned'} amount
          </p>
        )}
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
          <dt>Without GST</dt>
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
        <legend>Payment</legend>
        <label className="check">
          <input type="checkbox" checked={invoiceRaised} onChange={(e) => setInvoiceRaised(e.target.checked)} />
          Invoice raised by accounts
        </label>
        <label>
          Payment status
          <select value={payment} onChange={(e) => setPayment(e.target.value as LoanPaymentStatus)}>
            {Object.entries(LOAN_PAYMENT_LABEL).map(([k, v]) => (
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
          Payment note (as in the register)
          <input value={paymentNote} onChange={(e) => setPaymentNote(e.target.value)} placeholder="e.g. Invoice will be raised in Oct" />
        </label>
        <label className="check">
          <input type="checkbox" checked={loanClosed} onChange={(e) => setLoanClosed(e.target.checked)} />
          Loan closed
        </label>
        <label>
          Remarks
          <textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} rows={2} placeholder="e.g. Quarterly 0.25% on utilised amt on trail basis" />
        </label>
      </fieldset>

      {error && <p className="error">{error}</p>}
      <span className="inline-form">
        <button type="submit" disabled={busy || clientMode === 'lookup' || clientMode === 'pick'}>
          {entry ? 'Save changes' : 'Add loan payout'}
        </button>
        {entry && <CancelEntry url={`/api/loans/${entry.id}/cancel`} question="Why cancel this loan entry?" onDone={onDone} />}
      </span>
    </form>
  );
}

