import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { ClientRow, Employee, OrgChart, Page, Partner, Platform, Share, TaxRate } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { Amount } from '../../components/Amount';
import { ListFilters, useFilterQuery } from '../../components/ListFilters';
import { Modal } from '../../components/Modal';
import { useEntryPanel } from '../../components/useEntryPanel';
import { useTableView } from '../../components/useTableView';
import { formatDate, formatMoney, todayIso } from '../../format';
import { formatMonth, matchClients } from '../fd/FdPage';

export type PreIpoKind = 'BUY' | 'BUYBACK';
export type PortalStatus = 'PENDING' | 'UPDATED' | 'OFFLINE' | 'NOT_TO_UPDATE' | 'LISTED' | 'BOUGHT_BACK' | 'DELETED';
export type BillingStatus = 'TO_CONFIRM' | 'DEAL_DONE' | 'BILLED' | 'PAYMENT_RECEIVED' | 'PAID_BY_CLIENT';
export type DealSheet = 'NA' | 'PENDING' | 'SHARED' | 'SIGNED';

export interface PreIpoDeal {
  id: number;
  kind: PreIpoKind;
  month: string;
  rmId: number;
  rmName: string;
  branchName: string | null;
  region: string | null;
  partnerId: number | null;
  partnerName: string | null;
  sharingPercent: number | null;
  clientId: number;
  clientName: string;
  pan: string | null;
  clientType: string;
  mobile: string | null;
  email: string | null;
  dpName: string | null;
  dpId: string | null;
  dematClientId: string | null;
  scripId: number;
  scripName: string;
  isin: string | null;
  vendorId: number;
  vendorName: string;
  dealDate: string;
  quantity: number;
  clientPrice: number;
  vendorPrice: number;
  markupPerShare: number;
  markupPercent: number;
  amount: number;
  brokerage: number;
  gross: number;
  gstPercent: number;
  gst: number;
  invoice: number;
  tdsApplied: boolean;
  tdsPercent: number;
  tds: number;
  net: number;
  portalStatus: PortalStatus;
  billingStatus: BillingStatus;
  dealSheet: DealSheet;
  remarks: string | null;
  status: 'ACTIVE' | 'CANCELLED';
}

interface Scrip {
  id: number;
  name: string;
  isin: string | null;
  faceValue: number | null;
  lotSize: number | null;
  active: boolean;
  remarks: string | null;
}

interface Price {
  id: number;
  scripId: number;
  vendorId: number;
  vendorName: string;
  asOf: string;
  basePrice: number;
  markupPercent: number;
  clientPrice: number;
  changedBy: string;
}

interface BoardRow {
  scrip: Scrip;
  prices: Price[];
}

export const KIND_LABEL: Record<PreIpoKind, string> = { BUY: 'Buy', BUYBACK: 'Buyback' };
export const BILLING_LABEL: Record<BillingStatus, string> = {
  TO_CONFIRM: 'Need to confirm for billing',
  DEAL_DONE: 'Deal done',
  BILLED: 'Billed',
  PAYMENT_RECEIVED: 'Billed, payment received',
  PAID_BY_CLIENT: 'Commission received from client',
};
const PORTAL_LABEL: Record<PortalStatus, string> = {
  PENDING: 'Pending',
  UPDATED: 'Updated on portal',
  OFFLINE: 'Offline',
  NOT_TO_UPDATE: 'Not to be updated',
  LISTED: 'Listed',
  BOUGHT_BACK: 'Bought back',
  DELETED: 'Deleted',
};
const SHEET_LABEL: Record<DealSheet, string> = { NA: 'NA', PENDING: 'Signed, pending', SHARED: 'Shared', SIGNED: 'Signed and shared' };
const PAID: BillingStatus[] = ['PAYMENT_RECEIVED', 'PAID_BY_CLIENT'];

const round2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100;
const r4 = (n: number) => Math.round((n + Number.EPSILON) * 10000) / 10000;
const PAN_FORMAT = /^[A-Z]{5}[0-9]{4}[A-Z]$/;

export type PriceField = 'pct' | 'markup' | 'client';

/**
 * The deal's prices from the base price and one other field (as in the register: Mark up = client price − base on a buy,
 * price to us − client price on a buyback; Markup in % = Mark up ÷ base). Null without a base price.
 */
export function linkedPrices(kind: PreIpoKind, base: number, from: PriceField, value: number) {
  if (!base) return null;
  const sign = kind === 'BUY' ? 1 : -1;
  const markup = from === 'pct' ? r4((base * value) / 100) : from === 'markup' ? value : r4(sign * (value - base));
  const clientPrice = from === 'client' ? value : round2(base + sign * markup);
  return { markup, markupPercent: r4((markup / base) * 100), clientPrice };
}

