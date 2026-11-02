import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { PAN_FORMAT, matchClients } from '../entry/ClientAndRm';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../../api/client';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { Amount } from '../../components/Amount';
import { ListFilters, listPath, useFilterQuery } from '../../components/ListFilters';
import { Modal } from '../../components/Modal';
import { useEntryPanel } from '../../components/useEntryPanel';
import { useTableView } from '../../components/useTableView';
import { formatDate, formatMoney, todayIso, formatMonth } from '../../format';
export const KIND_LABEL = { BUY: 'Buy', BUYBACK: 'Buyback' };
export const BILLING_LABEL = {
    TO_CONFIRM: 'Need to confirm for billing',
    DEAL_DONE: 'Deal done',
    BILLED: 'Billed',
    PAYMENT_RECEIVED: 'Billed, payment received',
    PAID_BY_CLIENT: 'Commission received from client',
};
const PORTAL_LABEL = {
    PENDING: 'Pending',
    UPDATED: 'Updated on portal',
    OFFLINE: 'Offline',
    NOT_TO_UPDATE: 'Not to be updated',
    LISTED: 'Listed',
    BOUGHT_BACK: 'Bought back',
    DELETED: 'Deleted',
};
const SHEET_LABEL = { NA: 'NA', PENDING: 'Signed, pending', SHARED: 'Shared', SIGNED: 'Signed and shared' };
const PAID = ['PAYMENT_RECEIVED', 'PAID_BY_CLIENT'];
const round2 = (n) => Math.round((n + Number.EPSILON) * 100) / 100;
const r4 = (n) => Math.round((n + Number.EPSILON) * 10000) / 10000;
/**
 * The deal's prices from the base price and one other field (as in the register: Mark up = client price − base on a buy,
 * price to us − client price on a buyback; Markup in % = Mark up ÷ base). Null without a base price.
 */
