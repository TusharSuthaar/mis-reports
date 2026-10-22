import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useTableView } from '../../components/useTableView';
import { api } from '../../api/client';
import type { ClientRow, Employee, NpsCertificate, NpsEntry, OrgChart, Page, Partner, Share, TaxRate } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { Amount } from '../../components/Amount';
import { ListFilters, useFilterQuery } from '../../components/ListFilters';
import { Modal } from '../../components/Modal';
import { useEntryPanel } from '../../components/useEntryPanel';
import { formatDate, formatMoney, todayIso } from '../../format';
import { formatMonth, indianDigits, matchClients } from '../fd/FdPage';

const round2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100;

/** The revenue as the server works it out (R-86): POP charges × sharing % when both are given, else as typed. */
export function npsFigures(popCharges: number | null, sharePct: number | null, typed: number, gstPct: number) {
  const revenue = popCharges != null && sharePct != null ? round2((popCharges * sharePct) / 100) : round2(typed);
  const gst = round2((revenue * gstPct) / 100);
  const cgst = round2(gst / 2);
  return { revenue, cgst, sgst: round2(gst - cgst), gst, invoice: round2(revenue + gst) };
}

const CERTIFICATE: Record<NpsCertificate, string> = { PENDING: 'Pending', RECEIVED: 'Received', NOT_APPLICABLE: 'Not applicable' };
const PAN_FORMAT = /^[A-Z]{5}[0-9]{4}[A-Z]$/;
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

  const monthDay = `${month.slice(0, 7)}-01`;
  const employees = useLoad<Employee[]>('/api/employees');
  const partners = useLoad<Partner[]>('/api/partners');
  const chart = useLoad<OrgChart>(`/api/org?date=${monthDay}`);
  const pops = useLoad<string[]>('/api/nps/pops');
  const tax = useLoad<TaxRate>(`/api/product-taxes/NPS/on?date=${monthDay}`);
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

  const num = (s: string) => Number(s.replace(/,/g, '')) || 0;
  const fromCharges = popCharges !== '' && sharePct !== '';
  const gstPct = entry && entry.month === month.slice(0, 7) ? entry.gstPercent : (tax.data?.gstPercent ?? 0);
  const fig = npsFigures(fromCharges ? num(popCharges) : null, fromCharges ? num(sharePct) : null, num(revenue), gstPct);
  const people = (employees.data ?? []).filter((e) => e.status === 'ACTIVE');
  const sharePartners = (partners.data ?? []).filter((p) => p.type === 'SUB_BROKER' || p.type === 'REFERRAL');

  async function submit(e: FormEvent) {
    e.preventDefault();
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
      <fieldset>
        <legend>Client</legend>
        {entry ? (
          <p>
            {entry.clientName} <span className="mono">{entry.pan}</span>
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
                placeholder="ABCDE1234F or FC01234"
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
              <input type="radio" name="nps-client" checked={client?.id === c.id} onChange={() => setClient(c)} />
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
              Mobile No
              <input value={mobile} onChange={(e) => setMobile(e.target.value)} inputMode="tel" />
            </label>
            <label>
              Email Id
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
          Ref / sub-broker
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
        {entry && <CancelNps id={entry.id} onDone={onDone} />}
      </span>
    </form>
  );
}

function CancelNps({ id, onDone }: { id: number; onDone: () => void }) {
  const { busy, run } = useSubmit();
  return (
    <button
      type="button"
      className="link"
      disabled={busy}
      onClick={async () => {
        const reason = window.prompt('Why cancel this NPS entry?');
        if (reason === null) return;
        if (await run(() => api(`/api/nps/${id}/cancel`, { method: 'POST', body: { reason } }))) onDone();
      }}
    >
      Cancel this entry
    </button>
  );
}
