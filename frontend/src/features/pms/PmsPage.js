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
/** An AMC payout's figures as the server works them out (R-85). */
export function pmsPayoutFigures(amount, tdsApplied, tdsPct, gstPct) {
    const a = round2(amount);
    const tds = tdsApplied ? round2((a * tdsPct) / 100) : 0;
    const gst = round2((a * gstPct) / 100);
    return { tds, gst, net: round2(a - tds), invoice: round2(a + gst) };
}
const FREQUENCY = { MONTHLY: 'Monthly', QUARTERLY: 'Quarterly', NONE: 'No payout' };
const CERTIFICATE = { PENDING: 'Pending', RECEIVED: 'Received', NOT_APPLICABLE: 'Not applicable' };
const thisMonth = () => todayIso().slice(0, 7);
/** PMS (R-85): client investments, the AMC payouts per invoice period, and each client's revenue per month (the END's PMS). */
export function PmsPage({ embedded = false } = {}) {
    const [view, setView] = useState('investments');
    return (_jsxs("section", { children: [!embedded && _jsx("h1", { children: "PMS" }), _jsxs("div", { className: "segmented", role: "group", "aria-label": "PMS view", children: [_jsx("button", { type: "button", "aria-pressed": view === 'investments', className: view === 'investments' ? 'on' : undefined, onClick: () => setView('investments'), children: "Investments" }), _jsx("button", { type: "button", "aria-pressed": view === 'payouts', className: view === 'payouts' ? 'on' : undefined, onClick: () => setView('payouts'), children: "AMC payouts" }), _jsx("button", { type: "button", "aria-pressed": view === 'revenue', className: view === 'revenue' ? 'on' : undefined, onClick: () => setView('revenue'), children: "Monthly revenue (END)" })] }), _jsx("p", { className: "muted", children: "Each client's PMS revenue is typed for the month the AMC's payout is received: that month's END shows it in the PMS column, shared at the partner's PMS %." }), view === 'investments' ? _jsx(Investments, {}) : view === 'payouts' ? _jsx(Payouts, {}) : _jsx(Revenue, {})] }));
}
function Investments() {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const query = useFilterQuery();
    const list = useLoad(listPath('/api/pms', query));
    const suggestions = useLoad('/api/pms/suggestions');
    const [panel, setPanel] = useEntryPanel();
    const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
    const active = (list.data ?? []).filter((e) => e.status === 'ACTIVE' && e.holdingStatus === 'ACTIVE');
    const done = () => {
        setPanel(null);
        list.reload();
        suggestions.reload();
    };
    const view = useTableView(list.data, { month: (e) => e.month, region: (e) => e.region, client: (e) => e.clientName, rm: (e) => e.rmName, partner_amc: (e) => e.partnerName, pms: (e) => e.strategy, inv_amt: (e) => e.amount, investment_date: (e) => e.investmentDate, trail_yr: (e) => e.trailPercent, status: (e) => e.holdingStatus });
    return (_jsxs(_Fragment, { children: [canEdit && (_jsxs("div", { className: "actions", style: { justifyContent: 'flex-end', marginBottom: 12 }, children: [_jsx("a", { className: "button-link secondary", href: `/api/pms/export.xlsx${query ?? ''}`, download: true, children: "Download Excel" }), _jsx("button", { type: "button", onClick: () => setPanel('new'), children: "Add PMS investment" })] })), _jsx(ListFilters, { searchHint: "Client, PAN, code, AMC or strategy", platformKey: "amc", platformLabel: "AMC", platforms: (suggestions.data?.amcs ?? []).map((p) => ({ value: p, label: p })), entryFilters: false }), list.error && _jsx("p", { className: "error", children: list.error }), list.data && list.data.length === 0 && _jsx("p", { className: "muted", children: "No PMS investments match." }), list.data && list.data.length > 0 && (_jsxs(_Fragment, { children: [_jsx("div", { className: "table-scroll", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { className: "num", children: "SR.NO" }), view.th('month', 'Month'), view.th('region', 'Region'), view.th('client', 'Client'), view.th('rm', 'RM'), view.th('partner_amc', 'Partner (AMC)'), view.th('pms', 'PMS'), view.th('inv_amt', 'Inv amt', 'num'), view.th('investment_date', 'Investment date'), view.th('trail_yr', 'Trail % / yr', 'num'), _jsx("th", { children: "Payout" }), view.th('status', 'Status')] }) }), _jsxs("tbody", { children: [view.rows.map((e, i) => (_jsxs("tr", { className: e.status === 'CANCELLED' ? 'muted' : undefined, children: [_jsx("td", { className: "num", children: i + 1 }), _jsx("td", { children: formatMonth(e.month) }), _jsx("td", { children: e.region ?? _jsx("span", { className: "muted", children: "\u2014" }) }), _jsxs("td", { children: [_jsx("button", { type: "button", className: "cell-link", onClick: () => setPanel(e.id), children: e.clientName }), e.status === 'CANCELLED' && _jsx("span", { className: "badge", children: "cancelled" }), e.partnerName && _jsx("div", { className: "muted", children: e.partnerName })] }), _jsx("td", { children: e.rmName }), _jsx("td", { children: e.amc }), _jsx("td", { children: e.strategy }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.amount }) }), _jsx("td", { children: formatDate(e.investmentDate) }), _jsx("td", { className: "num", children: e.trailPercent ?? '' }), _jsx("td", { children: FREQUENCY[e.payoutFrequency] }), _jsx("td", { children: e.holdingStatus === 'REDEEMED' ? (_jsxs("span", { className: "badge", children: ["Redeemed ", e.redeemedOn ? formatDate(e.redeemedOn) : ''] })) : (_jsx("span", { className: "badge ok", children: "Active" })) })] }, e.id))), _jsxs("tr", { className: "total", children: [_jsxs("td", { colSpan: 7, children: ["Active investments (", active.length, ")"] }), _jsx("td", { className: "num", children: _jsx(Amount, { value: active.reduce((s, e) => s + e.amount, 0) }) }), _jsx("td", { colSpan: 4 })] })] })] }) }), view.more] })), panel === 'new' && (_jsx(Modal, { title: "Add PMS investment", onClose: () => setPanel(null), children: _jsx(InvestmentForm, { month: thisMonth(), onDone: done }) })), editing && (_jsx(Modal, { title: `${editing.clientName} — ${editing.strategy}`, onClose: () => setPanel(null), children: canEdit && editing.status === 'ACTIVE' ? (_jsx(InvestmentForm, { month: thisMonth(), entry: editing, onDone: done })) : (_jsxs("dl", { className: "facts", children: [_jsx("dt", { children: "PMS" }), _jsxs("dd", { children: [editing.amc, " \u00B7 ", editing.strategy] }), _jsx("dt", { children: "Amount" }), _jsx("dd", { children: formatMoney(editing.amount) })] })) }))] }));
}
/** Add or edit one PMS investment, in the register's order. */
function InvestmentForm({ month: listMonth, entry, onDone }) {
    const party = useClientAndRm(entry, entry ? entry.month : listMonth);
    const { clientMode } = party;
    const [amc, setAmc] = useState(entry?.amc ?? '');
    const [strategy, setStrategy] = useState(entry?.strategy ?? '');
    const [amount, setAmount] = useState(entry ? indianDigits(String(entry.amount)) : '');
    const [investmentDate, setInvestmentDate] = useState(entry?.investmentDate ?? '');
    const [onPortal, setOnPortal] = useState(entry?.onPortal ?? false);
    const [trail, setTrail] = useState(entry?.trailPercent != null ? String(entry.trailPercent) : '');
    const [frequency, setFrequency] = useState(entry?.payoutFrequency ?? 'QUARTERLY');
    const [qty, setQty] = useState(entry?.qty != null ? String(entry.qty) : '');
    const [price, setPrice] = useState(entry?.price != null ? String(entry.price) : '');
    const [consideration, setConsideration] = useState(entry?.consideration != null && entry.qty == null ? indianDigits(String(entry.consideration)) : '');
    const [certificate, setCertificate] = useState(entry?.certificate ?? 'PENDING');
    const [investmentMail, setInvestmentMail] = useState(entry?.investmentMail ?? false);
    const [holding, setHolding] = useState(entry?.holdingStatus ?? 'ACTIVE');
    const [redeemedOn, setRedeemedOn] = useState(entry?.redeemedOn ?? '');
    const [remarks, setRemarks] = useState(entry?.remarks ?? '');
    const { busy, error, run } = useSubmit();
    const suggestions = useLoad('/api/pms/suggestions');
    const num = (s) => Number(s.replace(/,/g, '')) || 0;
    const stockValue = num(qty) > 0 && num(price) > 0 ? round2(num(qty) * num(price)) : null;
    async function submit(e) {
        e.preventDefault();
        const body = {
            ...party.body(),
            amc,
            strategy,
            amount: amount === '' ? null : num(amount),
            investmentDate: investmentDate || null,
            onPortal,
            trailPercent: trail === '' ? null : num(trail),
            payoutFrequency: frequency,
            qty: qty === '' ? null : num(qty),
            price: price === '' ? null : num(price),
            consideration: stockValue != null || consideration === '' ? null : num(consideration),
            certificate,
            investmentMail,
            holdingStatus: holding,
            redeemedOn: holding === 'REDEEMED' ? redeemedOn || null : null,
            remarks,
        };
        const ok = await run(() => (entry ? api(`/api/pms/${entry.id}`, { method: 'PUT', body, ifMatch: entry.updatedAt }) : api('/api/pms', { method: 'POST', body })));
        if (ok)
            onDone();
    }
    return (_jsxs("form", { onSubmit: submit, className: "fd-form", children: [_jsx(ClientFieldset, { party: party, radioName: "pms-client" }), _jsx(MonthRmFieldset, { party: party }), _jsxs("fieldset", { children: [_jsx("legend", { children: "PMS" }), _jsxs("label", { children: ["Partner (AMC)", _jsx("input", { value: amc, onChange: (e) => setAmc(e.target.value), list: "pms-amcs", placeholder: "e.g. ICICI PRU", required: true })] }), _jsx("datalist", { id: "pms-amcs", children: (suggestions.data?.amcs ?? []).map((n) => (_jsx("option", { value: n }, n))) }), _jsxs("label", { children: ["PMS (strategy)", _jsx("input", { value: strategy, onChange: (e) => setStrategy(e.target.value), list: "pms-strategies", placeholder: "e.g. ICICI PIPE", required: true })] }), _jsx("datalist", { id: "pms-strategies", children: (suggestions.data?.strategies ?? []).map((n) => (_jsx("option", { value: n }, n))) }), _jsxs("label", { children: ["Inv amt (\u20B9)", _jsx("input", { value: amount, onChange: (e) => setAmount(indianDigits(e.target.value)), inputMode: "decimal", required: true })] }), _jsxs("label", { children: ["Investment date", _jsx("input", { type: "date", value: investmentDate, onChange: (e) => setInvestmentDate(e.target.value), required: true })] }), _jsxs("label", { children: ["Mark up % (trail per year)", _jsx("input", { type: "number", min: 0, max: 100, step: "0.001", value: trail, onChange: (e) => setTrail(e.target.value), placeholder: "e.g. 1.25" })] }), _jsxs("label", { children: ["Payout", _jsx("select", { value: frequency, onChange: (e) => setFrequency(e.target.value), children: Object.entries(FREQUENCY).map(([k, v]) => (_jsx("option", { value: k, children: v }, k))) })] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: onPortal, onChange: (e) => setOnPortal(e.target.checked) }), "Entry: updated on app"] })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Stock transfer (if any)" }), _jsxs("label", { children: ["Qty", _jsx("input", { type: "number", min: 0, step: "0.0001", value: qty, onChange: (e) => setQty(e.target.value) })] }), _jsxs("label", { children: ["Price (\u20B9)", _jsx("input", { type: "number", min: 0, step: "0.0001", value: price, onChange: (e) => setPrice(e.target.value) })] }), _jsxs("label", { children: ["Consideration investment amount (\u20B9)", stockValue != null ? (_jsx("input", { value: formatMoney(stockValue), readOnly: true })) : (_jsx("input", { value: consideration, onChange: (e) => setConsideration(indianDigits(e.target.value)), inputMode: "decimal", placeholder: "or qty \u00D7 price" }))] })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Status" }), _jsxs("label", { children: ["Certificate status", _jsx("select", { value: certificate, onChange: (e) => setCertificate(e.target.value), children: Object.entries(CERTIFICATE).map(([k, v]) => (_jsx("option", { value: k, children: v }, k))) })] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: investmentMail, onChange: (e) => setInvestmentMail(e.target.checked) }), "Investment mail sent"] }), _jsxs("label", { children: ["Holding", _jsxs("select", { value: holding, onChange: (e) => setHolding(e.target.value), children: [_jsx("option", { value: "ACTIVE", children: "Active" }), _jsx("option", { value: "REDEEMED", children: "Redeemed" })] })] }), holding === 'REDEEMED' && (_jsxs("label", { children: ["Redeemed on", _jsx("input", { type: "date", value: redeemedOn, onChange: (e) => setRedeemedOn(e.target.value), required: true })] })), _jsxs("label", { children: ["Remarks", _jsx("textarea", { value: remarks, onChange: (e) => setRemarks(e.target.value), rows: 2, placeholder: "e.g. Commission will be received in Aug" })] })] }), error && _jsx("p", { className: "error", children: error }), _jsxs("span", { className: "inline-form", children: [_jsx("button", { type: "submit", disabled: busy || clientMode === 'lookup' || clientMode === 'pick', children: entry ? 'Save changes' : 'Add PMS investment' }), entry && _jsx(CancelEntry, { url: `/api/pms/${entry.id}/cancel`, question: "Why cancel this PMS investment?", label: "Cancel this PMS investment", onDone: onDone })] })] }));
}
function Payouts() {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const list = useLoad('/api/pms/payouts');
    const [open, setOpen] = useState(null);
    const active = (list.data ?? []).filter((p) => p.status === 'ACTIVE');
    const done = () => {
        setOpen(null);
        list.reload();
    };
    const period = (p) => (p.periodFrom === p.periodTo ? formatMonth(p.periodFrom) : `${formatMonth(p.periodFrom)} to ${formatMonth(p.periodTo)}`);
    return (_jsxs(_Fragment, { children: [canEdit && (_jsx("div", { className: "actions", style: { justifyContent: 'flex-end', marginBottom: 12 }, children: _jsx("button", { type: "button", onClick: () => setOpen('new'), children: "Add AMC payout" }) })), list.error && _jsx("p", { className: "error", children: list.error }), list.data && list.data.length === 0 && _jsx("p", { className: "muted", children: "No AMC payouts yet." }), list.data && list.data.length > 0 && (_jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "AMC" }), _jsx("th", { children: "Invoice period" }), _jsx("th", { className: "num", children: "Amt" }), _jsx("th", { className: "num", children: "Less TDS" }), _jsx("th", { className: "num", children: "GST" }), _jsx("th", { className: "num", children: "Net" }), _jsx("th", { className: "num", children: "Invoice" }), _jsx("th", { children: "Amt received" })] }) }), _jsxs("tbody", { children: [list.data.map((p) => (_jsxs("tr", { children: [_jsx("td", { children: _jsx("button", { type: "button", className: "cell-link", onClick: () => setOpen(p), children: p.amc }) }), _jsx("td", { children: period(p) }), _jsx("td", { className: "num", children: formatMoney(p.amount) }), _jsx("td", { className: "num", children: formatMoney(p.tds) }), _jsx("td", { className: "num", children: formatMoney(p.gst) }), _jsx("td", { className: "num", children: formatMoney(p.net) }), _jsx("td", { className: "num", children: formatMoney(p.invoice) }), _jsx("td", { children: p.receivedOn ? formatDate(p.receivedOn) : _jsx("span", { className: "badge warn", children: "Pending" }) })] }, p.id))), _jsxs("tr", { className: "total", children: [_jsxs("td", { colSpan: 2, children: ["Total (", active.length, " payouts)"] }), _jsx("td", { className: "num", children: _jsx(Amount, { value: active.reduce((s, p) => s + p.amount, 0) }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: active.reduce((s, p) => s + p.tds, 0) }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: active.reduce((s, p) => s + p.gst, 0) }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: active.reduce((s, p) => s + p.net, 0) }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: active.reduce((s, p) => s + p.invoice, 0) }) }), _jsx("td", {})] })] })] })), open && (_jsx(Modal, { title: open === 'new' ? 'Add AMC payout' : `${open.amc} — ${period(open)}`, onClose: () => setOpen(null), size: "narrow", children: canEdit ? _jsx(PayoutForm, { payout: open === 'new' ? undefined : open, onDone: done }) : _jsx("p", { className: "muted", children: "View only." }) }))] }));
}
/** Add or edit one AMC payout: AMC, invoice period, amount, TDS, received date. */
function PayoutForm({ payout, onDone }) {
    const [amc, setAmc] = useState(payout?.amc ?? '');
    const [from, setFrom] = useState(payout?.periodFrom ?? thisMonth());
    const [to, setTo] = useState(payout?.periodTo ?? thisMonth());
    const [amount, setAmount] = useState(payout ? String(payout.amount) : '');
    const [tdsApplied, setTdsApplied] = useState(payout?.tdsApplied ?? true);
    const [tdsPct, setTdsPct] = useState(payout ? String(payout.tdsPercent) : '');
    const [invoiceRaised, setInvoiceRaised] = useState(payout?.invoiceRaised ?? false);
    const [receivedOn, setReceivedOn] = useState(payout?.receivedOn ?? '');
    const [remarks, setRemarks] = useState(payout?.remarks ?? '');
    const suggestions = useLoad('/api/pms/suggestions');
    const tax = useLoad(`/api/product-taxes/PMS/on?date=${to || from}-01`);
    const { busy, error, run } = useSubmit();
    useEffect(() => {
        if (!payout && tax.data) {
            setTdsApplied(tax.data.tdsByDefault);
            setTdsPct(String(tax.data.tdsPercent));
        }
    }, [payout, tax.data]);
    const gstPct = payout && payout.periodTo === to ? payout.gstPercent : (tax.data?.gstPercent ?? 0);
    const fig = pmsPayoutFigures(Number(amount) || 0, tdsApplied, Number(tdsPct) || 0, gstPct);
    const submit = async (e) => {
        e.preventDefault();
        const body = {
            amc,
            periodFrom: `${from}-01`,
            periodTo: `${to || from}-01`,
            amount: amount === '' ? null : Number(amount),
            tdsApplied,
            tdsPercent: tdsApplied ? Number(tdsPct) : 0,
            invoiceRaised,
            receivedOn: receivedOn || null,
            remarks,
        };
        if (await run(() => (payout ? api(`/api/pms/payouts/${payout.id}`, { method: 'PUT', body }) : api('/api/pms/payouts', { method: 'POST', body }))))
            onDone();
    };
    return (_jsxs("form", { onSubmit: submit, className: "fd-form", children: [_jsxs("label", { children: ["AMC", _jsx("input", { value: amc, onChange: (e) => setAmc(e.target.value), list: "pms-payout-amcs", placeholder: "e.g. Motilal AMC", required: true })] }), _jsx("datalist", { id: "pms-payout-amcs", children: (suggestions.data?.amcs ?? []).map((n) => (_jsx("option", { value: n }, n))) }), _jsxs("span", { className: "inline-form", children: [_jsxs("label", { children: ["Invoice period from", _jsx("input", { type: "month", value: from, onChange: (e) => setFrom(e.target.value), required: true })] }), _jsxs("label", { children: ["to", _jsx("input", { type: "month", value: to, onChange: (e) => setTo(e.target.value) })] })] }), _jsxs("label", { children: ["Amt (\u20B9, excl. GST)", _jsx("input", { type: "number", min: 0, step: "0.01", value: amount, onChange: (e) => setAmount(e.target.value), required: true })] }), _jsxs("span", { className: "inline-form", children: [_jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: tdsApplied, onChange: (e) => setTdsApplied(e.target.checked) }), "Less TDS"] }), tdsApplied && (_jsxs("label", { children: ["TDS %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: tdsPct, onChange: (e) => setTdsPct(e.target.value), "aria-label": "TDS %", required: true })] }))] }), _jsxs("dl", { className: "facts", children: [_jsx("dt", { children: "TDS" }), _jsx("dd", { children: formatMoney(fig.tds) }), _jsxs("dt", { children: ["GST (", gstPct, "%)"] }), _jsx("dd", { children: formatMoney(fig.gst) }), _jsx("dt", { children: "Net" }), _jsx("dd", { children: _jsx("strong", { children: formatMoney(fig.net) }) }), _jsx("dt", { children: "Invoice amount" }), _jsx("dd", { children: formatMoney(fig.invoice) })] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: invoiceRaised, onChange: (e) => setInvoiceRaised(e.target.checked) }), "Invoice raised"] }), _jsxs("label", { children: ["Amt received date", _jsx("input", { type: "date", value: receivedOn, onChange: (e) => setReceivedOn(e.target.value) })] }), _jsxs("label", { children: ["Remarks", _jsx("textarea", { value: remarks, onChange: (e) => setRemarks(e.target.value), rows: 2 })] }), error && _jsx("p", { className: "error", children: error }), _jsxs("span", { className: "inline-form", children: [_jsx("button", { type: "submit", disabled: busy, children: payout ? 'Save changes' : 'Add AMC payout' }), payout && _jsx(CancelEntry, { url: `/api/pms/payouts/${payout.id}/cancel`, question: "Why cancel this payout?", label: "Cancel this payout", onDone: onDone })] })] }));
}
/** Each client's PMS revenue per month received (R-85): the month's PMS lines and the END's PMS column. */
function Revenue() {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const list = useLoad('/api/pms/fees');
    const [open, setOpen] = useState(null);
    const active = (list.data ?? []).filter((f) => f.status === 'ACTIVE');
    const view = useTableView(list.data, {
        month: (f) => f.month, client: (f) => f.clientName, amc: (f) => f.amc, rm: (f) => f.rmName, partner: (f) => f.partnerName, amount: (f) => f.amount,
    });
    const done = () => {
        setOpen(null);
        list.reload();
    };
    return (_jsxs(_Fragment, { children: [canEdit && (_jsx("div", { className: "actions", style: { justifyContent: 'flex-end', marginBottom: 12 }, children: _jsx("button", { type: "button", onClick: () => setOpen('new'), children: "Add month's revenue" }) })), list.error && _jsx("p", { className: "error", children: list.error }), list.data && list.data.length === 0 && _jsx("div", { className: "empty", children: "No PMS revenue typed yet. Add each client's share when the AMC's payout is received." }), list.data && list.data.length > 0 && (_jsxs(_Fragment, { children: [_jsx("div", { className: "table-scroll", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [view.th('month', 'Month received'), view.th('client', 'Client'), view.th('amc', 'AMC · strategy'), view.th('rm', 'RM'), view.th('partner', 'Referral / sub-broker'), view.th('amount', 'Revenue (END)', 'num'), _jsx("th", { children: "Remarks" })] }) }), _jsxs("tbody", { children: [view.rows.map((f) => (_jsxs("tr", { className: f.status === 'CANCELLED' ? 'muted' : undefined, children: [_jsx("td", { className: "nowrap", children: formatMonth(f.month) }), _jsxs("td", { children: [_jsx("button", { type: "button", className: "cell-link", onClick: () => setOpen(f), children: f.clientName }), f.status === 'CANCELLED' && _jsx("span", { className: "badge", children: "cancelled" })] }), _jsxs("td", { children: [f.amc, f.strategy ? _jsxs("span", { className: "muted", children: [" \u00B7 ", f.strategy] }) : null] }), _jsx("td", { children: f.rmName }), _jsx("td", { children: f.partnerName ?? _jsx("span", { className: "muted", children: "\u2014" }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: f.amount }) }), _jsx("td", { children: f.remarks })] }, f.id))), _jsxs("tr", { className: "total", children: [_jsxs("td", { colSpan: 5, children: ["Total (", active.length, ")"] }), _jsx("td", { className: "num", children: _jsx(Amount, { value: active.reduce((sum, f) => sum + f.amount, 0) }) }), _jsx("td", {})] })] })] }) }), view.more] })), open && (_jsx(Modal, { title: open === 'new' ? "Add a client's PMS revenue" : `${open.clientName} — ${formatMonth(open.month)}`, onClose: () => setOpen(null), children: canEdit && (open === 'new' || open.status === 'ACTIVE') ? (_jsx(FeeForm, { fee: open === 'new' ? undefined : open, onDone: done })) : (_jsx("p", { children: open !== 'new' && `${open.amc} · ${formatMoney(open.amount)} · cancelled` })) }))] }));
}
function FeeForm({ fee, onDone }) {
    const investments = useLoad('/api/pms');
    const [investmentId, setInvestmentId] = useState(fee ? String(fee.investmentId) : '');
    const [month, setMonth] = useState(fee ? fee.month : thisMonth());
    const [amount, setAmount] = useState(fee ? indianDigits(String(fee.amount)) : '');
    const [remarks, setRemarks] = useState(fee?.remarks ?? '');
    const { busy, error, run } = useSubmit();
    const options = (investments.data ?? []).filter((i) => i.status === 'ACTIVE' || String(i.id) === investmentId);
    async function submit(e) {
        e.preventDefault();
        const body = { investmentId: Number(investmentId), month: `${month.slice(0, 7)}-01`, amount: Number(amount.replace(/,/g, '')), remarks };
        const ok = await run(() => (fee ? api(`/api/pms/fees/${fee.id}`, { method: 'PUT', body }) : api('/api/pms/fees', { method: 'POST', body })));
        if (ok)
            onDone();
    }
    return (_jsxs("form", { onSubmit: submit, className: "fd-form", children: [_jsxs("fieldset", { children: [_jsx("legend", { children: "Revenue" }), _jsxs("label", { style: { gridColumn: '1 / -1' }, children: ["Client's PMS investment", _jsxs("select", { value: investmentId, onChange: (e) => setInvestmentId(e.target.value), required: true, disabled: !!fee, children: [_jsx("option", { value: "", children: "Choose\u2026" }), options.map((i) => (_jsxs("option", { value: i.id, children: [i.clientName, " \u2014 ", i.amc, i.strategy ? ` · ${i.strategy}` : '', " (", formatMoney(i.amount), ")", i.holdingStatus === 'REDEEMED' ? ' · redeemed' : ''] }, i.id)))] })] }), _jsxs("label", { children: ["Month the payout was received", _jsx("input", { type: "month", value: month.slice(0, 7), onChange: (e) => e.target.value && setMonth(e.target.value), required: true })] }), _jsxs("label", { children: ["Revenue for the END (\u20B9, excl. GST)", _jsx("input", { value: amount, onChange: (e) => setAmount(indianDigits(e.target.value)), inputMode: "decimal", required: true })] }), _jsxs("label", { style: { gridColumn: '1 / -1' }, children: ["Remarks", _jsx("textarea", { value: remarks, onChange: (e) => setRemarks(e.target.value), rows: 2 })] })] }), error && _jsx("p", { className: "error", children: error }), _jsxs("span", { className: "inline-form", children: [_jsx("button", { type: "submit", disabled: busy, children: fee ? 'Save changes' : 'Add revenue' }), fee && _jsx(CancelEntry, { url: `/api/pms/fees/${fee.id}/cancel`, question: "Why cancel this revenue entry?", label: "Cancel this revenue entry", onDone: onDone })] })] }));
}