export function linkedPrices(kind, base, from, value) {
    if (!base)
        return null;
    const sign = kind === 'BUY' ? 1 : -1;
    const markup = from === 'pct' ? r4((base * value) / 100) : from === 'markup' ? value : r4(sign * (value - base));
    const clientPrice = from === 'client' ? value : round2(base + sign * markup);
    return { markup, markupPercent: r4((markup / base) * 100), clientPrice };
}
/** The revenue as the server works it out (R-93), for the live preview while typing. */
export function preIpoRevenue(kind, qty, clientPrice, vendorPrice, gstPct, tdsApplied, tdsPct) {
    const markup = kind === 'BUY' ? clientPrice - vendorPrice : vendorPrice - clientPrice;
    const brokerage = round2(qty * markup);
    const gross = round2((brokerage * 100) / (100 + gstPct));
    const gst = round2((gross * gstPct) / 100);
    const tds = tdsApplied ? round2((brokerage * tdsPct) / 100) : 0;
    return { markup, amount: round2(qty * clientPrice), brokerage, gross, gst, invoice: round2(gross + gst), tds, net: round2(brokerage - tds) };
}
/** Unlisted shares (Pre-IPO, R-97): the deals (buys and buybacks) and the vendors' price list. */
export function PreIpoPage({ embedded = false } = {}) {
    const [view, setView] = useState('BUY');
    const views = [['BUY', 'Buy'], ['BUYBACK', 'Buyback'], ['PRICES', 'Available IPOs']];
    return (_jsxs("section", { children: [!embedded && _jsx("h1", { children: "Unlisted" }), _jsx("div", { className: "tabs", role: "tablist", "aria-label": "Unlisted", children: views.map(([k, label]) => (_jsx("button", { type: "button", role: "tab", "aria-selected": view === k, className: view === k ? 'tab active' : 'tab', onClick: () => setView(k), children: label }, k))) }), view === 'PRICES' ? _jsx(AvailableIpos, {}) : _jsx(Deals, { kind: view }, view)] }));
}
/** One kind of deal: the register keeps buys and buybacks on separate sheets, and so does this screen. */
function Deals({ kind }) {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const filters = useFilterQuery();
    const query = filters === null ? null : `${filters ? `${filters}&` : '?'}kind=${kind}`;
    const buy = kind === 'BUY';
    const list = useLoad(listPath('/api/preipo', query));
    const platforms = useLoad('/api/platforms');
    const [panel, setPanel] = useEntryPanel();
    const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
    const active = (list.data ?? []).filter((e) => e.status === 'ACTIVE');
    const total = (k) => active.reduce((s, e) => s + e[k], 0);
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
    return (_jsxs(_Fragment, { children: [_jsx("div", { className: "page-head", style: { justifyContent: 'flex-end', marginBottom: 12 }, children: canEdit && (_jsxs("div", { className: "actions", children: [_jsx("a", { className: "button-link secondary", href: `/api/preipo/export.xlsx${query ?? ''}`, download: true, children: "Download Excel" }), _jsx("button", { type: "button", onClick: () => setPanel('new'), children: buy ? 'Add buy' : 'Add buyback' })] })) }), _jsx(ListFilters, { searchHint: "Client, PAN, code, scrip or ISIN", platformKey: "platformId", platformLabel: "Vendor", platforms: (platforms.data ?? []).map((p) => ({ value: String(p.id), label: p.name })), payments: BILLING_LABEL }), list.error && _jsx("p", { className: "error", children: list.error }), list.data && list.data.length === 0 && _jsx("p", { className: "muted", children: buy ? 'No buys match.' : 'No buybacks match.' }), list.data && list.data.length > 0 && (_jsxs(_Fragment, { children: [_jsx("div", { className: "table-scroll", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { className: "num", children: "SR.NO" }), view.th('month', 'Month'), view.th('date', buy ? 'Invt date' : 'Transaction date'), view.th('client', 'Client'), view.th('scrip', 'Unlisted'), view.th('vendor', 'Vendor'), view.th('rm', 'RM'), view.th('qty', 'Qty', 'num'), buy ? view.th('vendor_price', 'Base price', 'num') : view.th('client_price', 'Client price', 'num'), buy ? view.th('client_price', 'Price to client', 'num') : view.th('vendor_price', 'Price to us', 'num'), view.th('markup', 'Mark up', 'num'), view.th('markup_pct', 'Mark up %', 'num'), view.th('amount', 'Investment amount', 'num'), view.th('brokerage', 'Gross Brk', 'num'), view.th('gross', 'Without GST', 'num'), view.th('net', 'Net revenue', 'num'), view.th('billing', 'Billing')] }) }), _jsxs("tbody", { children: [view.rows.map((e, i) => (_jsxs("tr", { className: e.status === 'CANCELLED' ? 'muted' : undefined, children: [_jsx("td", { className: "num", children: i + 1 }), _jsx("td", { className: "nowrap", children: formatMonth(e.month) }), _jsx("td", { className: "nowrap", children: formatDate(e.dealDate) }), _jsxs("td", { children: [_jsx("button", { type: "button", className: "cell-link", onClick: () => setPanel(e.id), children: e.clientName }), e.status === 'CANCELLED' && _jsx("span", { className: "badge", children: "cancelled" }), e.partnerName && _jsx("div", { className: "muted", children: e.partnerName })] }), _jsxs("td", { children: [e.scripName, e.isin ? _jsxs("span", { className: "muted mono", children: [" ", e.isin] }) : null] }), _jsx("td", { children: e.vendorName }), _jsx("td", { children: e.rmName }), _jsx("td", { className: "num", children: e.quantity.toLocaleString('en-IN') }), _jsx("td", { className: "num", children: formatMoney(buy ? e.vendorPrice : e.clientPrice) }), _jsx("td", { className: "num", children: formatMoney(buy ? e.clientPrice : e.vendorPrice) }), _jsx("td", { className: "num", children: formatMoney(e.markupPerShare) }), _jsxs("td", { className: "num", children: [e.markupPercent.toFixed(2), "%"] }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.amount }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.brokerage }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.gross }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.net }) }), _jsx("td", { children: PAID.includes(e.billingStatus) ? BILLING_LABEL[e.billingStatus] : _jsx("span", { className: "error", children: BILLING_LABEL[e.billingStatus] }) })] }, e.id))), _jsxs("tr", { className: "total", children: [_jsxs("td", { colSpan: 12, children: ["Total (", active.length, " ", buy ? 'buys' : 'buybacks', ")"] }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('amount') }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('brokerage') }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('gross') }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('net') }) }), _jsx("td", {})] })] })] }) }), view.more] })), panel === 'new' && (_jsx(Modal, { title: buy ? 'Add Unlisted buy' : 'Add Unlisted buyback', onClose: () => setPanel(null), children: _jsx(DealForm, { kind: kind, onDone: done }) })), editing && (_jsx(Modal, { title: `${editing.clientName} — ${KIND_LABEL[editing.kind]} ${editing.scripName}`, onClose: () => setPanel(null), children: canEdit && editing.status === 'ACTIVE' ? (_jsx(DealForm, { kind: editing.kind, entry: editing, onDone: done })) : (_jsxs("p", { children: [KIND_LABEL[editing.kind], " \u00B7 ", editing.scripName, " \u00B7 ", formatMoney(editing.amount), " \u00B7 ", editing.status === 'CANCELLED' ? 'Cancelled' : BILLING_LABEL[editing.billingStatus]] })) }))] }));
}
/** Add or edit one deal, laid out as in the Pre-IPO register. Scrip + vendor + date → prices from the price list. */
function DealForm({ kind, entry, onDone }) {
    const num = (s) => Number(s.replace(/,/g, '')) || 0;
    const str = (n) => (n == null ? '' : String(n));
    const [month, setMonth] = useState(entry ? entry.month : todayIso().slice(0, 7));
    const [rmId, setRmId] = useState(entry ? String(entry.rmId) : '');
    const [lookup, setLookup] = useState('');
    const [looked, setLooked] = useState('');
    const [client, setClient] = useState(null);
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
    const [portal, setPortal] = useState(entry?.portalStatus ?? 'PENDING');
    const [billing, setBilling] = useState(entry?.billingStatus ?? 'TO_CONFIRM');
    const [sheet, setSheet] = useState(entry?.dealSheet ?? 'NA');
    const [remarks, setRemarks] = useState(entry?.remarks ?? '');
    const { busy, error, run } = useSubmit();
    const monthDay = `${month.slice(0, 7)}-01`;
    const employees = useLoad('/api/employees');
    const partners = useLoad('/api/partners');
    const platforms = useLoad('/api/platforms');
    const scrips = useLoad('/api/preipo/scrips');
    const board = useLoad(dealDate ? `/api/preipo/prices?date=${dealDate}` : null);
    const chart = useLoad(`/api/org?date=${monthDay}`);
    const tax = useLoad(`/api/product-taxes/PREIPO/on?date=${monthDay}`);
    const found = useLoad(!entry && looked ? `/api/clients?q=${encodeURIComponent(looked)}&size=20` : null);
    const matches = useMemo(() => (looked && found.data ? matchClients(found.data.items, looked) : null), [found.data, looked]);
    const clientMode = entry ? 'found' : !matches ? 'lookup' : matches.length === 0 ? 'new' : client ? 'found' : 'pick';
    const rmLocked = !entry && !!client?.rmId;
    const shares = useLoad(client ? `/api/shares?clientIds=${client.id}` : null);
    useEffect(() => {
        if (matches && matches.length === 1)
            setClient(matches[0]);
        if (matches && matches.length === 0 && PAN_FORMAT.test(looked))
            setNewPan(looked);
    }, [matches, looked]);
    useEffect(() => {
        if (!client)
            return;
        if (client.rmId)
            setRmId(String(client.rmId));
        const own = shares.data?.[client.id]?.find((s) => !s.branch);
        if (own && !partnerId)
            setPartnerId(String(own.partnerId));
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
        if (pricesTouched || !listed)
            return;
        setVendorPrice(String(listed.basePrice));
        setMarkupPct(String(listed.markupPercent));
        fill(listed.basePrice, 'pct', listed.markupPercent);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [listed, kind, pricesTouched]);
    /**
     * Base price, mark-up per share, mark-up % and client price fill each other: a buy adds the mark-up to the base price,
     * a buyback takes it off. {@code from} is the field just typed; the base price keeps the mark-up % (or the client price).
     */
    function fill(base, from, value) {
        const p = linkedPrices(kind, base, from, value);
        if (!p)
            return;
        if (from !== 'markup')
            setMarkup(String(p.markup));
        if (from !== 'pct')
            setMarkupPct(String(p.markupPercent));
        if (from !== 'client')
            setClientPrice(String(p.clientPrice));
    }
    const typed = (setter, from) => (v) => {
        setPricesTouched(true);
        setter(v);
        if (v !== '')
            fill(num(vendorPrice), from, num(v));
    };
    const typedBase = (v) => {
        setPricesTouched(true);
        setVendorPrice(v);
        if (markupPct !== '')
            fill(num(v), 'pct', num(markupPct));
        else if (clientPrice !== '')
            fill(num(v), 'client', num(clientPrice));
    };
    const findClient = () => {
        setClient(null);
        setLooked(lookup.trim().toUpperCase());
    };
    const place = useMemo(() => {
        for (const r of chart.data?.regions ?? []) {
            for (const b of r.branches)
                if (b.people.some((p) => String(p.id) === rmId))
                    return { region: r.name, rd: r.rd?.name ?? '—', branch: b.name };
        }
        return null;
    }, [chart.data, rmId]);
    const gstPct = entry && entry.month === month.slice(0, 7) ? entry.gstPercent : (tax.data?.gstPercent ?? 18);
    const rev = preIpoRevenue(kind, num(qty), num(clientPrice), num(vendorPrice), gstPct, tdsApplied, num(tdsPct));
    const people = (employees.data ?? []).filter((e) => e.status === 'ACTIVE');
    const sharePartners = (partners.data ?? []).filter((p) => p.type === 'SUB_BROKER' || p.type === 'REFERRAL');
    const scrip = scrips.data?.find((s) => String(s.id) === scripId);
    async function submit(e) {
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
        const ok = await run(() => (entry ? api(`/api/preipo/${entry.id}`, { method: 'PUT', body, ifMatch: entry.updatedAt }) : api('/api/preipo', { method: 'POST', body })));
        if (ok)
            onDone();
    }
    return (_jsxs("form", { onSubmit: submit, className: "fd-form", children: [tax.error && _jsx("p", { className: "error", children: tax.error }), _jsx("p", { className: "muted", style: { marginTop: 0 }, children: kind === 'BUY' ? 'Client buys: our mark-up is added to the vendor base price.' : 'Client sells back: our mark-up is taken off the price the vendor pays.' }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Client" }), entry ? (_jsxs("p", { children: [entry.clientName, " ", _jsx("span", { className: "mono", children: entry.pan }), " \u00B7 Client type: ", entry.clientType] })) : (_jsxs(_Fragment, { children: [_jsxs("label", { children: ["Client PAN or client code", _jsx("input", { value: lookup, onChange: (e) => setLookup(e.target.value.toUpperCase().replace(/\s/g, '')), onKeyDown: (e) => {
                                            if (e.key === 'Enter') {
                                                e.preventDefault();
                                                findClient();
                                            }
                                        }, placeholder: "ABCDE1234F or EM1234" })] }), _jsx("button", { type: "button", className: "secondary field-button", disabled: lookup.trim().length < 3, onClick: findClient, children: "Find client" })] })), clientMode === 'pick' &&
                        (matches ?? []).map((c) => (_jsxs("label", { className: "check", children: [_jsx("input", { type: "radio", name: "preipo-client", checked: client?.id === c.id, onChange: () => setClient(c) }), c.name, " \u00B7 ", _jsx("span", { className: "mono", children: c.pan ?? 'no PAN' }), " \u00B7 ", c.iwellCode ?? 'no code', " \u00B7 ", c.rmName ?? 'no RM'] }, c.id))), !entry && client && (_jsxs("p", { className: "ok", children: [client.name, " \u00B7 ", _jsx("span", { className: "mono", children: client.pan ?? 'no PAN' }), " \u00B7 Client type: ", client.iwellCode ?? 'OFFLINE'] })), clientMode === 'new' && (_jsxs(_Fragment, { children: [_jsx("p", { className: "muted", children: "No client has this PAN or code: enter the new client (client type OFFLINE). They are added to Clients with the RM below." }), _jsxs("label", { children: ["Client name", _jsx("input", { value: newName, onChange: (e) => setNewName(e.target.value), required: true })] }), _jsxs("label", { children: ["PAN", _jsx("input", { value: newPan, onChange: (e) => setNewPan(e.target.value.toUpperCase().replace(/\s/g, '')), maxLength: 10, placeholder: "ABCDE1234F" })] })] })), (clientMode === 'found' || clientMode === 'new') && (_jsxs(_Fragment, { children: [_jsxs("label", { children: ["Mobile", _jsx("input", { value: mobile, onChange: (e) => setMobile(e.target.value), inputMode: "tel" })] }), _jsxs("label", { children: ["Email", _jsx("input", { type: "email", value: email, onChange: (e) => setEmail(e.target.value) })] }), _jsxs("label", { children: ["DP name", _jsx("input", { value: dpName, onChange: (e) => setDpName(e.target.value) })] }), _jsxs("label", { children: ["DP ID", _jsx("input", { value: dpId, onChange: (e) => setDpId(e.target.value) })] }), _jsxs("label", { children: ["Demat client ID", _jsx("input", { value: dematId, onChange: (e) => setDematId(e.target.value) })] })] }))] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Month and RM" }), _jsxs("label", { children: ["Month", _jsx("input", { type: "month", value: month.slice(0, 7), onChange: (e) => e.target.value && setMonth(e.target.value), required: true })] }), rmLocked ? (_jsxs("label", { children: ["RM", _jsx("input", { value: client?.rmName ?? '', readOnly: true })] })) : (_jsxs("label", { children: ["RM", _jsxs("select", { value: rmId, onChange: (e) => setRmId(e.target.value), required: true, children: [_jsx("option", { value: "", children: "Choose\u2026" }), people.map((p) => (_jsx("option", { value: p.id, children: p.branchName ? `${p.name} — ${p.branchName}` : p.name }, p.id)))] })] })), _jsxs("label", { children: ["Referral / sub-broker", _jsxs("select", { value: partnerId, onChange: (e) => setPartnerId(e.target.value), children: [_jsx("option", { value: "", children: "None" }), sharePartners.map((p) => (_jsxs("option", { value: p.id, children: [p.name, " (", p.type === 'SUB_BROKER' ? 'sub-broker' : 'referral', ")"] }, p.id)))] })] }), partnerId && (_jsxs("label", { children: ["Sharing % for this deal", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: sharing, onChange: (e) => setSharing(e.target.value), placeholder: "Blank: the partner's Unlisted %" })] })), _jsxs("p", { className: "muted", children: [place ? `Region: ${place.region} · RD: ${place.rd} · Branch: ${place.branch}` : 'Region and RD come from the RM.', rmLocked && ' · The RM comes from the client: change it on the Clients screen.'] })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Unlisted" }), _jsxs("label", { children: ["Unlisted (scrip)", _jsxs("select", { value: scripId, onChange: (e) => setScripId(e.target.value), required: true, children: [_jsx("option", { value: "", children: "Choose\u2026" }), (scrips.data ?? []).filter((s) => s.active || String(s.id) === scripId).map((s) => (_jsxs("option", { value: s.id, children: [s.name, s.isin ? ` (${s.isin})` : ''] }, s.id)))] })] }), _jsxs("label", { children: ["Vendor (partner)", _jsxs("select", { value: vendorId, onChange: (e) => setVendorId(e.target.value), required: true, children: [_jsx("option", { value: "", children: "Choose\u2026" }), (platforms.data ?? []).map((p) => (_jsx("option", { value: p.id, children: p.name }, p.id)))] })] }), _jsxs("label", { children: [kind === 'BUY' ? 'Investment date' : 'Transaction date', _jsx("input", { type: "date", value: dealDate, onChange: (e) => setDealDate(e.target.value), required: true })] }), _jsxs("label", { children: ["Quantity", _jsx("input", { type: "number", min: 0, step: "any", value: qty, onChange: (e) => setQty(e.target.value), required: true })] }), _jsxs("label", { children: [kind === 'BUY' ? 'Base price (₹)' : 'Price to us (₹)', _jsx("input", { type: "number", min: 0, step: "any", value: vendorPrice, onChange: (e) => typedBase(e.target.value), required: true })] }), _jsxs("label", { children: ["Mark up (\u20B9 per share)", _jsx("input", { type: "number", min: 0, step: "any", value: markup, onChange: (e) => typed(setMarkup, 'markup')(e.target.value) })] }), _jsxs("label", { children: ["Mark up %", _jsx("input", { type: "number", min: 0, max: 100, step: "any", value: markupPct, onChange: (e) => typed(setMarkupPct, 'pct')(e.target.value) })] }), _jsxs("label", { children: [kind === 'BUY' ? 'Price offered to client (₹)' : 'Client price (₹)', _jsx("input", { type: "number", min: 0, step: "any", value: clientPrice, onChange: (e) => typed(setClientPrice, 'client')(e.target.value), required: true })] }), _jsxs("p", { className: "muted", children: [scrip?.lotSize ? `Minimum lot ${scrip.lotSize}. ` : '', listed
                                ? `Price list on ${formatDate(listed.asOf)}: base ${formatMoney(listed.basePrice)}, client ${formatMoney(listed.clientPrice)} (${listed.markupPercent}% mark-up).`
                                : scripId && vendorId ? 'This vendor has no price for this scrip on the deal date: type the prices.' : 'Choose the scrip and vendor to fill the prices from Available IPOs.'] })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Brokerage" }), _jsxs("label", { children: ["TDS", _jsxs("select", { value: tdsApplied ? 'YES' : 'NO', onChange: (e) => setTdsApplied(e.target.value === 'YES'), children: [_jsx("option", { value: "YES", children: "Deducted by the vendor" }), _jsx("option", { value: "NO", children: "Not deducted" })] })] }), _jsxs("label", { children: ["TDS % (on Gross Brk)", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: tdsApplied ? tdsPct : '0', disabled: !tdsApplied, onChange: (e) => setTdsPct(e.target.value), required: true })] }), _jsxs("label", { children: ["GST %", _jsx("input", { value: gstPct, readOnly: true })] }), rev.markup < 0 && (_jsx("p", { className: "error", children: kind === 'BUY' ? 'The client price is below the vendor base price.' : 'The client price is above what the vendor pays.' })), _jsx("div", { className: "figures", children: [
                            ['Mark-up per share', rev.markup],
                            ['Investment amount', rev.amount],
                            ['Gross Brk (incl. GST)', rev.brokerage],
                            ['Without GST', rev.gross],
                            [`GST (${gstPct}%)`, rev.gst],
                            ['TDS', rev.tds],
                            ['Invoice amount', rev.invoice],
                            ['Net revenue', rev.net],
                        ].map(([label, value]) => (_jsxs("div", { className: label === 'Net revenue' ? 'figure key' : 'figure', children: [_jsx("span", { children: label }), _jsx("b", { children: formatMoney(value) })] }, label))) })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Follow-up" }), _jsxs("label", { children: ["Epsilon portal", _jsx("select", { value: portal, onChange: (e) => setPortal(e.target.value), children: Object.entries(PORTAL_LABEL).map(([k, v]) => (_jsx("option", { value: k, children: v }, k))) })] }), _jsxs("label", { children: ["Billing", _jsx("select", { value: billing, onChange: (e) => setBilling(e.target.value), children: Object.entries(BILLING_LABEL).map(([k, v]) => (_jsx("option", { value: k, children: v }, k))) })] }), _jsxs("label", { children: ["Deal sheet", _jsx("select", { value: sheet, onChange: (e) => setSheet(e.target.value), children: Object.entries(SHEET_LABEL).map(([k, v]) => (_jsx("option", { value: k, children: v }, k))) })] }), _jsxs("label", { children: ["Remarks", _jsx("textarea", { value: remarks, onChange: (e) => setRemarks(e.target.value), rows: 2 })] })] }), error && _jsx("p", { className: "error", children: error }), _jsxs("span", { className: "inline-form", children: [_jsx("button", { type: "submit", disabled: busy || clientMode === 'lookup' || clientMode === 'pick' || rev.markup < 0, children: entry ? 'Save changes' : kind === 'BUY' ? 'Add buy' : 'Add buyback' }), entry && _jsx(CancelDeal, { id: entry.id, onDone: onDone })] })] }));
}
function CancelDeal({ id, onDone }) {
    const { busy, run } = useSubmit();
    const [asking, setAsking] = useState(false);
    const [reason, setReason] = useState('');
    if (!asking) {
        return (_jsx("button", { type: "button", className: "link", disabled: busy, onClick: () => setAsking(true), children: "Cancel this deal" }));
    }
    return (_jsxs("span", { className: "inline-form", children: [_jsxs("label", { children: ["Why cancel?", _jsx("input", { value: reason, onChange: (e) => setReason(e.target.value), autoFocus: true })] }), _jsx("button", { type: "button", className: "secondary", disabled: busy, onClick: async () => {
                    if (await run(() => api(`/api/preipo/${id}/cancel`, { method: 'POST', body: { reason } })))
                        onDone();
                }, children: "Cancel deal" })] }));
}
/** The price list: every scrip with each vendor's latest base price and client price on a day. */
function AvailableIpos() {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const [date, setDate] = useState(todayIso());
    const board = useLoad(`/api/preipo/prices?date=${date}`);
    const [open, setOpen] = useState(null);
    const rows = board.data ?? [];
    const view = useTableView(rows, {
        scrip: (r) => r.scrip.name, isin: (r) => r.scrip.isin, lot: (r) => r.scrip.lotSize,
        base: (r) => r.prices[0]?.basePrice ?? null, client: (r) => r.prices[0]?.clientPrice ?? null, vendors: (r) => r.prices.length,
    });
    const done = () => {
        setOpen(null);
        board.reload();
    };
    return (_jsxs(_Fragment, { children: [_jsxs("div", { className: "page-head", style: { marginBottom: 12 }, children: [_jsxs("label", { className: "inline", children: ["Prices on", _jsx("input", { type: "date", value: date, onChange: (e) => e.target.value && setDate(e.target.value) })] }), canEdit && (_jsxs("div", { className: "actions", children: [_jsx("button", { type: "button", className: "secondary", onClick: () => setOpen('new'), children: "Add scrip" }), _jsx("button", { type: "button", onClick: () => setOpen('upload'), children: "Upload price sheet" })] }))] }), _jsx("p", { className: "muted", children: "Each vendor's latest price on or before the date. Lowest base price first; click a scrip for its price history or to set a price." }), board.error && _jsx("p", { className: "error", children: board.error }), board.data && rows.length === 0 && _jsx("div", { className: "empty", children: "No scrips yet. Upload the Unlisted (Pre-IPO) price sheet or add a scrip." }), rows.length > 0 && (_jsxs(_Fragment, { children: [_jsx("div", { className: "table-scroll", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [view.th('scrip', 'Scrip'), view.th('isin', 'ISIN'), view.th('lot', 'Min. lot', 'num'), view.th('base', 'Best base price', 'num'), view.th('client', 'Client price', 'num'), view.th('vendors', 'All vendors')] }) }), _jsx("tbody", { children: view.rows.map((r) => {
                                        const best = r.prices[0];
                                        return (_jsxs("tr", { children: [_jsx("td", { children: _jsx("button", { type: "button", className: "cell-link", onClick: () => setOpen(r.scrip), children: r.scrip.name }) }), _jsx("td", { className: "mono", children: r.scrip.isin ?? '—' }), _jsx("td", { className: "num", children: r.scrip.lotSize?.toLocaleString('en-IN') ?? '—' }), _jsx("td", { className: "num", children: best ? _jsxs(_Fragment, { children: [formatMoney(best.basePrice), " ", _jsx("span", { className: "muted", children: best.vendorName })] }) : _jsx("span", { className: "muted", children: "no price" }) }), _jsx("td", { className: "num", children: best ? formatMoney(best.clientPrice) : '' }), _jsx("td", { children: r.prices.map((p) => (_jsxs("span", { className: "badge", title: `as on ${formatDate(p.asOf)}`, style: { marginRight: 4 }, children: [p.vendorName, " ", formatMoney(p.basePrice)] }, p.vendorId))) })] }, r.scrip.id));
                                    }) })] }) }), view.more] })), open === 'upload' && (_jsx(Modal, { title: "Upload price sheet", onClose: () => setOpen(null), children: _jsx(UploadPrices, { onDone: done }) })), open === 'new' && (_jsx(Modal, { title: "Add scrip", onClose: () => setOpen(null), children: _jsx(ScripForm, { onDone: done }) })), open && typeof open === 'object' && (_jsx(Modal, { title: open.name, onClose: () => setOpen(null), children: _jsx(ScripPanel, { scrip: open, canEdit: canEdit, onDone: done }) }))] }));
}
function UploadPrices({ onDone }) {
    const [file, setFile] = useState(null);
    const [asOf, setAsOf] = useState(todayIso());
    const [markup, setMarkup] = useState('4');
    const [result, setResult] = useState(null);
    const { busy, error, run } = useSubmit();
    async function submit(e) {
        e.preventDefault();
        if (!file)
            return;
        const form = new FormData();
        form.append('file', file);
        form.append('asOf', asOf);
        form.append('markupPercent', markup || '4');
        await run(async () => setResult(await api('/api/preipo/prices/upload', { method: 'POST', body: form })));
    }
    if (result) {
        return (_jsxs("div", { children: [_jsxs("p", { className: "ok", children: [result.prices, " prices for ", result.scrips, " scrips saved as on ", formatDate(asOf), result.newScrips ? `; ${result.newScrips} new scrips added` : '', "."] }), result.newVendors.length > 0 && _jsxs("p", { className: "muted", children: ["New vendors added to Platforms: ", result.newVendors.join(', '), "."] }), result.withoutPrice > 0 && _jsxs("p", { className: "muted", children: [result.withoutPrice, " scrips had no vendor price that day: they are listed without a price."] }), result.skippedRows > 0 && _jsxs("p", { className: "muted", children: [result.skippedRows, " rows were notes or other lists (no ISIN, no price) and were skipped."] }), _jsx("button", { type: "button", onClick: onDone, children: "Done" })] }));
    }
    return (_jsxs("form", { onSubmit: submit, className: "fd-form", children: [_jsx("p", { className: "muted", children: "The Pre-IPO (Unlisted) master file's \"Purchase Price Availabity\" sheet: ISIN, scrip name, face value, lot size, and one \"Base Price (INR)\" column per vendor with the vendor's name above it." }), _jsxs("label", { children: ["File", _jsx("input", { type: "file", accept: ".xlsx", onChange: (e) => setFile(e.target.files?.[0] ?? null), required: true })] }), _jsxs("label", { children: ["Prices as on", _jsx("input", { type: "date", value: asOf, onChange: (e) => setAsOf(e.target.value), required: true })] }), _jsxs("label", { children: ["Mark-up % for client price", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: markup, onChange: (e) => setMarkup(e.target.value) })] }), error && _jsx("p", { className: "error", children: error }), _jsx("button", { type: "submit", disabled: busy || !file, children: "Upload" })] }));
}
function ScripForm({ scrip, onDone }) {
    const [name, setName] = useState(scrip?.name ?? '');
    const [isin, setIsin] = useState(scrip?.isin ?? '');
    const [face, setFace] = useState(scrip?.faceValue != null ? String(scrip.faceValue) : '');
    const [lot, setLot] = useState(scrip?.lotSize != null ? String(scrip.lotSize) : '');
    const [active, setActive] = useState(scrip?.active ?? true);
    const { busy, error, run } = useSubmit();
    async function submit(e) {
        e.preventDefault();
        const body = { name, isin, faceValue: face ? Number(face) : null, lotSize: lot ? Number(lot) : null, active };
        if (await run(() => (scrip ? api(`/api/preipo/scrips/${scrip.id}`, { method: 'PUT', body }) : api('/api/preipo/scrips', { method: 'POST', body }))))
            onDone();
    }
    return (_jsxs("form", { onSubmit: submit, className: "fd-form", children: [_jsxs("label", { children: ["Scrip name", _jsx("input", { value: name, onChange: (e) => setName(e.target.value), required: true })] }), _jsxs("label", { children: ["ISIN", _jsx("input", { value: isin, onChange: (e) => setIsin(e.target.value.toUpperCase()) })] }), _jsxs("label", { children: ["Face value", _jsx("input", { type: "number", min: 0, step: "any", value: face, onChange: (e) => setFace(e.target.value) })] }), _jsxs("label", { children: ["Minimum lot", _jsx("input", { type: "number", min: 1, step: 1, value: lot, onChange: (e) => setLot(e.target.value) })] }), scrip && (_jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: active, onChange: (e) => setActive(e.target.checked) }), "Still offered (unticked: hidden from the price list)"] })), error && _jsx("p", { className: "error", children: error }), _jsx("button", { type: "submit", disabled: busy, children: scrip ? 'Save scrip' : 'Add scrip' })] }));
}
/** One scrip: its details, every price it has had, and a form to set a vendor's price from a date. */
function ScripPanel({ scrip, canEdit, onDone }) {
    const history = useLoad(`/api/preipo/scrips/${scrip.id}/prices`);
    const platforms = useLoad('/api/platforms');
    const [vendorId, setVendorId] = useState('');
    const [asOf, setAsOf] = useState(todayIso());
    const [base, setBase] = useState('');
    const [markup, setMarkup] = useState('4');
    const [editing, setEditing] = useState(false);
    const { busy, error, run } = useSubmit();
    async function submit(e) {
        e.preventDefault();
        const ok = await run(() => api('/api/preipo/prices', { method: 'POST', body: { scripId: scrip.id, vendorId: Number(vendorId), asOf, basePrice: Number(base), markupPercent: Number(markup) } }));
        if (ok) {
            setBase('');
            history.reload();
        }
    }
    if (editing)
        return _jsx(ScripForm, { scrip: scrip, onDone: onDone });
    return (_jsxs("div", { className: "fd-form", children: [_jsxs("p", { children: [scrip.isin ? _jsx("span", { className: "mono", children: scrip.isin }) : 'No ISIN', " \u00B7 Face value ", scrip.faceValue ?? '—', " \u00B7 Minimum lot ", scrip.lotSize ?? '—', canEdit && (_jsxs(_Fragment, { children: [' ', _jsx("button", { type: "button", className: "link", onClick: () => setEditing(true), children: "Edit scrip" })] }))] }), _jsx("h3", { children: "Price history" }), history.data && history.data.length === 0 && _jsx("p", { className: "muted", children: "No prices yet." }), history.data && history.data.length > 0 && (_jsxs("table", { className: "compact", children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "As on" }), _jsx("th", { children: "Vendor" }), _jsx("th", { className: "num", children: "Base" }), _jsx("th", { className: "num", children: "Mark-up %" }), _jsx("th", { className: "num", children: "Client price" }), _jsx("th", { children: "By" })] }) }), _jsx("tbody", { children: history.data.map((p) => (_jsxs("tr", { children: [_jsx("td", { children: formatDate(p.asOf) }), _jsx("td", { children: p.vendorName }), _jsx("td", { className: "num", children: formatMoney(p.basePrice) }), _jsx("td", { className: "num", children: p.markupPercent }), _jsx("td", { className: "num", children: formatMoney(p.clientPrice) }), _jsx("td", { className: "muted", children: p.changedBy })] }, p.id))) })] })), canEdit && (_jsxs("form", { onSubmit: submit, className: "fd-form", children: [_jsx("h3", { children: "Set a price" }), _jsxs("label", { children: ["Vendor", _jsxs("select", { value: vendorId, onChange: (e) => setVendorId(e.target.value), required: true, children: [_jsx("option", { value: "", children: "Choose\u2026" }), (platforms.data ?? []).map((p) => (_jsx("option", { value: p.id, children: p.name }, p.id)))] })] }), _jsxs("label", { children: ["From", _jsx("input", { type: "date", value: asOf, onChange: (e) => setAsOf(e.target.value), required: true })] }), _jsxs("label", { children: ["Base price (\u20B9)", _jsx("input", { type: "number", min: 0, step: "any", value: base, onChange: (e) => setBase(e.target.value), required: true })] }), _jsxs("label", { children: ["Mark-up %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: markup, onChange: (e) => setMarkup(e.target.value) })] }), _jsxs("p", { className: "muted", children: ["Client price: ", base ? formatMoney(round2((Number(base) * (100 + Number(markup || 0))) / 100)) : '—'] }), error && _jsx("p", { className: "error", children: error }), _jsx("button", { type: "submit", disabled: busy, children: "Save price" })] }))] }));
}
