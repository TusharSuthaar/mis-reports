import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useTableView } from '../../components/useTableView';
import { api } from '../../api/client';
import {
  CERTIFICATE_LABEL,
  PAYMENT_LABEL,
  type AifEntry,
  type AifSuggestions,
  type ClientRow,
  type Employee,
  type FdCertificate,
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
import { formatMoney, todayIso } from '../../format';
import { Amount } from '../../components/Amount';
import { bondRevenue } from '../bonds/BondsPage';
import { formatMonth, indianDigits, matchClients } from '../fd/FdPage';

const round2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100;
const PAN_FORMAT = /^[A-Z]{5}[0-9]{4}[A-Z]$/;
const thisMonth = () => todayIso().slice(0, 7);

/** AIF: one row per commitment, with its drawdowns; revenue on the commitment from the mark-up and the platform's terms. */
export function AifPage({ embedded = false }: { embedded?: boolean } = {}) {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const month = thisMonth();
  const query = useFilterQuery();
  const list = useLoad<AifEntry[]>(`/api/aif${query}`);
  const platforms = useLoad<Platform[]>('/api/platforms');
  const [panel, setPanel] = useEntryPanel();
  const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
  const active = (list.data ?? []).filter((e) => e.status === 'ACTIVE');
  const total = (k: 'commitment' | 'called' | 'gross' | 'net' | 'invoice') => active.reduce((s, e) => s + e[k], 0);
  const done = () => {
    setPanel(null);
    list.reload();
  };

  const view = useTableView(list.data, { month: (e) => e.month, client: (e) => e.clientName, fund: (e) => e.fundName, platform: (e) => e.platformName, rm: (e) => e.rmName, ref_sharing: (e) => e.partnerName, commitment: (e) => e.commitment, called: (e) => e.called, gross: (e) => e.gross, invoice: (e) => e.invoice, payment: (e) => e.paymentStatus });
  return (
    <section>
      <div className="page-head" style={embedded ? { justifyContent: 'flex-end', marginBottom: 12 } : undefined}>
        {!embedded && <h1>AIF</h1>}
        {canEdit && (
          <div className="actions">
            <a className="button-link secondary" href={`/api/aif/export.xlsx${query}`} download>
              Download Excel
            </a>
            <button type="button" onClick={() => setPanel('new')}>
              Add AIF
            </button>
          </div>
        )}
      </div>
      <ListFilters
        searchHint="Client, PAN, code or fund name"
        platformKey="platformId"
        platforms={(platforms.data ?? []).map((p) => ({ value: String(p.id), label: p.name }))}
      />
      {list.error && <p className="error">{list.error}</p>}
      {list.data && list.data.length === 0 && <p className="muted">No AIF commitments match.</p>}
      {list.data && list.data.length > 0 && (
        <>
<div className="table-scroll">
<table>
          <thead>
            <tr>
              <th className="num">SR.NO</th>
              {view.th('month', 'Month')}
              {view.th('client', 'Client')}
              {view.th('fund', 'Fund')}
              {view.th('platform', 'Platform')}
              {view.th('rm', 'RM')}
              {view.th('ref_sharing', 'Ref / sharing')}
              {view.th('commitment', 'Commitment', 'num')}
              {view.th('called', 'Called', 'num')}
              {view.th('gross', 'Gross', 'num')}
              {view.th('invoice', 'Invoice', 'num')}
              {view.th('payment', 'Payment')}
            </tr>
          </thead>
          <tbody>
            {view.rows.map((e, i) => (
              <tr key={e.id} className={e.status === 'CANCELLED' ? 'muted' : undefined}>
                <td className="num">{i + 1}</td>
                <td>{formatMonth(e.month)}</td>
                <td>
                  <button type="button" className="cell-link" onClick={() => setPanel(e.id)}>
                    {e.clientName}
                  </button>
                  {e.status === 'CANCELLED' && <span className="badge">cancelled</span>}
                </td>
                <td>{e.fundName}</td>
                <td>{e.platformName}</td>
                <td>{e.rmName}</td>
                <td>{e.partnerName ? `${e.partnerName}${e.sharingPercent != null ? ` · ${e.sharingPercent}%` : ''}` : <span className="muted">—</span>}</td>
                <td className="num"><Amount value={e.commitment} /></td>
                <td className="num"><Amount value={e.called} /></td>
                <td className="num"><Amount value={e.gross} /></td>
                <td className="num"><Amount value={e.invoice} /></td>
                <td>{e.paymentStatus === 'RECEIVED' ? PAYMENT_LABEL[e.paymentStatus] : <span className="error">{PAYMENT_LABEL[e.paymentStatus]}</span>}</td>
              </tr>
            ))}
            <tr className="total">
              <td colSpan={7}>Total ({active.length} commitments)</td>
              <td className="num"><Amount value={total('commitment')} /></td>
              <td className="num"><Amount value={total('called')} /></td>
              <td className="num"><Amount value={total('gross')} /></td>
              <td className="num"><Amount value={total('invoice')} /></td>
              <td />
            </tr>
          </tbody>
        </table>
</div>
{view.more}
</>
      )}
      {panel === 'new' && (
        <Modal title="Add AIF" onClose={() => setPanel(null)}>
          <AifForm month={month} onDone={done} />
        </Modal>
      )}
      {editing && (
        <Modal title={`${editing.clientName} — ${editing.fundName}`} onClose={() => setPanel(null)}>
          {canEdit && editing.status === 'ACTIVE' ? (
            <AifForm month={month} entry={editing} onDone={done} />
          ) : (
            <p>
              {editing.fundName} · {formatMoney(editing.commitment)} · {editing.status === 'CANCELLED' ? 'Cancelled' : PAYMENT_LABEL[editing.paymentStatus]}
            </p>
          )}
        </Modal>
      )}
    </section>
  );
}

type Drawdown = { amount: string; paidOn: string };

/** Add or edit one AIF commitment, laid out as in the AIF register. */
function AifForm({ month: startMonth, entry, onDone }: { month: string; entry?: AifEntry; onDone: () => void }) {
  const num = (s: string) => Number(s.replace(/,/g, '')) || 0;
  const str = (n: number | null | undefined) => (n == null ? '' : String(n));
  const [month, setMonth] = useState(entry ? entry.month : startMonth);
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
  const [sharing, setSharing] = useState(str(entry?.sharingPercent));
  const [platformId, setPlatformId] = useState(entry ? String(entry.platformId) : '');
  const [fundName, setFundName] = useState(entry?.fundName ?? '');
  const [commitment, setCommitment] = useState(entry ? indianDigits(String(entry.commitment)) : '');
  const [drawdowns, setDrawdowns] = useState<Drawdown[]>(
    entry && entry.instalments.length > 0
      ? entry.instalments.map((i) => ({ amount: indianDigits(String(i.amount)), paidOn: i.paidOn ?? '' }))
      : [{ amount: '', paidOn: '' }],
  );
  const [agreementDate, setAgreementDate] = useState(entry?.agreementDate ?? '');
  const [tenure, setTenure] = useState(entry?.tenure ?? '');
  const [maturityDate, setMaturityDate] = useState(entry?.maturityDate ?? '');
  const [soa, setSoa] = useState(entry?.soaReceived ?? false);
  const [appForm, setAppForm] = useState(entry?.applicationForm ?? false);
  const [mail, setMail] = useState(entry?.investmentMail ?? false);
  const [qty, setQty] = useState(str(entry?.quantity));
  const [price, setPrice] = useState(entry?.price != null ? indianDigits(String(entry.price)) : '');
  const [certificate, setCertificate] = useState<FdCertificate>(entry?.certificate ?? 'PENDING');
  const [payoutTerms, setPayoutTerms] = useState(entry?.payoutTerms ?? '');
  const [markup, setMarkup] = useState(str(entry?.markupPercent));
  const [markupTouched, setMarkupTouched] = useState(!!entry);
  const [tdsApplied, setTdsApplied] = useState(entry?.tdsApplied ?? true);
  const [tdsPct, setTdsPct] = useState(str(entry?.tdsPercent));
  const [invoiceRaised, setInvoiceRaised] = useState(entry?.invoiceRaised ?? false);
  const [invoiceDate, setInvoiceDate] = useState(entry?.invoiceDate ?? '');
  const [receivedOn, setReceivedOn] = useState(entry?.paymentReceivedOn ?? '');
  const [payment, setPayment] = useState<FdPaymentStatus>(entry?.paymentStatus ?? 'PENDING');
  const [partnerPaid, setPartnerPaid] = useState(entry?.partnerPaid ?? false);
  const [onInvestwell, setOnInvestwell] = useState(entry?.onInvestwell ?? false);
  const [remarks, setRemarks] = useState(entry?.remarks ?? '');
  const { busy, error, run } = useSubmit();

  const monthDay = `${month.slice(0, 7)}-01`;
  const employees = useLoad<Employee[]>('/api/employees');
  const partners = useLoad<Partner[]>('/api/partners');
  const platforms = useLoad<Platform[]>('/api/platforms');
  const suggestions = useLoad<AifSuggestions>('/api/aif/suggestions');
  const chart = useLoad<OrgChart>(`/api/org?date=${monthDay}`);
  const tax = useLoad<TaxRate>(`/api/product-taxes/AIF/on?date=${monthDay}`);
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
  useEffect(() => {
    if (!entry && terms.data) {
      setTdsPct(String(terms.data.tdsPercent));
      setTdsApplied(terms.data.tdsPercent > 0);
    }
  }, [entry, terms.data]);
  const lastMarkup = suggestions.data?.markups.find((m) => String(m.platformId) === platformId && m.fundName.toLowerCase() === fundName.trim().toLowerCase());
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

  const sameMonth = entry && entry.month === month.slice(0, 7);
  const share = sameMonth ? entry.ourShare : (terms.data?.ourShare ?? 100);
  const inclusive = sameMonth ? entry.gstInclusive : (terms.data?.gstInclusive ?? false);
  const gstPct = sameMonth ? entry.gstPercent : (tax.data?.gstPercent ?? 0);
  const [given, setGiven] = useGiven(entry);
  const worked = bondRevenue(num(commitment), num(markup), share, inclusive, gstPct, tdsApplied, num(tdsPct));
  const rev = shownFigures(worked, given);
  const called = round2(drawdowns.reduce((s, d) => s + num(d.amount), 0));
  const partnerShare = partnerId && sharing !== '' ? round2((rev.gross * num(sharing)) / 100) : null;
  const people = (employees.data ?? []).filter((e) => e.status === 'ACTIVE');
  const sharePartners = (partners.data ?? []).filter((p) => p.type === 'SUB_BROKER' || p.type === 'REFERRAL');
  const setDrawdown = (i: number, patch: Partial<Drawdown>) => setDrawdowns(drawdowns.map((d, j) => (j === i ? { ...d, ...patch } : d)));

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
      sharingPercent: partnerId ? n(sharing) : null,
      mobile,
      email,
      address,
      platformId: platformId ? Number(platformId) : null,
      fundName,
      commitment: n(commitment),
      agreementDate: agreementDate || null,
      tenure,
      maturityDate: maturityDate || null,
      soaReceived: soa,
      applicationForm: appForm,
      investmentMail: mail,
      quantity: n(qty),
      price: n(price),
      certificate,
      payoutTerms,
      markupPercent: n(markup),
      tdsApplied,
      tdsPercent: tdsApplied ? num(tdsPct) : 0,
      invoiceRaised,
      invoiceDate: invoiceDate || null,
      paymentReceivedOn: receivedOn || null,
      paymentStatus: payment,
      partnerPaid,
      onInvestwell,
      remarks,
      ...givenBody(given),
      instalments: drawdowns.filter((d) => num(d.amount) > 0).map((d) => ({ amount: num(d.amount), paidOn: d.paidOn || null })),
    };
    const ok = await run(() => (entry ? api(`/api/aif/${entry.id}`, { method: 'PUT', body }) : api('/api/aif', { method: 'POST', body })));
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
              <input type="radio" name="aif-client" checked={client?.id === c.id} onChange={() => setClient(c)} />
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
        <legend>Month, RM and revenue sharing</legend>
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
        {partnerId && (
          <label>
            Revenue sharing % (this deal)
            <input type="number" min={0} max={100} step="0.01" value={sharing} onChange={(e) => setSharing(e.target.value)} placeholder="partner's AIF %" />
          </label>
        )}
        <p className="muted">
          {place ? `Region: ${place.region} · RD: ${place.rd} · Branch: ${place.branch}` : 'Region and RD come from the RM.'}
          {rmLocked && ' · The RM comes from the client: change it on the Clients screen.'}
          {partnerId && (sharing === '' ? " · Partner's general AIF % applies." : ` · Partner gets ${sharing}% of gross${partnerShare != null ? ` = ${formatMoney(partnerShare)}` : ''}.`)}
        </p>
      </fieldset>

      <fieldset>
        <legend>AIF</legend>
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
          AIF (fund name)
          <input value={fundName} onChange={(e) => setFundName(e.target.value)} list="aif-funds" required />
        </label>
        <datalist id="aif-funds">
          {(suggestions.data?.fundNames ?? []).map((n) => (
            <option key={n} value={n} />
          ))}
        </datalist>
        <label>
          Commitment (₹)
          <input value={commitment} onChange={(e) => setCommitment(indianDigits(e.target.value))} inputMode="decimal" required />
        </label>
        <label>
          Date of agreement signing
          <input type="date" value={agreementDate} onChange={(e) => setAgreementDate(e.target.value)} />
        </label>
        <label>
          Tenure
          <input value={tenure} onChange={(e) => setTenure(e.target.value)} placeholder="e.g. Five Years" />
        </label>
        <label>
          Maturity date
          <input type="date" value={maturityDate} onChange={(e) => setMaturityDate(e.target.value)} />
        </label>
        <label>
          Quantity
          <input type="number" min={0} step="any" value={qty} onChange={(e) => setQty(e.target.value)} />
        </label>
        <label>
          Price (₹)
          <input value={price} onChange={(e) => setPrice(indianDigits(e.target.value))} inputMode="decimal" />
        </label>
      </fieldset>

      <fieldset>
        <legend>Instalments (drawdowns)</legend>
        {drawdowns.map((d, i) => (
          <span key={i} className="inline-form">
            <label>
              {ordinal(i + 1)} instalment (₹)
              <input value={d.amount} onChange={(e) => setDrawdown(i, { amount: indianDigits(e.target.value) })} inputMode="decimal" />
            </label>
            <label>
              Date of payment
              <input type="date" value={d.paidOn} onChange={(e) => setDrawdown(i, { paidOn: e.target.value })} aria-label={`${ordinal(i + 1)} instalment date of payment`} />
            </label>
            {drawdowns.length > 1 && (
              <button type="button" className="link" onClick={() => setDrawdowns(drawdowns.filter((_, j) => j !== i))}>
                Remove
              </button>
            )}
          </span>
        ))}
        <span className="inline-form">
          <button type="button" className="secondary" onClick={() => setDrawdowns([...drawdowns, { amount: '', paidOn: '' }])}>
            Add instalment
          </button>
          <span className="muted">
            Called so far: <strong>{formatMoney(called)}</strong>
            {num(commitment) > 0 && ` of ${formatMoney(num(commitment))} (${Math.round((called * 100) / num(commitment))}%)`}
          </span>
        </span>
        {num(commitment) > 0 && called > num(commitment) && <p className="error">The instalments add up to more than the commitment.</p>}
      </fieldset>

      <fieldset>
        <legend>Documents</legend>
        <label className="check">
          <input type="checkbox" checked={soa} onChange={(e) => setSoa(e.target.checked)} />
          SOA received
        </label>
        <label className="check">
          <input type="checkbox" checked={appForm} onChange={(e) => setAppForm(e.target.checked)} />
          Application form
        </label>
        <label className="check">
          <input type="checkbox" checked={mail} onChange={(e) => setMail(e.target.checked)} />
          Investment mail
        </label>
        <label className="check">
          <input type="checkbox" checked={onInvestwell} onChange={(e) => setOnInvestwell(e.target.checked)} />
          Updated on Investwell
        </label>
        <label>
          Certificate status
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
        <legend>Revenue</legend>
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
        <label>
          Payout terms
          <input value={payoutTerms} onChange={(e) => setPayoutTerms(e.target.value)} placeholder="e.g. Will be paid quarterly - Oct" />
        </label>
        {lastMarkup && <p className="muted">Mark-up last used for this fund on this platform: {lastMarkup.markupPercent}%</p>}
        <p className="muted">
          {platformId ? `Platform terms: brokerage ${inclusive ? 'includes GST' : 'plus GST'}, our share ${share}%. Revenue is on the commitment.` : 'Choose the platform to see its terms.'}
        </p>
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
        {partnerId && (
          <label className="check">
            <input type="checkbox" checked={partnerPaid} onChange={(e) => setPartnerPaid(e.target.checked)} />
            Revenue share paid to the partner
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
          {entry ? 'Save changes' : 'Add AIF'}
        </button>
        {entry && <CancelAif id={entry.id} onDone={onDone} />}
      </span>
    </form>
  );
}

function ordinal(n: number): string {
  return n === 1 ? '1st' : n === 2 ? '2nd' : n === 3 ? '3rd' : `${n}th`;
}

function CancelAif({ id, onDone }: { id: number; onDone: () => void }) {
  const { busy, run } = useSubmit();
  return (
    <button
      type="button"
      className="link"
      disabled={busy}
      onClick={async () => {
        const reason = window.prompt('Why cancel this AIF entry?');
        if (reason === null) return;
        if (await run(() => api(`/api/aif/${id}/cancel`, { method: 'POST', body: { reason } }))) onDone();
      }}
    >
      Cancel this AIF
    </button>
  );
}