/** The revenue as the server works it out (R-93), for the live preview while typing. */
export function preIpoRevenue(kind: PreIpoKind, qty: number, clientPrice: number, vendorPrice: number, gstPct: number, tdsApplied: boolean, tdsPct: number) {
  const markup = kind === 'BUY' ? clientPrice - vendorPrice : vendorPrice - clientPrice;
  const brokerage = round2(qty * markup);
  const gross = round2((brokerage * 100) / (100 + gstPct));
  const gst = round2((gross * gstPct) / 100);
  const tds = tdsApplied ? round2((brokerage * tdsPct) / 100) : 0;
  return { markup, amount: round2(qty * clientPrice), brokerage, gross, gst, invoice: round2(gross + gst), tds, net: round2(brokerage - tds) };
}

/** Pre-IPO (unlisted shares): the deals (buys and buybacks) and the vendors' price list. */
export function PreIpoPage({ embedded = false }: { embedded?: boolean } = {}) {
  const [view, setView] = useState<PreIpoKind | 'PRICES'>('BUY');
  const views: [PreIpoKind | 'PRICES', string][] = [['BUY', 'Buy'], ['BUYBACK', 'Buyback'], ['PRICES', 'Available IPOs']];
  return (
    <section>
      {!embedded && <h1>Pre-IPO</h1>}
      <div className="tabs" role="tablist" aria-label="Pre-IPO">
        {views.map(([k, label]) => (
          <button key={k} type="button" role="tab" aria-selected={view === k} className={view === k ? 'tab active' : 'tab'} onClick={() => setView(k)}>
            {label}
          </button>
        ))}
      </div>
      {view === 'PRICES' ? <AvailableIpos /> : <Deals key={view} kind={view} />}
    </section>
  );
}

