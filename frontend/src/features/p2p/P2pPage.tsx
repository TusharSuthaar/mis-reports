import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useTableView } from '../../components/useTableView';
import { api } from '../../api/client';
import type { ClientRow, Employee, OrgChart, P2pEntry, Page, Partner, Share, TaxRate } from '../../api/types';
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

/** The brokerage figures as the server works them out (R-87). */
export function p2pFigures(brokerage: number, tdsApplied: boolean, tdsPct: number, gstPct: number) {
  const gross = round2(brokerage);
  const tds = tdsApplied ? round2((gross * tdsPct) / 100) : 0;
  const gst = round2((gross * gstPct) / 100);
  return { gross, tds, gst, net: round2(gross - tds), invoice: round2(gross + gst) };
}

const PAN_FORMAT = /^[A-Z]{5}[0-9]{4}[A-Z]$/;
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

  const monthDay = `${month.slice(0, 7)}-01`;
  const employees = useLoad<Employee[]>('/api/employees');
  const partners = useLoad<Partner[]>('/api/partners');
  const chart = useLoad<OrgChart>(`/api/org?date=${monthDay}`);
  const suggestions = useLoad<{ platforms: string[]; schemes: string[] }>('/api/p2p/suggestions');
  const tax = useLoad<TaxRate>(`/api/product-taxes/P2P/on?date=${monthDay}`);
  const found = useLoad<Page<ClientRow>>(!entry && looked ? `/api/clients?q=${encodeURIComponent(looked)}&size=20` : null);
  const matches = useMemo(() => (looked && found.data ? matchClients(found.data.items, looked) : null), [found.data, looked]);
  const clientMode: 'lookup' | 'found' | 'pick' | 'new' = entry ? 'found' : !matches ? 'lookup' : matches.length === 0 ? 'new' : client ? 'found' : 'pick';
  const rmLocked = !entry && !!client?.rmId;
  const shares = useLoad<Record<string, Share[]>>(client ? `/api/shares?clientIds=${client.id}` : null);

  useEffect(() => {
    if (!entry && tax.data) {
      setTdsApplied(tax.data.tdsByDefault);
      setTdsPct(String(tax.data.tdsPercent));
    }
  }, [entry, tax.data]);
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
  const gstPct = entry && entry.month === month.slice(0, 7) ? entry.gstPercent : (tax.data?.gstPercent ?? 0);
  const fig = p2pFigures(num(brokerage), tdsApplied, num(tdsPct), gstPct);
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
              <input type="radio" name="p2p-client" checked={client?.id === c.id} onChange={() => setClient(c)} />
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
        {entry && <CancelP2p id={entry.id} onDone={onDone} />}
      </span>
    </form>
  );
}

function CancelP2p({ id, onDone }: { id: number; onDone: () => void }) {
  const { busy, run } = useSubmit();
  return (
    <button
      type="button"
      className="link"
      disabled={busy}
      onClick={async () => {
        const reason = window.prompt('Why cancel this P2P entry?');
        if (reason === null) return;
        if (await run(() => api(`/api/p2p/${id}/cancel`, { method: 'POST', body: { reason } }))) onDone();
      }}
    >
      Cancel this entry
    </button>
  );
}
