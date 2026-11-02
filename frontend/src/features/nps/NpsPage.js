import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { CancelEntry, ClientFieldset, MonthRmFieldset, useClientAndRm } from '../entry/ClientAndRm';
import { useState } from 'react';
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
/** The revenue as the server works it out (R-86): POP charges × sharing % when both are given, else as typed. */
export function npsFigures(popCharges, sharePct, typed, gstPct) {
    const revenue = popCharges != null && sharePct != null ? round2((popCharges * sharePct) / 100) : round2(typed);
    const gst = round2((revenue * gstPct) / 100);
    const cgst = round2(gst / 2);
    return { revenue, cgst, sgst: round2(gst - cgst), gst, invoice: round2(revenue + gst) };
}
const CERTIFICATE = { PENDING: 'Pending', RECEIVED: 'Received', NOT_APPLICABLE: 'Not applicable' };
const thisMonth = () => todayIso().slice(0, 7);
const PAYMENTS = { PENDING: 'Revenue not received', RECEIVED: 'Received' };
/** NPS: one row per investment, as in the NPS register, with its revenue when there is one (R-86). */
export function NpsPage({ embedded = false } = {}) {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const query = useFilterQuery();
    const list = useLoad(listPath('/api/nps', query));
    const pops = useLoad('/api/nps/pops');
    const [panel, setPanel] = useEntryPanel();
    const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
    const active = (list.data ?? []).filter((e) => e.status === 'ACTIVE');
    const done = () => {
        setPanel(null);
        list.reload();
        pops.reload();
    };
    const view = useTableView(list.data, { month: (e) => e.month, region: (e) => e.region, client: (e) => e.clientName, rm: (e) => e.rmName, partner_pop: (e) => e.pop, inv_date: (e) => e.investmentDate, investment_amt: (e) => e.amount, invoice_w_o_gst: (e) => e.revenue, invoice_with_gst: (e) => e.invoice, amt_received: (e) => e.receivedOn });
    return (_jsxs("section", { children: [_jsxs("div", { className: "page-head", style: embedded ? { justifyContent: 'flex-end', marginBottom: 12 } : undefined, children: [!embedded && _jsx("h1", { children: "NPS" }), canEdit && (_jsxs("div", { className: "actions", children: [_jsx("a", { className: "button-link secondary", href: `/api/nps/export.xlsx${query ?? ''}`, download: true, children: "Download Excel" }), _jsx("button", { type: "button", onClick: () => setPanel('new'), children: "Add NPS investment" })] }))] }), _jsx("p", { className: "muted", children: "An entry with revenue counts in its month: the END shows it in the NPS column, shared at the partner's NPS %." }), _jsx(ListFilters, { searchHint: "Client, PAN, code or POP", platformKey: "pop", platformLabel: "POP", platforms: (pops.data ?? []).map((p) => ({ value: p, label: p })), payments: PAYMENTS }), list.error && _jsx("p", { className: "error", children: list.error }), list.data && list.data.length === 0 && _jsx("p", { className: "muted", children: "No NPS investments match." }), list.data && list.data.length > 0 && (_jsxs(_Fragment, { children: [_jsx("div", { className: "table-scroll", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { className: "num", children: "SR.NO" }), view.th('month', 'Month'), view.th('region', 'Region'), view.th('client', 'Client'), view.th('rm', 'RM'), view.th('partner_pop', 'Partner (POP)'), view.th('inv_date', 'INV date'), view.th('investment_amt', 'Investment amt', 'num'), _jsx("th", { children: "Revenue remarks" }), view.th('invoice_w_o_gst', 'Invoice w/o GST', 'num'), view.th('invoice_with_gst', 'Invoice with GST', 'num'), view.th('amt_received', 'Amt received')] }) }), _jsxs("tbody", { children: [view.rows.map((e, i) => (_jsxs("tr", { className: e.status === 'CANCELLED' ? 'muted' : undefined, children: [_jsx("td", { className: "num", children: i + 1 }), _jsx("td", { children: formatMonth(e.month) }), _jsx("td", { children: e.region ?? _jsx("span", { className: "muted", children: "\u2014" }) }), _jsxs("td", { children: [_jsx("button", { type: "button", className: "cell-link", onClick: () => setPanel(e.id), children: e.clientName }), e.status === 'CANCELLED' && _jsx("span", { className: "badge", children: "cancelled" })] }), _jsx("td", { children: e.rmName }), _jsx("td", { children: e.pop }), _jsx("td", { children: formatDate(e.investmentDate) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.amount }) }), _jsx("td", { className: "muted", children: e.revenueRemarks ?? '' }), _jsx("td", { className: "num", children: e.revenue > 0 ? formatMoney(e.revenue) : '' }), _jsx("td", { className: "num", children: e.revenue > 0 ? formatMoney(e.invoice) : '' }), _jsx("td", { children: e.receivedOn ? formatDate(e.receivedOn) : e.revenue > 0 ? _jsx("span", { className: "badge warn", children: "Pending" }) : '' })] }, e.id))), _jsxs("tr", { className: "total", children: [_jsxs("td", { colSpan: 7, children: ["Total (", active.length, " investments)"] }), _jsx("td", { className: "num", children: _jsx(Amount, { value: active.reduce((s, e) => s + e.amount, 0) }) }), _jsx("td", {}), _jsx("td", { className: "num", children: _jsx(Amount, { value: active.reduce((s, e) => s + e.revenue, 0) }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: active.reduce((s, e) => s + e.invoice, 0) }) }), _jsx("td", {})] })] })] }) }), view.more] })), panel === 'new' && (_jsx(Modal, { title: "Add NPS investment", onClose: () => setPanel(null), children: _jsx(NpsForm, { month: thisMonth(), onDone: done }) })), editing && (_jsx(Modal, { title: `${editing.clientName} — ${editing.pop}`, onClose: () => setPanel(null), children: canEdit && editing.status === 'ACTIVE' ? (_jsx(NpsForm, { month: thisMonth(), entry: editing, onDone: done })) : (_jsxs("dl", { className: "facts", children: [_jsx("dt", { children: "POP" }), _jsx("dd", { children: editing.pop }), _jsx("dt", { children: "Amount" }), _jsx("dd", { children: formatMoney(editing.amount) })] })) }))] }));
}
/** Add or edit one NPS investment, in the register's order, with a Revenue section. */
function NpsForm({ month: listMonth, entry, onDone }) {
    const party = useClientAndRm(entry, entry ? entry.month : listMonth);
    const { month, monthDay, clientMode } = party;
    const [pop, setPop] = useState(entry?.pop ?? '');
    const [investmentDate, setInvestmentDate] = useState(entry?.investmentDate ?? '');
    const [amount, setAmount] = useState(entry ? indianDigits(String(entry.amount)) : '');
    const [welcomeMail, setWelcomeMail] = useState(entry?.welcomeMail ?? false);
    const [investmentMail, setInvestmentMail] = useState(entry?.investmentMail ?? false);
    const [certificate, setCertificate] = useState(entry?.certificate ?? 'PENDING');
    const [onPortal, setOnPortal] = useState(entry?.onPortal ?? false);
    const [revenueRemarks, setRevenueRemarks] = useState(entry?.revenueRemarks ?? '');
    const [popCharges, setPopCharges] = useState(entry?.popCharges != null ? String(entry.popCharges) : '');
    const [sharePct, setSharePct] = useState(entry?.sharePercent != null ? String(entry.sharePercent) : '');
    const [revenue, setRevenue] = useState(entry && entry.popCharges == null && entry.revenue > 0 ? String(entry.revenue) : '');
    const [receivedOn, setReceivedOn] = useState(entry?.receivedOn ?? '');
    const [remarks, setRemarks] = useState(entry?.remarks ?? '');
    const { busy, error, run } = useSubmit();
    const pops = useLoad('/api/nps/pops');
    const tax = useLoad(`/api/product-taxes/NPS/on?date=${monthDay}`);
    const num = (s) => Number(s.replace(/,/g, '')) || 0;
    const fromCharges = popCharges !== '' && sharePct !== '';
    const gstPct = entry && entry.month === month.slice(0, 7) ? entry.gstPercent : (tax.data?.gstPercent ?? 0);
    const fig = npsFigures(fromCharges ? num(popCharges) : null, fromCharges ? num(sharePct) : null, num(revenue), gstPct);
    async function submit(e) {
        e.preventDefault();
        const body = {
            ...party.body(),
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
        const ok = await run(() => (entry ? api(`/api/nps/${entry.id}`, { method: 'PUT', body, ifMatch: entry.updatedAt }) : api('/api/nps', { method: 'POST', body })));
        if (ok)
            onDone();
    }
    return (_jsxs("form", { onSubmit: submit, className: "fd-form", children: [tax.error && _jsx("p", { className: "error", children: tax.error }), _jsx(ClientFieldset, { party: party, radioName: "nps-client" }), _jsx(MonthRmFieldset, { party: party }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Investment" }), _jsxs("label", { children: ["Partner (POP)", _jsx("input", { value: pop, onChange: (e) => setPop(e.target.value), list: "nps-pops", placeholder: "e.g. STOCK HOLDING", required: true })] }), _jsx("datalist", { id: "nps-pops", children: ['STOCK HOLDING', 'CAMS', 'ICICI NPS', ...(pops.data ?? [])]
                            .filter((v, i, a) => a.findIndex((x) => x.toLowerCase() === v.toLowerCase()) === i)
                            .map((n) => (_jsx("option", { value: n }, n))) }), _jsxs("label", { children: ["INV date", _jsx("input", { type: "date", value: investmentDate, onChange: (e) => setInvestmentDate(e.target.value), required: true })] }), _jsxs("label", { children: ["Investment amt (\u20B9)", _jsx("input", { value: amount, onChange: (e) => setAmount(indianDigits(e.target.value)), inputMode: "decimal", required: true })] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: welcomeMail, onChange: (e) => setWelcomeMail(e.target.checked) }), "Welcome mail"] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: investmentMail, onChange: (e) => setInvestmentMail(e.target.checked) }), "Investment mail"] }), _jsxs("label", { children: ["Certificate status", _jsx("select", { value: certificate, onChange: (e) => setCertificate(e.target.value), children: Object.entries(CERTIFICATE).map(([k, v]) => (_jsx("option", { value: k, children: v }, k))) })] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: onPortal, onChange: (e) => setOnPortal(e.target.checked) }), "Entry status: updated on app"] })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Revenue" }), _jsxs("label", { children: ["Revenue remarks", _jsx("input", { value: revenueRemarks, onChange: (e) => setRevenueRemarks(e.target.value), placeholder: "e.g. sharing will 60% of POP Charges / NO REVENUE" })] }), _jsxs("span", { className: "inline-form", children: [_jsxs("label", { children: ["POP charges (\u20B9)", _jsx("input", { type: "number", min: 0, step: "0.01", value: popCharges, onChange: (e) => setPopCharges(e.target.value) })] }), _jsxs("label", { children: ["Sharing %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: sharePct, onChange: (e) => setSharePct(e.target.value) })] })] }), _jsxs("label", { children: ["Invoice amt without GST (\u20B9)", fromCharges ? (_jsx("input", { value: formatMoney(fig.revenue), readOnly: true })) : (_jsx("input", { type: "number", min: 0, step: "0.01", value: revenue, onChange: (e) => setRevenue(e.target.value), placeholder: "or POP charges \u00D7 sharing %" }))] }), _jsxs("dl", { className: "facts", children: [_jsxs("dt", { children: ["CGST (", gstPct / 2, "%)"] }), _jsx("dd", { children: formatMoney(fig.cgst) }), _jsxs("dt", { children: ["SGST (", gstPct / 2, "%)"] }), _jsx("dd", { children: formatMoney(fig.sgst) }), _jsx("dt", { children: "Invoice amt with GST" }), _jsx("dd", { children: _jsx("strong", { children: formatMoney(fig.invoice) }) })] }), _jsxs("label", { children: ["Amt received date", _jsx("input", { type: "date", value: receivedOn, onChange: (e) => setReceivedOn(e.target.value) })] }), _jsxs("label", { children: ["Remarks", _jsx("textarea", { value: remarks, onChange: (e) => setRemarks(e.target.value), rows: 2 })] })] }), error && _jsx("p", { className: "error", children: error }), _jsxs("span", { className: "inline-form", children: [_jsx("button", { type: "submit", disabled: busy || clientMode === 'lookup' || clientMode === 'pick', children: entry ? 'Save changes' : 'Add NPS investment' }), entry && _jsx(CancelEntry, { url: `/api/nps/${entry.id}/cancel`, question: "Why cancel this NPS entry?", onDone: onDone })] })] }));
}
