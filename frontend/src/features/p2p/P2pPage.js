import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { CancelEntry, ClientFieldset, MonthRmFieldset, useClientAndRm } from '../entry/ClientAndRm';
import { useEffect, useState } from 'react';
import { useTableView } from '../../components/useTableView';
import { api } from '../../api/client';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { Amount } from '../../components/Amount';
import { ListFilters, listPath, useFilterQuery } from '../../components/ListFilters';
import { Modal } from '../../components/Modal';
import { useEntryPanel } from '../../components/useEntryPanel';
import { formatDate, formatMoney, todayIso, formatMonth, indianDigits } from '../../format';
const round2 = (n) => Math.round((n + Number.EPSILON) * 100) / 100;
/** The brokerage figures as the server works them out (R-87). */
export function p2pFigures(brokerage, tdsApplied, tdsPct, gstPct) {
    const gross = round2(brokerage);
    const tds = tdsApplied ? round2((gross * tdsPct) / 100) : 0;
    const gst = round2((gross * gstPct) / 100);
    return { gross, tds, gst, net: round2(gross - tds), invoice: round2(gross + gst) };
}
const thisMonth = () => todayIso().slice(0, 7);
const PAYMENTS = { PENDING: 'Brokerage not received', RECEIVED: 'Received' };
/** P2P: one row per investment, as in the P2P register; brokerage typed when known (R-87). */
export function P2pPage({ embedded = false } = {}) {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const query = useFilterQuery();
    const list = useLoad(listPath('/api/p2p', query));
    const suggestions = useLoad('/api/p2p/suggestions');
    const [panel, setPanel] = useEntryPanel();
    const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
    const active = (list.data ?? []).filter((e) => e.status === 'ACTIVE');
    const done = () => {
        setPanel(null);
        list.reload();
        suggestions.reload();
    };
    const view = useTableView(list.data, { month: (e) => e.month, region: (e) => e.region, client: (e) => e.clientName, rm: (e) => e.rmName, partner_platform: (e) => e.partnerName, scheme: (e) => e.scheme, roi: (e) => e.roiPercent, amount_invested: (e) => e.amount, start_date: (e) => e.startDate, brokerage: (e) => e.brokerage });
    return (_jsxs("section", { children: [_jsxs("div", { className: "page-head", style: embedded ? { justifyContent: 'flex-end', marginBottom: 12 } : undefined, children: [!embedded && _jsx("h1", { children: "P2P" }), canEdit && (_jsxs("div", { className: "actions", children: [_jsx("a", { className: "button-link secondary", href: `/api/p2p/export.xlsx${query ?? ''}`, download: true, children: "Download Excel" }), _jsx("button", { type: "button", onClick: () => setPanel('new'), children: "Add P2P investment" })] }))] }), _jsx("p", { className: "muted", children: "An entry with brokerage counts in its month: the END shows it in the P2P column, shared at the partner's P2P %." }), _jsx(ListFilters, { searchHint: "Client, PAN, code or platform", platformKey: "platform", platformLabel: "Platform", platforms: (suggestions.data?.platforms ?? []).map((p) => ({ value: p, label: p })), payments: PAYMENTS }), list.error && _jsx("p", { className: "error", children: list.error }), list.data && list.data.length === 0 && _jsx("p", { className: "muted", children: "No P2P investments match." }), list.data && list.data.length > 0 && (_jsxs(_Fragment, { children: [_jsx("div", { className: "table-scroll", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { className: "num", children: "SR.NO" }), view.th('month', 'Month'), view.th('region', 'Region'), view.th('client', 'Client'), view.th('rm', 'RM'), view.th('partner_platform', 'Partner (platform)'), view.th('scheme', 'Scheme'), view.th('roi', 'ROI %', 'num'), view.th('amount_invested', 'Amount invested', 'num'), view.th('start_date', 'Start date'), _jsx("th", { children: "Payment status" }), view.th('brokerage', 'Brokerage', 'num')] }) }), _jsxs("tbody", { children: [view.rows.map((e, i) => (_jsxs("tr", { className: e.status === 'CANCELLED' ? 'muted' : undefined, children: [_jsx("td", { className: "num", children: i + 1 }), _jsx("td", { children: formatMonth(e.month) }), _jsx("td", { children: e.region ?? _jsx("span", { className: "muted", children: "\u2014" }) }), _jsxs("td", { children: [_jsx("button", { type: "button", className: "cell-link", onClick: () => setPanel(e.id), children: e.clientName }), e.status === 'CANCELLED' && _jsx("span", { className: "badge", children: "cancelled" }), e.partnerName && _jsx("div", { className: "muted", children: e.partnerName })] }), _jsx("td", { children: e.rmName }), _jsx("td", { children: e.platform }), _jsx("td", { children: e.scheme }), _jsx("td", { className: "num", children: e.roiPercent ?? '' }), _jsx("td", { className: "num", children: e.amount == null ? _jsx("span", { className: "muted", children: "\u2014" }) : _jsx(Amount, { value: e.amount }) }), _jsx("td", { children: e.startDate ? formatDate(e.startDate) : '' }), _jsx("td", { children: e.receivedOn ? _jsxs("span", { className: "badge ok", children: ["Received ", formatDate(e.receivedOn)] }) : (e.paymentNote ?? '') }), _jsx("td", { className: "num", children: e.brokerage == null ? _jsx("span", { className: "muted", children: "not known" }) : formatMoney(e.gross) })] }, e.id))), _jsxs("tr", { className: "total", children: [_jsxs("td", { colSpan: 8, children: ["Total (", active.length, " investments)"] }), _jsx("td", { className: "num", children: _jsx(Amount, { value: active.reduce((s, e) => s + (e.amount ?? 0), 0) }) }), _jsx("td", { colSpan: 2 }), _jsx("td", { className: "num", children: _jsx(Amount, { value: active.reduce((s, e) => s + e.gross, 0) }) })] })] })] }) }), view.more] })), panel === 'new' && (_jsx(Modal, { title: "Add P2P investment", onClose: () => setPanel(null), children: _jsx(P2pForm, { month: thisMonth(), onDone: done }) })), editing && (_jsx(Modal, { title: `${editing.clientName} — ${editing.platform}`, onClose: () => setPanel(null), children: canEdit && editing.status === 'ACTIVE' ? (_jsx(P2pForm, { month: thisMonth(), entry: editing, onDone: done })) : (_jsxs("dl", { className: "facts", children: [_jsx("dt", { children: "Platform" }), _jsxs("dd", { children: [editing.platform, " \u00B7 ", editing.scheme] }), _jsx("dt", { children: "Amount" }), _jsx("dd", { children: editing.amount == null ? '—' : formatMoney(editing.amount) })] })) }))] }));
}
/** Add or edit one P2P investment, in the register's order, with a Brokerage section. */
function P2pForm({ month: listMonth, entry, onDone }) {
    const party = useClientAndRm(entry, entry ? entry.month : listMonth);
    const { month, monthDay, clientMode } = party;
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
    const suggestions = useLoad('/api/p2p/suggestions');
    const tax = useLoad(`/api/product-taxes/P2P/on?date=${monthDay}`);
    useEffect(() => {
        if (!entry && tax.data) {
            setTdsApplied(tax.data.tdsByDefault);
            setTdsPct(String(tax.data.tdsPercent));
        }
    }, [entry, tax.data]);
    const num = (s) => Number(s.replace(/,/g, '')) || 0;
    const gstPct = entry && entry.month === month.slice(0, 7) ? entry.gstPercent : (tax.data?.gstPercent ?? 0);
    const fig = p2pFigures(num(brokerage), tdsApplied, num(tdsPct), gstPct);
    async function submit(e) {
        e.preventDefault();
        const body = {
            ...party.body(),
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
        const ok = await run(() => (entry ? api(`/api/p2p/${entry.id}`, { method: 'PUT', body, ifMatch: entry.updatedAt }) : api('/api/p2p', { method: 'POST', body })));
        if (ok)
            onDone();
    }
    return (_jsxs("form", { onSubmit: submit, className: "fd-form", children: [tax.error && _jsx("p", { className: "error", children: tax.error }), _jsx(ClientFieldset, { party: party, radioName: "p2p-client" }), _jsx(MonthRmFieldset, { party: party }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Investment" }), _jsxs("label", { children: ["Partner (platform)", _jsx("input", { value: platform, onChange: (e) => setPlatform(e.target.value), list: "p2p-platforms", placeholder: "e.g. INDIA MONEY MART", required: true })] }), _jsx("datalist", { id: "p2p-platforms", children: (suggestions.data?.platforms ?? []).map((n) => (_jsx("option", { value: n }, n))) }), _jsxs("label", { children: ["P2P scheme", _jsx("input", { value: scheme, onChange: (e) => setScheme(e.target.value), list: "p2p-schemes", placeholder: "e.g. YEARLY", required: true })] }), _jsx("datalist", { id: "p2p-schemes", children: ['YEARLY', 'QUARTERLY', 'MONTHLY', ...(suggestions.data?.schemes ?? [])]
                            .filter((v, i, a) => a.findIndex((x) => x.toLowerCase() === v.toLowerCase()) === i)
                            .map((n) => (_jsx("option", { value: n }, n))) }), _jsxs("label", { children: ["ROI %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: roi, onChange: (e) => setRoi(e.target.value), placeholder: "e.g. 13" })] }), _jsxs("label", { children: ["Amount invested (\u20B9)", _jsx("input", { value: amount, onChange: (e) => setAmount(indianDigits(e.target.value)), inputMode: "decimal" })] }), _jsxs("label", { children: ["Funds initiated date", _jsx("input", { type: "date", value: fundsOn, onChange: (e) => setFundsOn(e.target.value) })] }), _jsxs("label", { children: ["Start date", _jsx("input", { type: "date", value: startDate, onChange: (e) => setStartDate(e.target.value) })] }), _jsxs("label", { children: ["Quarterly end date", _jsx("input", { type: "date", value: quarterEnd, onChange: (e) => setQuarterEnd(e.target.value) })] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: onPortal, onChange: (e) => setOnPortal(e.target.checked) }), "Entry status: updated on app"] })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Brokerage" }), _jsx("p", { className: "muted", style: { marginTop: 0 }, children: "The P2P register has no revenue figures: type the brokerage (excl. GST) when it is known." }), _jsxs("label", { children: ["Brokerage (\u20B9, excl. GST)", _jsx("input", { type: "number", min: 0, step: "0.01", value: brokerage, onChange: (e) => setBrokerage(e.target.value), placeholder: "not known yet" })] }), _jsxs("span", { className: "inline-form", children: [_jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: tdsApplied, onChange: (e) => setTdsApplied(e.target.checked) }), "TDS"] }), tdsApplied && (_jsxs("label", { children: ["TDS %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: tdsPct, onChange: (e) => setTdsPct(e.target.value), "aria-label": "TDS %", required: true })] }))] }), _jsxs("dl", { className: "facts", children: [_jsx("dt", { children: "TDS" }), _jsx("dd", { children: formatMoney(fig.tds) }), _jsxs("dt", { children: ["GST (", gstPct, "%)"] }), _jsx("dd", { children: formatMoney(fig.gst) }), _jsx("dt", { children: "Net revenue" }), _jsx("dd", { children: _jsx("strong", { children: formatMoney(fig.net) }) }), _jsx("dt", { children: "Invoice amount" }), _jsx("dd", { children: formatMoney(fig.invoice) })] }), _jsxs("label", { children: ["Payment status (as in the register)", _jsx("input", { value: paymentNote, onChange: (e) => setPaymentNote(e.target.value), placeholder: "e.g. BILL RAISED -13 OCT" })] }), _jsxs("label", { children: ["Amt received date", _jsx("input", { type: "date", value: receivedOn, onChange: (e) => setReceivedOn(e.target.value) })] }), _jsxs("label", { children: ["Rev sharing status", _jsx("input", { value: revSharing, onChange: (e) => setRevSharing(e.target.value) })] }), _jsxs("label", { children: ["Remarks", _jsx("textarea", { value: remarks, onChange: (e) => setRemarks(e.target.value), rows: 2 })] })] }), error && _jsx("p", { className: "error", children: error }), _jsxs("span", { className: "inline-form", children: [_jsx("button", { type: "submit", disabled: busy || clientMode === 'lookup' || clientMode === 'pick', children: entry ? 'Save changes' : 'Add P2P investment' }), entry && _jsx(CancelEntry, { url: `/api/p2p/${entry.id}/cancel`, question: "Why cancel this P2P entry?", onDone: onDone })] })] }));
}
