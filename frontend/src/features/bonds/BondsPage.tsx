import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import {
  BOND_PAYOUT_LABEL,
  PAYMENT_LABEL,
  type BondEntry,
  type BondPayoutFrequency,
  type BondSuggestions,
  type ClientRow,
  type Employee,
  type FdPaymentStatus,
  type OrgChart,
  type Page,
  type Partner,
  type Platform,
  type PlatformTerms,
  type Share,
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
import { formatDate, formatMoney, todayIso } from '../../format';
import { Amount } from '../../components/Amount';
import { formatMonth, indianDigits, matchClients } from '../fd/FdPage';

const round2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100;
const PAN_FORMAT = /^[A-Z]{5}[0-9]{4}[A-Z]$/;

/** The brokerage as the server works it out (R-76), for the live preview while typing. */
export function bondRevenue(amount: number, markup: number, share: number, gstInclusive: boolean, gstPct: number, tdsApplied: boolean, tdsPct: number) {
  const brokerage = round2((amount * markup * share) / 10000);
  const gross = gstInclusive ? round2((brokerage * 100) / (100 + gstPct)) : brokerage;
  const gst = round2((gross * gstPct) / 100);
  const tds = tdsApplied ? round2((gross * tdsPct) / 100) : 0;
  return { brokerage, gross, gst, tds, net: round2(gross - tds), invoice: round2(gross + gst) };
}

const thisMonth = () => todayIso().slice(0, 7);

/** Bonds: one row per bond deal, entered by hand; brokerage from the mark-up and the platform's terms. */
export function BondsPage({ embedded = false }: { embedded?: boolean } = {}) {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const month = thisMonth();   // the month a new deal starts in
  const query = useFilterQuery();
  const list = useLoad<BondEntry[]>(`/api/bonds${query}`);
  const platforms = useLoad<Platform[]>('/api/platforms');
  const [panel, setPanel] = useEntryPanel();
  const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
  const active = (list.data ?? []).filter((e) => e.status === 'ACTIVE');
  const total = (k: 'consideration' | 'gross' | 'net' | 'invoice') => active.reduce((s, e) => s + e[k], 0);
  const done = () => {
    setPanel(null);
    list.reload();
  };

  return (
    <section>
      <div className="page-head" style={embedded ? { justifyContent: 'flex-end', marginBottom: 12 } : undefined}>
        {!embedded && <h1>Bonds</h1>}
        {canEdit && (
          <div className="actions">
            <a className="button-link secondary" href={`/api/bonds/export.xlsx${query}`} download>
              Download Excel
            </a>
            <button type="button" onClick={() => setPanel('new')}>
              Add bond
            </button>
          </div>
        )}
      </div>
      <ListFilters
        searchHint="Client, PAN, code, bond name or ISIN"
        platformKey="platformId"
        platforms={(platforms.data ?? []).map((p) => ({ value: String(p.id), label: p.name }))}
      />
      {list.error && <p className="error">{list.error}</p>}
      {list.data && list.data.length === 0 && <p className="muted">No bond deals match.</p>}
      {list.data && list.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th className="num">SR.NO</th>
              <th>Month</th>
              <th>Inv date</th>
              <th>Client</th>
              <th>Bond</th>
              <th>Platform</th>
              <th>RM</th>
              <th className="num">Consideration</th>
              <th className="num">Gross</th>
              <th className="num">Net</th>
              <th className="num">Invoice</th>
              <th>Payment</th>
            </tr>
          </thead>
          <tbody>
            {list.data.map((e, i) => (
              <tr key={e.id} className={e.status === 'CANCELLED' ? 'muted' : undefined}>
                <td className="num">{i + 1}</td>
                <td>{formatMonth(e.month)}</td>
                <td>{formatDate(e.investmentDate)}</td>
                <td>
                  <button type="button" className="cell-link" onClick={() => setPanel(e.id)}>
                    {e.clientName}
                  </button>
                  {e.status === 'CANCELLED' && <span className="badge">cancelled</span>}
                </td>
                <td>
                  {e.bondName}
                  {e.isin ? <span className="muted mono"> {e.isin}</span> : null}
                </td>
                <td>{e.platformName}</td>
                <td>{e.rmName}</td>
                <td className="num"><Amount value={e.consideration} /></td>
                <td className="num"><Amount value={e.gross} /></td>
                <td className="num"><Amount value={e.net} /></td>
                <td className="num"><Amount value={e.invoice} /></td>
                <td>{e.paymentStatus === 'RECEIVED' ? PAYMENT_LABEL[e.paymentStatus] : <span className="error">{PAYMENT_LABEL[e.paymentStatus]}</span>}</td>
              </tr>
            ))}
            <tr className="total">
              <td colSpan={7}>Total ({active.length} deals)</td>
              <td className="num"><Amount value={total('consideration')} /></td>
              <td className="num"><Amount value={total('gross')} /></td>
              <td className="num"><Amount value={total('net')} /></td>
              <td className="num"><Amount value={total('invoice')} /></td>
              <td />
            </tr>
          </tbody>
        </table>
      )}
      {panel === 'new' && (
        <Modal title="Add bond" onClose={() => setPanel(null)}>
          <BondForm month={month} onDone={done} />
        </Modal>
      )}
      {editing && (
        <Modal title={`${editing.clientName} — ${editing.bondName}`} onClose={() => setPanel(null)}>
          {canEdit && editing.status === 'ACTIVE' ? (
            <BondForm month={month} entry={editing} onDone={done} />
          ) : (
            <p>
              {editing.bondName} · {formatMoney(editing.consideration)} · {editing.status === 'CANCELLED' ? 'Cancelled' : PAYMENT_LABEL[editing.paymentStatus]}
            </p>
          )}
        </Modal>
      )}
    </section>
  );
}