/** One kind of deal: the register keeps buys and buybacks on separate sheets, and so does this screen. */
function Deals({ kind }: { kind: PreIpoKind }) {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const filters = useFilterQuery();
  const query = `${filters ? `${filters}&` : '?'}kind=${kind}`;
  const buy = kind === 'BUY';
  const list = useLoad<PreIpoDeal[]>(`/api/preipo${query}`);
  const platforms = useLoad<Platform[]>('/api/platforms');
  const [panel, setPanel] = useEntryPanel();
  const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
  const active = (list.data ?? []).filter((e) => e.status === 'ACTIVE');
  const total = (k: 'amount' | 'brokerage' | 'gross' | 'net') => active.reduce((s, e) => s + e[k], 0);
  const done = () => {
    setPanel(null);
    list.reload();
  };
  const view = useTableView(list.data, {
    month: (e) => e.month, date: (e) => e.dealDate, client: (e) => e.clientName, scrip: (e) => e.scripName,
    vendor: (e) => e.vendorName, rm: (e) => e.rmName, qty: (e) => e.quantity, client_price: (e) => e.clientPrice, vendor_price: (e) => e.vendorPrice, markup: (e) => e.markupPerShare, markup_pct: (e) => e.markupPercent,
    amount: (e) => e.amount, brokerage: (e) => e.brokerage,
    gross: (e) => e.gross, net: (e) => e.net, billing: (e) => e.billingStatus,
  });
  return (
    <>
      <div className="page-head" style={{ justifyContent: 'flex-end', marginBottom: 12 }}>
        {canEdit && (
          <div className="actions">
            <a className="button-link secondary" href={`/api/preipo/export.xlsx${query}`} download>
              Download Excel
            </a>
            <button type="button" onClick={() => setPanel('new')}>
              {buy ? 'Add buy' : 'Add buyback'}
            </button>
          </div>
        )}
      </div>
      <ListFilters
        searchHint="Client, PAN, code, scrip or ISIN"
        platformKey="platformId"
        platformLabel="Vendor"
        platforms={(platforms.data ?? []).map((p) => ({ value: String(p.id), label: p.name }))}
        payments={BILLING_LABEL}
      />
      {list.error && <p className="error">{list.error}</p>}
      {list.data && list.data.length === 0 && <p className="muted">{buy ? 'No buys match.' : 'No buybacks match.'}</p>}
      {list.data && list.data.length > 0 && (
        <>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th className="num">SR.NO</th>
                  {view.th('month', 'Month')}
                  {view.th('date', buy ? 'Invt date' : 'Transaction date')}
                  {view.th('client', 'Client')}
                  {view.th('scrip', 'Pre-IPO')}
                  {view.th('vendor', 'Vendor')}
                  {view.th('rm', 'RM')}
                  {view.th('qty', 'Qty', 'num')}
                  {buy ? view.th('vendor_price', 'Base price', 'num') : view.th('client_price', 'Client price', 'num')}
                  {buy ? view.th('client_price', 'Price to client', 'num') : view.th('vendor_price', 'Price to us', 'num')}
                  {view.th('markup', 'Mark up', 'num')}
                  {view.th('markup_pct', 'Mark up %', 'num')}
                  {view.th('amount', 'Investment amount', 'num')}
                  {view.th('brokerage', 'Gross Brk', 'num')}
                  {view.th('gross', 'Without GST', 'num')}
                  {view.th('net', 'Net revenue', 'num')}
                  {view.th('billing', 'Billing')}
                </tr>
              </thead>
              <tbody>
                {view.rows.map((e, i) => (
                  <tr key={e.id} className={e.status === 'CANCELLED' ? 'muted' : undefined}>
                    <td className="num">{i + 1}</td>
                    <td className="nowrap">{formatMonth(e.month)}</td>
                    <td className="nowrap">{formatDate(e.dealDate)}</td>
                    <td>
                      <button type="button" className="cell-link" onClick={() => setPanel(e.id)}>
                        {e.clientName}
                      </button>
                      {e.status === 'CANCELLED' && <span className="badge">cancelled</span>}
                      {e.partnerName && <div className="muted">{e.partnerName}</div>}
                    </td>
                    <td>
                      {e.scripName}
                      {e.isin ? <span className="muted mono"> {e.isin}</span> : null}
                    </td>
                    <td>{e.vendorName}</td>
                    <td>{e.rmName}</td>
                    <td className="num">{e.quantity.toLocaleString('en-IN')}</td>
                    <td className="num">{formatMoney(buy ? e.vendorPrice : e.clientPrice)}</td>
                    <td className="num">{formatMoney(buy ? e.clientPrice : e.vendorPrice)}</td>
                    <td className="num">{formatMoney(e.markupPerShare)}</td>
                    <td className="num">{e.markupPercent.toFixed(2)}%</td>
                    <td className="num"><Amount value={e.amount} /></td>
                    <td className="num"><Amount value={e.brokerage} /></td>
                    <td className="num"><Amount value={e.gross} /></td>
                    <td className="num"><Amount value={e.net} /></td>
                    <td>{PAID.includes(e.billingStatus) ? BILLING_LABEL[e.billingStatus] : <span className="error">{BILLING_LABEL[e.billingStatus]}</span>}</td>
                  </tr>
                ))}
                <tr className="total">
                  <td colSpan={12}>Total ({active.length} {buy ? 'buys' : 'buybacks'})</td>
                  <td className="num"><Amount value={total('amount')} /></td>
                  <td className="num"><Amount value={total('brokerage')} /></td>
                  <td className="num"><Amount value={total('gross')} /></td>
                  <td className="num"><Amount value={total('net')} /></td>
                  <td />
                </tr>
              </tbody>
            </table>
          </div>
          {view.more}
        </>
      )}
      {panel === 'new' && (
        <Modal title={buy ? 'Add Pre-IPO buy' : 'Add Pre-IPO buyback'} onClose={() => setPanel(null)}>
          <DealForm kind={kind} onDone={done} />
        </Modal>
      )}
      {editing && (
        <Modal title={`${editing.clientName} — ${KIND_LABEL[editing.kind]} ${editing.scripName}`} onClose={() => setPanel(null)}>
          {canEdit && editing.status === 'ACTIVE' ? (
            <DealForm kind={editing.kind} entry={editing} onDone={done} />
          ) : (
            <p>
              {KIND_LABEL[editing.kind]} · {editing.scripName} · {formatMoney(editing.amount)} · {editing.status === 'CANCELLED' ? 'Cancelled' : BILLING_LABEL[editing.billingStatus]}
            </p>
          )}
        </Modal>
      )}
    </>
  );
}