/** Add or edit one bond deal, laid out as in the bond register. */
function BondForm({ month: listMonth, entry, onDone }: { month: string; entry?: BondEntry; onDone: () => void }) {
  const num = (s: string) => Number(s.replace(/,/g, '')) || 0;   // keeps a leading minus (accrued interest can be negative)
  const str = (n: number | null | undefined) => (n == null ? '' : String(n));
  const [month, setMonth] = useState(entry ? entry.month : listMonth);
  const [rmId, setRmId] = useState(entry ? String(entry.rmId) : '');
  const [lookup, setLookup] = useState('');
  const [looked, setLooked] = useState('');
  const [client, setClient] = useState<ClientRow | null>(null);
  const [newName, setNewName] = useState('');
  const [newPan, setNewPan] = useState('');
  const [mobile, setMobile] = useState(entry?.mobile ?? '');
  const [email, setEmail] = useState(entry?.email ?? '');
  const [address, setAddress] = useState(entry?.address ?? '');
  const [partnerId, setPartnerId] = useState(entry?.partnerId ? String(entry.partnerId) : '');
  const [platformId, setPlatformId] = useState(entry ? String(entry.platformId) : '');
  const [bondName, setBondName] = useState(entry?.bondName ?? '');
  const [isin, setIsin] = useState(entry?.isin ?? '');
  const [coupon, setCoupon] = useState(str(entry?.couponPercent));
  const [ytm, setYtm] = useState(str(entry?.ytmPercent));
  const [payout, setPayout] = useState<BondPayoutFrequency | ''>(entry?.payoutFrequency ?? '');
  const [schedule, setSchedule] = useState(entry?.paymentSchedule ?? '');
  const [invDate, setInvDate] = useState(entry?.investmentDate ?? '');
  const [allotDate, setAllotDate] = useState(entry?.allotmentDate ?? '');
  const [maturityDate, setMaturityDate] = useState(entry?.maturityDate ?? '');
  const [qty, setQty] = useState(str(entry?.quantity));
  const [rate, setRate] = useState(entry?.ratePerUnit != null ? indianDigits(String(entry.ratePerUnit)) : '');
  const [faceValue, setFaceValue] = useState(entry?.faceValue != null ? indianDigits(String(entry.faceValue)) : '');
  const [amount, setAmount] = useState(entry ? indianDigits(String(entry.investmentAmount)) : '');
  const [amountTouched, setAmountTouched] = useState(!!entry);
  const [accrued, setAccrued] = useState(entry ? indianDigits(String(entry.accruedInterest)) : '');
  const [markup, setMarkup] = useState(str(entry?.markupPercent));
  const [markupTouched, setMarkupTouched] = useState(!!entry);
  const [tdsApplied, setTdsApplied] = useState(entry?.tdsApplied ?? true);
  const [tdsPct, setTdsPct] = useState(str(entry?.tdsPercent));
  const [invoiceRaised, setInvoiceRaised] = useState(entry?.invoiceRaised ?? false);
  const [invoiceDate, setInvoiceDate] = useState(entry?.invoiceDate ?? '');
  const [receivedOn, setReceivedOn] = useState(entry?.paymentReceivedOn ?? '');
  const [payment, setPayment] = useState<FdPaymentStatus>(entry?.paymentStatus ?? 'PENDING');
  const [onPortal, setOnPortal] = useState(entry?.onPortal ?? false);
  const [remarks, setRemarks] = useState(entry?.remarks ?? '');
  const { busy, error, run } = useSubmit();

  const monthDay = `${month.slice(0, 7)}-01`;
  const employees = useLoad<Employee[]>('/api/employees');
  const partners = useLoad<Partner[]>('/api/partners');
  const platforms = useLoad<Platform[]>('/api/platforms');
  const suggestions = useLoad<BondSuggestions>('/api/bonds/suggestions');
  const chart = useLoad<OrgChart>(`/api/org?date=${monthDay}`);
  const tax = useLoad<TaxRate>(`/api/product-taxes/BOND/on?date=${monthDay}`);
  const terms = useLoad<PlatformTerms>(platformId ? `/api/platforms/${platformId}/terms/on?date=${monthDay}` : null);
  const found = useLoad<Page<ClientRow>>(!entry && looked ? `/api/clients?q=${encodeURIComponent(looked)}&size=20` : null);
  const matches = useMemo(() => (looked && found.data ? matchClients(found.data.items, looked) : null), [found.data, looked]);
  const clientMode: 'lookup' | 'found' | 'pick' | 'new' = entry ? 'found' : !matches ? 'lookup' : matches.length === 0 ? 'new' : client ? 'found' : 'pick';
  const rmLocked = !entry && !!client?.rmId;
  const shares = useLoad<Record<string, Share[]>>(client ? `/api/shares?clientIds=${client.id}` : null);

  useEffect(() => {
    if (matches && matches.length === 1) setClient(matches[0]);
    if (matches && matches.length === 0 && PAN_FORMAT.test(looked)) setNewPan(looked);
  }, [matches, looked]);
  useEffect(() => {
    if (!client) return;
    if (client.rmId) setRmId(String(client.rmId));
    const own = shares.data?.[client.id]?.find((s) => !s.branch);
    if (own && !partnerId) setPartnerId(String(own.partnerId));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client, shares.data]);
  // A new deal takes the platform's TDS %.
  useEffect(() => {
    if (!entry && terms.data) {
      setTdsPct(String(terms.data.tdsPercent));
      setTdsApplied(terms.data.tdsPercent > 0);
    }
  }, [entry, terms.data]);
  // Qty × rate → investment amount, until the amount is typed by hand.
  useEffect(() => {
    if (!amountTouched && num(qty) > 0 && num(rate) > 0) setAmount(indianDigits(String(round2(num(qty) * num(rate)))));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [qty, rate, amountTouched]);
  // Bond name known → its ISIN; platform + ISIN (or name) known → the mark-up last used.
  useEffect(() => {
    if (!isin && bondName) {
      const hit = suggestions.data?.isins.find((x) => x.bondName.toLowerCase() === bondName.trim().toLowerCase());
      if (hit) setIsin(hit.isin);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bondName, suggestions.data]);
  const lastMarkup = suggestions.data?.markups.find(
    (m) =>
      String(m.platformId) === platformId &&
      ((isin && m.isin?.toUpperCase() === isin.trim().toUpperCase()) || m.bondName.toLowerCase() === bondName.trim().toLowerCase()),
  );
  useEffect(() => {
    if (!markupTouched && lastMarkup) setMarkup(String(lastMarkup.markupPercent));
  }, [lastMarkup, markupTouched]);
  const findClient = () => {
    setClient(null);
    setLooked(lookup.trim().toUpperCase());
  };

  const place = useMemo(() => {
    for (const r of chart.data?.regions ?? []) {
      for (const b of r.branches) if (b.people.some((p) => String(p.id) === rmId)) return { region: r.name, rd: r.rd?.name ?? '—', branch: b.name };
    }
    return null;
  }, [chart.data, rmId]);

  const share = entry && entry.month === month.slice(0, 7) ? entry.ourShare : (terms.data?.ourShare ?? 100);
  const inclusive = entry && entry.month === month.slice(0, 7) ? entry.gstInclusive : (terms.data?.gstInclusive ?? false);
  const gstPct = entry && entry.month === month.slice(0, 7) ? entry.gstPercent : (tax.data?.gstPercent ?? 0);
  const [given, setGiven] = useGiven(entry);
  const worked = bondRevenue(num(amount), num(markup), share, inclusive, gstPct, tdsApplied, num(tdsPct));
  const rev = shownFigures(worked, given);
  const consideration = round2(num(amount) + num(accrued));
  const people = (employees.data ?? []).filter((e) => e.status === 'ACTIVE');
  const sharePartners = (partners.data ?? []).filter((p) => p.type === 'SUB_BROKER' || p.type === 'REFERRAL');

  async function submit(e: FormEvent) {
    e.preventDefault();
    const n = (s: string) => (s === '' ? null : num(s));
    const body = {
      clientId: entry ? entry.clientId : clientMode === 'found' ? client?.id : null,
      newClientName: clientMode === 'new' ? newName : null,
      newClientPan: clientMode === 'new' && newPan ? newPan : null,
      month: monthDay,
      rmId: Number(rmId),
      partnerId: partnerId ? Number(partnerId) : null,
      mobile,
      email,
      address,
      platformId: platformId ? Number(platformId) : null,
      bondName,
      isin,
      couponPercent: n(coupon),
      ytmPercent: n(ytm),
      payoutFrequency: payout || null,
      paymentSchedule: schedule,
      investmentDate: invDate || null,
      allotmentDate: allotDate || null,
      maturityDate: maturityDate || null,
      quantity: n(qty),
      ratePerUnit: n(rate),
      faceValue: n(faceValue),
      investmentAmount: n(amount),
      accruedInterest: n(accrued) ?? 0,
      markupPercent: n(markup),
      tdsApplied,
      tdsPercent: tdsApplied ? num(tdsPct) : 0,
      invoiceRaised,
      invoiceDate: invoiceDate || null,
      paymentReceivedOn: receivedOn || null,
      paymentStatus: payment,
      onPortal,
      remarks,
      ...givenBody(given),
    };
    const ok = await run(() => (entry ? api(`/api/bonds/${entry.id}`, { method: 'PUT', body }) : api('/api/bonds', { method: 'POST', body })));
    if (ok) onDone();
  }

  return (
    <form onSubmit={submit} className="fd-form">
      {tax.error && <p className="error">{tax.error}</p>}
      {terms.error && <p className="error">{terms.error}</p>}
      <fieldset>
        <legend>Client</legend>
        {entry ? (
          <p>
            {entry.clientName} <span className="mono">{entry.pan}</span> · Client type: {entry.clientType}
          </p>
        ) : (
          <span className="inline-form">
            <label>
              Client PAN or client code
              <input
                value={lookup}
                onChange={(e) => setLookup(e.target.value.toUpperCase().replace(/\s/g, ''))}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    findClient();
                  }
                }}
                placeholder="ABCDE1234F or EM1234"
              />
            </label>
            <button type="button" className="secondary" disabled={lookup.trim().length < 3} onClick={findClient}>
              Find
            </button>
          </span>
        )}
        {clientMode === 'pick' &&
          (matches ?? []).map((c) => (
            <label key={c.id} className="check">
              <input type="radio" name="bond-client" checked={client?.id === c.id} onChange={() => setClient(c)} />
              {c.name} · <span className="mono">{c.pan ?? 'no PAN'}</span> · {c.iwellCode ?? 'no code'} · {c.rmName ?? 'no RM'}
            </label>
          ))}
        {!entry && client && (
          <p className="ok">
            {client.name} · <span className="mono">{client.pan ?? 'no PAN'}</span> · Client type: {client.iwellCode ?? 'OFFLINE'}
          </p>
        )}
        {clientMode === 'new' && (
          <>
            <p className="muted">No client has this PAN or code: enter the new client (client type OFFLINE). They are added to Clients with the RM below.</p>
            <label>
              Client name
              <input value={newName} onChange={(e) => setNewName(e.target.value)} required />
            </label>
            <label>
              PAN
              <input value={newPan} onChange={(e) => setNewPan(e.target.value.toUpperCase().replace(/\s/g, ''))} maxLength={10} placeholder="ABCDE1234F" />
            </label>
          </>
        )}
        {(clientMode === 'found' || clientMode === 'new') && (
          <>
            <label>
              Mobile
              <input value={mobile} onChange={(e) => setMobile(e.target.value)} inputMode="tel" />
            </label>
            <label>
              Email
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
            </label>
            <label>
              Address
              <textarea value={address} onChange={(e) => setAddress(e.target.value)} rows={2} />
            </label>
          </>
        )}
      </fieldset>

      <fieldset>
        <legend>Month and RM</legend>
        <label>
          Month
          <input type="month" value={month.slice(0, 7)} onChange={(e) => e.target.value && setMonth(e.target.value)} required />
        </label>
        {rmLocked ? (
          <label>
            RM
            <input value={client?.rmName ?? ''} readOnly />
          </label>
        ) : (
          <label>
            RM
            <select value={rmId} onChange={(e) => setRmId(e.target.value)} required>
              <option value="">Choose…</option>
              {people.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.branchName ? `${p.name} — ${p.branchName}` : p.name}
                </option>
              ))}
            </select>
          </label>
        )}
        <label>
          Referral / sub-broker
          <select value={partnerId} onChange={(e) => setPartnerId(e.target.value)}>
            <option value="">None</option>
            {sharePartners.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} ({p.type === 'SUB_BROKER' ? 'sub-broker' : 'referral'})
              </option>
            ))}
          </select>
        </label>
        <p className="muted">
          {place ? `Region: ${place.region} · RD: ${place.rd} · Branch: ${place.branch}` : 'Region and RD come from the RM.'}
          {rmLocked && ' · The RM comes from the client: change it on the Clients screen.'}
        </p>
      </fieldset>

      <fieldset>
        <legend>Bond</legend>
        <label>
          Partner (platform)
          <select value={platformId} onChange={(e) => setPlatformId(e.target.value)} required>
            <option value="">Choose…</option>
            {(platforms.data ?? []).map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Bond name
          <input value={bondName} onChange={(e) => setBondName(e.target.value)} list="bond-names" required />
        </label>
        <datalist id="bond-names">
          {(suggestions.data?.bondNames ?? []).map((n) => (
            <option key={n} value={n} />
          ))}
        </datalist>
        <label>
          ISIN
          <input value={isin} onChange={(e) => setIsin(e.target.value.toUpperCase())} />
        </label>
        <label>
          Coupon rate %
          <input type="number" min={0} max={100} step="0.0001" value={coupon} onChange={(e) => setCoupon(e.target.value)} />
        </label>
        <label>
          YTM %
          <input type="number" min={0} max={100} step="0.0001" value={ytm} onChange={(e) => setYtm(e.target.value)} />
        </label>
        <label>
          Interest payout frequency
          <select value={payout} onChange={(e) => setPayout(e.target.value as BondPayoutFrequency)}>
            <option value="">—</option>
            {Object.entries(BOND_PAYOUT_LABEL).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <label>
          Interest payment schedule
          <input value={schedule} onChange={(e) => setSchedule(e.target.value)} placeholder="e.g. 10th of every month" />
        </label>
        <label>
          Investment date
          <input type="date" value={invDate} onChange={(e) => setInvDate(e.target.value)} required />
        </label>
        <label>
          Date of allotment
          <input type="date" value={allotDate} onChange={(e) => setAllotDate(e.target.value)} />
        </label>
        <label>
          Maturity / sell date
          <input type="date" value={maturityDate} onChange={(e) => setMaturityDate(e.target.value)} />
        </label>
      </fieldset>

      <fieldset>
        <legend>Amounts</legend>
        <label>
          Quantity
          <input type="number" min={0} step="any" value={qty} onChange={(e) => setQty(e.target.value)} required />
        </label>
        <label>
          Purchase rate per unit (₹)
          <input value={rate} onChange={(e) => setRate(indianDigits(e.target.value))} inputMode="decimal" />
        </label>
        <label>
          Face value (₹)
          <input value={faceValue} onChange={(e) => setFaceValue(indianDigits(e.target.value))} inputMode="decimal" />
        </label>
        <label>
          Investment amount (₹)
          <input
            value={amount}
            onChange={(e) => {
              setAmountTouched(true);
              setAmount(indianDigits(e.target.value));
            }}
            inputMode="decimal"
            required
          />
        </label>
        <label>
          Accrued interest (₹)
          <input
            value={accrued}
            onChange={(e) => setAccrued((e.target.value.trim().startsWith('-') ? '-' : '') + indianDigits(e.target.value))}
            inputMode="decimal"
            title="Negative when the deal is between the record date and the coupon date"
          />
        </label>
        <label>
          Consideration (₹)
          <input value={consideration ? formatMoney(consideration) : ''} readOnly />
        </label>
      </fieldset>

      <fieldset>
        <legend>Brokerage</legend>
        <GivenFiguresFields value={given} onChange={setGiven} />
        <label>
          Mark-up %
          <input
            type="number"
            min={0}
            max={100}
            step="0.0001"
            value={markup}
            onChange={(e) => {
              setMarkupTouched(true);
              setMarkup(e.target.value);
            }}
            required
          />
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
        {lastMarkup && <p className="muted">Mark-up last used for this bond on this platform: {lastMarkup.markupPercent}%</p>}
        <p className="muted">
          {platformId ? `Platform terms: brokerage ${inclusive ? 'includes GST' : 'plus GST'}, our share ${share}%` : 'Choose the platform to see its terms.'}
        </p>
        <dl className="facts">
          <dt>Brokerage</dt>
          <dd>{formatMoney(rev.brokerage)}</dd>
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
        <label>
          Invoice raised on
          <input type="date" value={invoiceDate} onChange={(e) => setInvoiceDate(e.target.value)} />
        </label>
        <label>
          Amount received on
          <input type="date" value={receivedOn} onChange={(e) => setReceivedOn(e.target.value)} />
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
        <label className="check">
          <input type="checkbox" checked={onPortal} onChange={(e) => setOnPortal(e.target.checked)} />
          Updated on Epsilon portal
        </label>
        <label>
          Remarks
          <textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} rows={2} />
        </label>
      </fieldset>

      {error && <p className="error">{error}</p>}
      <span className="inline-form">
        <button type="submit" disabled={busy || clientMode === 'lookup' || clientMode === 'pick'}>
          {entry ? 'Save changes' : 'Add bond'}
        </button>
        {entry && <CancelBond id={entry.id} onDone={onDone} />}
      </span>
    </form>
  );
}

function CancelBond({ id, onDone }: { id: number; onDone: () => void }) {
  const { busy, run } = useSubmit();
  return (
    <button
      type="button"
      className="link"
      disabled={busy}
      onClick={async () => {
        const reason = window.prompt('Why cancel this bond entry?');
        if (reason === null) return;
        if (await run(() => api(`/api/bonds/${id}/cancel`, { method: 'POST', body: { reason } }))) onDone();
      }}
    >
      Cancel this bond
    </button>
  );
}