/** Add or edit one deal, laid out as in the Pre-IPO register. Scrip + vendor + date → prices from the price list. */
function DealForm({ kind, entry, onDone }: { kind: PreIpoKind; entry?: PreIpoDeal; onDone: () => void }) {
  const num = (s: string) => Number(s.replace(/,/g, '')) || 0;
  const str = (n: number | null | undefined) => (n == null ? '' : String(n));
  const [month, setMonth] = useState(entry ? entry.month : todayIso().slice(0, 7));
  const [rmId, setRmId] = useState(entry ? String(entry.rmId) : '');
  const [lookup, setLookup] = useState('');
  const [looked, setLooked] = useState('');
  const [client, setClient] = useState<ClientRow | null>(null);
  const [newName, setNewName] = useState('');
  const [newPan, setNewPan] = useState('');
  const [mobile, setMobile] = useState(entry?.mobile ?? '');
  const [email, setEmail] = useState(entry?.email ?? '');
  const [dpName, setDpName] = useState(entry?.dpName ?? '');
  const [dpId, setDpId] = useState(entry?.dpId ?? '');
  const [dematId, setDematId] = useState(entry?.dematClientId ?? '');
  const [partnerId, setPartnerId] = useState(entry?.partnerId ? String(entry.partnerId) : '');
  const [sharing, setSharing] = useState(str(entry?.sharingPercent));
  const [scripId, setScripId] = useState(entry ? String(entry.scripId) : '');
  const [vendorId, setVendorId] = useState(entry ? String(entry.vendorId) : '');
  const [dealDate, setDealDate] = useState(entry?.dealDate ?? todayIso());
  const [qty, setQty] = useState(str(entry?.quantity));
  const [clientPrice, setClientPrice] = useState(str(entry?.clientPrice));
  const [vendorPrice, setVendorPrice] = useState(str(entry?.vendorPrice));
  const [markup, setMarkup] = useState(str(entry?.markupPerShare));
  const [markupPct, setMarkupPct] = useState(entry ? String(entry.markupPercent) : '');
  const [pricesTouched, setPricesTouched] = useState(!!entry);
  const [tdsApplied, setTdsApplied] = useState(entry?.tdsApplied ?? true);
  const [tdsPct, setTdsPct] = useState(str(entry?.tdsPercent));
  const [portal, setPortal] = useState<PortalStatus>(entry?.portalStatus ?? 'PENDING');
  const [billing, setBilling] = useState<BillingStatus>(entry?.billingStatus ?? 'TO_CONFIRM');
  const [sheet, setSheet] = useState<DealSheet>(entry?.dealSheet ?? 'NA');
  const [remarks, setRemarks] = useState(entry?.remarks ?? '');
  const { busy, error, run } = useSubmit();

  const monthDay = `${month.slice(0, 7)}-01`;
  const employees = useLoad<Employee[]>('/api/employees');
  const partners = useLoad<Partner[]>('/api/partners');
  const platforms = useLoad<Platform[]>('/api/platforms');
  const scrips = useLoad<Scrip[]>('/api/preipo/scrips');
  const board = useLoad<BoardRow[]>(dealDate ? `/api/preipo/prices?date=${dealDate}` : null);
  const chart = useLoad<OrgChart>(`/api/org?date=${monthDay}`);
  const tax = useLoad<TaxRate>(`/api/product-taxes/PREIPO/on?date=${monthDay}`);
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
  // A new deal takes the Pre-IPO TDS (5% to start).
  useEffect(() => {
    if (!entry && tax.data) {
      setTdsPct(String(tax.data.tdsPercent));
      setTdsApplied(tax.data.tdsByDefault);
    }
  }, [entry, tax.data]);
  // The vendor's price on the deal date fills the prices until they are typed by hand.
  const listed = board.data?.find((r) => String(r.scrip.id) === scripId)?.prices.find((p) => String(p.vendorId) === vendorId);
  useEffect(() => {
    if (pricesTouched || !listed) return;
    setVendorPrice(String(listed.basePrice));
    setMarkupPct(String(listed.markupPercent));
    fill(listed.basePrice, 'pct', listed.markupPercent);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [listed, kind, pricesTouched]);
  /**
   * Base price, mark-up per share, mark-up % and client price fill each other: a buy adds the mark-up to the base price,
   * a buyback takes it off. {@code from} is the field just typed; the base price keeps the mark-up % (or the client price).
   */
  function fill(base: number, from: PriceField, value: number) {
    const p = linkedPrices(kind, base, from, value);
    if (!p) return;
    if (from !== 'markup') setMarkup(String(p.markup));
    if (from !== 'pct') setMarkupPct(String(p.markupPercent));
    if (from !== 'client') setClientPrice(String(p.clientPrice));
  }
  const typed = (setter: (v: string) => void, from: PriceField) => (v: string) => {
    setPricesTouched(true);
    setter(v);
    if (v !== '') fill(num(vendorPrice), from, num(v));
  };
  const typedBase = (v: string) => {
    setPricesTouched(true);
    setVendorPrice(v);
    if (markupPct !== '') fill(num(v), 'pct', num(markupPct));
    else if (clientPrice !== '') fill(num(v), 'client', num(clientPrice));
  };
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

  const gstPct = entry && entry.month === month.slice(0, 7) ? entry.gstPercent : (tax.data?.gstPercent ?? 18);
  const rev = preIpoRevenue(kind, num(qty), num(clientPrice), num(vendorPrice), gstPct, tdsApplied, num(tdsPct));
  const people = (employees.data ?? []).filter((e) => e.status === 'ACTIVE');
  const sharePartners = (partners.data ?? []).filter((p) => p.type === 'SUB_BROKER' || p.type === 'REFERRAL');
  const scrip = scrips.data?.find((s) => String(s.id) === scripId);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const body = {
      clientId: entry ? entry.clientId : clientMode === 'found' ? client?.id : null,
      newClientName: clientMode === 'new' ? newName : null,
      newClientPan: clientMode === 'new' && newPan ? newPan : null,
      kind,
      month: monthDay,
      rmId: Number(rmId),
      partnerId: partnerId ? Number(partnerId) : null,
      sharingPercent: partnerId && sharing !== '' ? num(sharing) : null,
      mobile,
      email,
      dpName,
      dpId,
      dematClientId: dematId,
      scripId: scripId ? Number(scripId) : null,
      vendorId: vendorId ? Number(vendorId) : null,
      dealDate: dealDate || null,
      quantity: num(qty),
      clientPrice: num(clientPrice),
      vendorPrice: num(vendorPrice),
      tdsApplied,
      tdsPercent: tdsApplied ? num(tdsPct) : 0,
      portalStatus: portal,
      billingStatus: billing,
      dealSheet: sheet,
      remarks,
    };
    const ok = await run(() => (entry ? api(`/api/preipo/${entry.id}`, { method: 'PUT', body }) : api('/api/preipo', { method: 'POST', body })));
    if (ok) onDone();
  }

  return (
    <form onSubmit={submit} className="fd-form">
      {tax.error && <p className="error">{tax.error}</p>}
      <p className="muted" style={{ marginTop: 0 }}>
        {kind === 'BUY' ? 'Client buys: our mark-up is added to the vendor base price.' : 'Client sells back: our mark-up is taken off the price the vendor pays.'}
      </p>
      <fieldset>
        <legend>Client</legend>
        {entry ? (
          <p>
            {entry.clientName} <span className="mono">{entry.pan}</span> · Client type: {entry.clientType}
          </p>
        ) : (
          <>
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
            <button type="button" className="secondary field-button" disabled={lookup.trim().length < 3} onClick={findClient}>
              Find client
            </button>
          </>
        )}
        {clientMode === 'pick' &&
          (matches ?? []).map((c) => (
            <label key={c.id} className="check">
              <input type="radio" name="preipo-client" checked={client?.id === c.id} onChange={() => setClient(c)} />
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
              DP name
              <input value={dpName} onChange={(e) => setDpName(e.target.value)} />
            </label>
            <label>
              DP ID
              <input value={dpId} onChange={(e) => setDpId(e.target.value)} />
            </label>
            <label>
              Demat client ID
              <input value={dematId} onChange={(e) => setDematId(e.target.value)} />
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
        {partnerId && (
          <label>
            Sharing % for this deal
            <input type="number" min={0} max={100} step="0.01" value={sharing} onChange={(e) => setSharing(e.target.value)} placeholder="Blank: the partner's Pre-IPO %" />
          </label>
        )}
        <p className="muted">
          {place ? `Region: ${place.region} · RD: ${place.rd} · Branch: ${place.branch}` : 'Region and RD come from the RM.'}
          {rmLocked && ' · The RM comes from the client: change it on the Clients screen.'}
        </p>
      </fieldset>

      <fieldset>
        <legend>Pre-IPO</legend>
        <label>
          Pre-IPO (scrip)
          <select value={scripId} onChange={(e) => setScripId(e.target.value)} required>
            <option value="">Choose…</option>
            {(scrips.data ?? []).filter((s) => s.active || String(s.id) === scripId).map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
                {s.isin ? ` (${s.isin})` : ''}
              </option>
            ))}
          </select>
        </label>
        <label>
          Vendor (partner)
          <select value={vendorId} onChange={(e) => setVendorId(e.target.value)} required>
            <option value="">Choose…</option>
            {(platforms.data ?? []).map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          {kind === 'BUY' ? 'Investment date' : 'Transaction date'}
          <input type="date" value={dealDate} onChange={(e) => setDealDate(e.target.value)} required />
        </label>
        <label>
          Quantity
          <input type="number" min={0} step="any" value={qty} onChange={(e) => setQty(e.target.value)} required />
        </label>
        <label>
          {kind === 'BUY' ? 'Base price (₹)' : 'Price to us (₹)'}
          <input type="number" min={0} step="any" value={vendorPrice} onChange={(e) => typedBase(e.target.value)} required />
        </label>
        <label>
          Mark up (₹ per share)
          <input type="number" min={0} step="any" value={markup} onChange={(e) => typed(setMarkup, 'markup')(e.target.value)} />
        </label>
        <label>
          Mark up %
          <input type="number" min={0} max={100} step="any" value={markupPct} onChange={(e) => typed(setMarkupPct, 'pct')(e.target.value)} />
        </label>
        <label>
          {kind === 'BUY' ? 'Price offered to client (₹)' : 'Client price (₹)'}
          <input type="number" min={0} step="any" value={clientPrice} onChange={(e) => typed(setClientPrice, 'client')(e.target.value)} required />
        </label>
        <p className="muted">
          {scrip?.lotSize ? `Minimum lot ${scrip.lotSize}. ` : ''}
          {listed
            ? `Price list on ${formatDate(listed.asOf)}: base ${formatMoney(listed.basePrice)}, client ${formatMoney(listed.clientPrice)} (${listed.markupPercent}% mark-up).`
            : scripId && vendorId ? 'This vendor has no price for this scrip on the deal date: type the prices.' : 'Choose the scrip and vendor to fill the prices from Available IPOs.'}
        </p>
      </fieldset>

      <fieldset>
        <legend>Brokerage</legend>
        <label>
          TDS
          <select value={tdsApplied ? 'YES' : 'NO'} onChange={(e) => setTdsApplied(e.target.value === 'YES')}>
            <option value="YES">Deducted by the vendor</option>
            <option value="NO">Not deducted</option>
          </select>
        </label>
        <label>
          TDS % (on Gross Brk)
          <input type="number" min={0} max={100} step="0.01" value={tdsApplied ? tdsPct : '0'} disabled={!tdsApplied} onChange={(e) => setTdsPct(e.target.value)} required />
        </label>
        <label>
          GST %
          <input value={gstPct} readOnly />
        </label>
        {rev.markup < 0 && (
          <p className="error">{kind === 'BUY' ? 'The client price is below the vendor base price.' : 'The client price is above what the vendor pays.'}</p>
        )}
        <div className="figures">
          {(
            [
              ['Mark-up per share', rev.markup],
              ['Investment amount', rev.amount],
              ['Gross Brk (incl. GST)', rev.brokerage],
              ['Without GST', rev.gross],
              [`GST (${gstPct}%)`, rev.gst],
              ['TDS', rev.tds],
              ['Invoice amount', rev.invoice],
              ['Net revenue', rev.net],
            ] as const
          ).map(([label, value]) => (
            <div key={label} className={label === 'Net revenue' ? 'figure key' : 'figure'}>
              <span>{label}</span>
              <b>{formatMoney(value)}</b>
            </div>
          ))}
        </div>
      </fieldset>

      <fieldset>
        <legend>Follow-up</legend>
        <label>
          Epsilon portal
          <select value={portal} onChange={(e) => setPortal(e.target.value as PortalStatus)}>
            {Object.entries(PORTAL_LABEL).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <label>
          Billing
          <select value={billing} onChange={(e) => setBilling(e.target.value as BillingStatus)}>
            {Object.entries(BILLING_LABEL).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <label>
          Deal sheet
          <select value={sheet} onChange={(e) => setSheet(e.target.value as DealSheet)}>
            {Object.entries(SHEET_LABEL).map(([k, v]) => (
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
        <button type="submit" disabled={busy || clientMode === 'lookup' || clientMode === 'pick' || rev.markup < 0}>
          {entry ? 'Save changes' : kind === 'BUY' ? 'Add buy' : 'Add buyback'}
        </button>
        {entry && <CancelDeal id={entry.id} onDone={onDone} />}
      </span>
    </form>
  );
}

function CancelDeal({ id, onDone }: { id: number; onDone: () => void }) {
  const { busy, run } = useSubmit();
  const [asking, setAsking] = useState(false);
  const [reason, setReason] = useState('');
  if (!asking) {
    return (
      <button type="button" className="link" disabled={busy} onClick={() => setAsking(true)}>
        Cancel this deal
      </button>
    );
  }
  return (
    <span className="inline-form">
      <label>
        Why cancel?
        <input value={reason} onChange={(e) => setReason(e.target.value)} autoFocus />
      </label>
      <button
        type="button"
        className="secondary"
        disabled={busy}
        onClick={async () => {
          if (await run(() => api(`/api/preipo/${id}/cancel`, { method: 'POST', body: { reason } }))) onDone();
        }}
      >
        Cancel deal
      </button>
    </span>
  );
}

/** The price list: every scrip with each vendor's latest base price and client price on a day. */
function AvailableIpos() {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const [date, setDate] = useState(todayIso());
  const board = useLoad<BoardRow[]>(`/api/preipo/prices?date=${date}`);
  const [open, setOpen] = useState<Scrip | 'new' | 'upload' | null>(null);
  const rows = board.data ?? [];
  const view = useTableView(rows, {
    scrip: (r) => r.scrip.name, isin: (r) => r.scrip.isin, lot: (r) => r.scrip.lotSize,
    base: (r) => r.prices[0]?.basePrice ?? null, client: (r) => r.prices[0]?.clientPrice ?? null, vendors: (r) => r.prices.length,
  });
  const done = () => {
    setOpen(null);
    board.reload();
  };
  return (
    <>
      <div className="page-head" style={{ marginBottom: 12 }}>
        <label className="inline">
          Prices on
          <input type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} />
        </label>
        {canEdit && (
          <div className="actions">
            <button type="button" className="secondary" onClick={() => setOpen('new')}>
              Add scrip
            </button>
            <button type="button" onClick={() => setOpen('upload')}>
              Upload price sheet
            </button>
          </div>
        )}
      </div>
      <p className="muted">Each vendor's latest price on or before the date. Lowest base price first; click a scrip for its price history or to set a price.</p>
      {board.error && <p className="error">{board.error}</p>}
      {board.data && rows.length === 0 && <div className="empty">No scrips yet. Upload the Pre-IPO price sheet or add a scrip.</div>}
      {rows.length > 0 && (
        <>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  {view.th('scrip', 'Scrip')}
                  {view.th('isin', 'ISIN')}
                  {view.th('lot', 'Min. lot', 'num')}
                  {view.th('base', 'Best base price', 'num')}
                  {view.th('client', 'Client price', 'num')}
                  {view.th('vendors', 'All vendors')}
                </tr>
              </thead>
              <tbody>
                {view.rows.map((r) => {
                  const best = r.prices[0];
                  return (
                    <tr key={r.scrip.id}>
                      <td>
                        <button type="button" className="cell-link" onClick={() => setOpen(r.scrip)}>
                          {r.scrip.name}
                        </button>
                      </td>
                      <td className="mono">{r.scrip.isin ?? '—'}</td>
                      <td className="num">{r.scrip.lotSize?.toLocaleString('en-IN') ?? '—'}</td>
                      <td className="num">{best ? <>{formatMoney(best.basePrice)} <span className="muted">{best.vendorName}</span></> : <span className="muted">no price</span>}</td>
                      <td className="num">{best ? formatMoney(best.clientPrice) : ''}</td>
                      <td>
                        {r.prices.map((p) => (
                          <span key={p.vendorId} className="badge" title={`as on ${formatDate(p.asOf)}`} style={{ marginRight: 4 }}>
                            {p.vendorName} {formatMoney(p.basePrice)}
                          </span>
                        ))}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          {view.more}
        </>
      )}
      {open === 'upload' && (
        <Modal title="Upload price sheet" onClose={() => setOpen(null)}>
          <UploadPrices onDone={done} />
        </Modal>
      )}
      {open === 'new' && (
        <Modal title="Add scrip" onClose={() => setOpen(null)}>
          <ScripForm onDone={done} />
        </Modal>
      )}
      {open && typeof open === 'object' && (
        <Modal title={open.name} onClose={() => setOpen(null)}>
          <ScripPanel scrip={open} canEdit={canEdit} onDone={done} />
        </Modal>
      )}
    </>
  );
}

function UploadPrices({ onDone }: { onDone: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [asOf, setAsOf] = useState(todayIso());
  const [markup, setMarkup] = useState('4');
  const [result, setResult] = useState<{ scrips: number; newScrips: number; prices: number; withoutPrice: number; newVendors: string[]; skippedRows: number } | null>(null);
  const { busy, error, run } = useSubmit();
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!file) return;
    const form = new FormData();
    form.append('file', file);
    form.append('asOf', asOf);
    form.append('markupPercent', markup || '4');
    await run(async () => setResult(await api('/api/preipo/prices/upload', { method: 'POST', body: form })));
  }
  if (result) {
    return (
      <div>
        <p className="ok">
          {result.prices} prices for {result.scrips} scrips saved as on {formatDate(asOf)}
          {result.newScrips ? `; ${result.newScrips} new scrips added` : ''}.
        </p>
        {result.newVendors.length > 0 && <p className="muted">New vendors added to Platforms: {result.newVendors.join(', ')}.</p>}
        {result.withoutPrice > 0 && <p className="muted">{result.withoutPrice} scrips had no vendor price that day: they are listed without a price.</p>}
        {result.skippedRows > 0 && <p className="muted">{result.skippedRows} rows were notes or other lists (no ISIN, no price) and were skipped.</p>}
        <button type="button" onClick={onDone}>
          Done
        </button>
      </div>
    );
  }
  return (
    <form onSubmit={submit} className="fd-form">
      <p className="muted">
        The Pre-IPO master file's "Purchase Price Availabity" sheet: ISIN, scrip name, face value, lot size, and one "Base Price (INR)" column per vendor
        with the vendor's name above it.
      </p>
      <label>
        File
        <input type="file" accept=".xlsx" onChange={(e) => setFile(e.target.files?.[0] ?? null)} required />
      </label>
      <label>
        Prices as on
        <input type="date" value={asOf} onChange={(e) => setAsOf(e.target.value)} required />
      </label>
      <label>
        Mark-up % for client price
        <input type="number" min={0} max={100} step="0.01" value={markup} onChange={(e) => setMarkup(e.target.value)} />
      </label>
      {error && <p className="error">{error}</p>}
      <button type="submit" disabled={busy || !file}>
        Upload
      </button>
    </form>
  );
}

function ScripForm({ scrip, onDone }: { scrip?: Scrip; onDone: () => void }) {
  const [name, setName] = useState(scrip?.name ?? '');
  const [isin, setIsin] = useState(scrip?.isin ?? '');
  const [face, setFace] = useState(scrip?.faceValue != null ? String(scrip.faceValue) : '');
  const [lot, setLot] = useState(scrip?.lotSize != null ? String(scrip.lotSize) : '');
  const [active, setActive] = useState(scrip?.active ?? true);
  const { busy, error, run } = useSubmit();
  async function submit(e: FormEvent) {
    e.preventDefault();
    const body = { name, isin, faceValue: face ? Number(face) : null, lotSize: lot ? Number(lot) : null, active };
    if (await run(() => (scrip ? api(`/api/preipo/scrips/${scrip.id}`, { method: 'PUT', body }) : api('/api/preipo/scrips', { method: 'POST', body })))) onDone();
  }
  return (
    <form onSubmit={submit} className="fd-form">
      <label>
        Scrip name
        <input value={name} onChange={(e) => setName(e.target.value)} required />
      </label>
      <label>
        ISIN
        <input value={isin} onChange={(e) => setIsin(e.target.value.toUpperCase())} />
      </label>
      <label>
        Face value
        <input type="number" min={0} step="any" value={face} onChange={(e) => setFace(e.target.value)} />
      </label>
      <label>
        Minimum lot
        <input type="number" min={1} step={1} value={lot} onChange={(e) => setLot(e.target.value)} />
      </label>
      {scrip && (
        <label className="check">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
          Still offered (unticked: hidden from the price list)
        </label>
      )}
      {error && <p className="error">{error}</p>}
      <button type="submit" disabled={busy}>
        {scrip ? 'Save scrip' : 'Add scrip'}
      </button>
    </form>
  );
}

/** One scrip: its details, every price it has had, and a form to set a vendor's price from a date. */
function ScripPanel({ scrip, canEdit, onDone }: { scrip: Scrip; canEdit: boolean; onDone: () => void }) {
  const history = useLoad<Price[]>(`/api/preipo/scrips/${scrip.id}/prices`);
  const platforms = useLoad<Platform[]>('/api/platforms');
  const [vendorId, setVendorId] = useState('');
  const [asOf, setAsOf] = useState(todayIso());
  const [base, setBase] = useState('');
  const [markup, setMarkup] = useState('4');
  const [editing, setEditing] = useState(false);
  const { busy, error, run } = useSubmit();
  async function submit(e: FormEvent) {
    e.preventDefault();
    const ok = await run(() =>
      api('/api/preipo/prices', { method: 'POST', body: { scripId: scrip.id, vendorId: Number(vendorId), asOf, basePrice: Number(base), markupPercent: Number(markup) } }),
    );
    if (ok) {
      setBase('');
      history.reload();
    }
  }
  if (editing) return <ScripForm scrip={scrip} onDone={onDone} />;
  return (
    <div className="fd-form">
      <p>
        {scrip.isin ? <span className="mono">{scrip.isin}</span> : 'No ISIN'} · Face value {scrip.faceValue ?? '—'} · Minimum lot {scrip.lotSize ?? '—'}
        {canEdit && (
          <>
            {' '}
            <button type="button" className="link" onClick={() => setEditing(true)}>
              Edit scrip
            </button>
          </>
        )}
      </p>
      <h3>Price history</h3>
      {history.data && history.data.length === 0 && <p className="muted">No prices yet.</p>}
      {history.data && history.data.length > 0 && (
        <table className="compact">
          <thead>
            <tr>
              <th>As on</th>
              <th>Vendor</th>
              <th className="num">Base</th>
              <th className="num">Mark-up %</th>
              <th className="num">Client price</th>
              <th>By</th>
            </tr>
          </thead>
          <tbody>
            {history.data.map((p) => (
              <tr key={p.id}>
                <td>{formatDate(p.asOf)}</td>
                <td>{p.vendorName}</td>
                <td className="num">{formatMoney(p.basePrice)}</td>
                <td className="num">{p.markupPercent}</td>
                <td className="num">{formatMoney(p.clientPrice)}</td>
                <td className="muted">{p.changedBy}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {canEdit && (
        <form onSubmit={submit} className="fd-form">
          <h3>Set a price</h3>
          <label>
            Vendor
            <select value={vendorId} onChange={(e) => setVendorId(e.target.value)} required>
              <option value="">Choose…</option>
              {(platforms.data ?? []).map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            From
            <input type="date" value={asOf} onChange={(e) => setAsOf(e.target.value)} required />
          </label>
          <label>
            Base price (₹)
            <input type="number" min={0} step="any" value={base} onChange={(e) => setBase(e.target.value)} required />
          </label>
          <label>
            Mark-up %
            <input type="number" min={0} max={100} step="0.01" value={markup} onChange={(e) => setMarkup(e.target.value)} />
          </label>
          <p className="muted">Client price: {base ? formatMoney(round2((Number(base) * (100 + Number(markup || 0))) / 100)) : '—'}</p>
          {error && <p className="error">{error}</p>}
          <button type="submit" disabled={busy}>
            Save price
          </button>
        </form>
      )}
    </div>
  );
}
