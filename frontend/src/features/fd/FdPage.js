import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { CancelEntry, ClientFieldset, MonthRmFieldset, useClientAndRm } from '../entry/ClientAndRm';
import { useEffect, useState } from 'react';
import { useTableView } from '../../components/useTableView';
import { api } from '../../api/client';
import { CERTIFICATE_LABEL, COMPOUNDING_LABEL, INTEREST_FREQUENCY_LABEL, PAYMENT_LABEL, } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { ListFilters, listPath, useFilterQuery } from '../../components/ListFilters';
import { GivenFiguresFields, givenBody, shownFigures, useGiven } from '../../components/GivenFigures';
import { Modal } from '../../components/Modal';
import { useEntryPanel } from '../../components/useEntryPanel';
import { formatDate, formatMoney, todayIso, formatMonth, indianDigits } from '../../format';
import { Amount } from '../../components/Amount';
const round2 = (n) => Math.round((n + Number.EPSILON) * 100) / 100;
/** The commission as the server works it out (R-70), for the live preview while typing. */
export function fdRevenue(amount, margin, tdsApplied, tdsPct, gstPct) {
    const gross = round2((amount * margin) / 100);
    const tds = tdsApplied ? round2((gross * tdsPct) / 100) : 0;
    const gst = round2((gross * gstPct) / 100);
    return { gross, tds, gst, net: round2(gross - tds), invoice: round2(gross + gst) };
}
const COMPOUNDS_PER_YEAR = { QUARTERLY: 4, HALF_YEARLY: 2, YEARLY: 1 };
/**
 * Maturity value as the server works it out (R-72), from the period in months. Cumulative: compounded n times a year for
 * each full period, simple interest on the months left; interest paid out: P + P × r × months/12.
 */
export function maturityValue(amount, ratePct, months, frequency, compounding) {
    if (!amount || !months || months < 1 || !frequency)
        return null;
    const r = ratePct / 100;
    if (frequency !== 'CUMULATIVE')
        return round2(amount + (amount * r * months) / 12);
    const n = COMPOUNDS_PER_YEAR[compounding];
    const full = Math.floor(months / (12 / n));
    const left = months - full * (12 / n);
    return round2(amount * Math.pow(1 + r / n, full) * (1 + (r * left) / 12));
}
/** Deposit date + months → maturity date (same day of the month; the last day when the month is shorter). */
export function addMonths(iso, months) {
    if (!iso || !months)
        return '';
    const [y, m, d] = iso.split('-').map(Number);
    const target = new Date(Date.UTC(y, m - 1 + months, 1));
    const last = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
    target.setUTCDate(Math.min(d, last));
    return target.toISOString().slice(0, 10);
}
/** Whole months from deposit to maturity (days left over are dropped, as the register does). */
export function monthsBetween(from, to) {
    if (!from || !to || to <= from)
        return 0;
    const [y1, m1, d1] = from.split('-').map(Number);
    const [y2, m2, d2] = to.split('-').map(Number);
    return (y2 - y1) * 12 + (m2 - m1) - (d2 < d1 ? 1 : 0);
}
const thisMonth = () => todayIso().slice(0, 7);
/** FD: one row per FD booked, entered by hand (Phase 2, entry only). */
export function FdPage({ embedded = false } = {}) {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const month = thisMonth(); // the month a new FD starts in
    const query = useFilterQuery();
    const list = useLoad(listPath('/api/fd', query));
    const suggestions = useLoad('/api/fd/suggestions');
    const [panel, setPanel] = useEntryPanel();
    const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
    const active = (list.data ?? []).filter((e) => e.status === 'ACTIVE');
    const total = (k) => active.reduce((s, e) => s + e[k], 0);
    const done = () => {
        setPanel(null);
        list.reload();
    };
    const view = useTableView(list.data, { month: (e) => e.month, deposit: (e) => e.depositDate, client: (e) => e.clientName, fd: (e) => e.fdName, rm: (e) => e.rmName, amount: (e) => e.amount, gross: (e) => e.gross, net: (e) => e.net, invoice: (e) => e.invoice, certificate: (e) => e.certificate, payment: (e) => e.paymentStatus });
    return (_jsxs("section", { children: [_jsxs("div", { className: "page-head", style: embedded ? { justifyContent: 'flex-end', marginBottom: 12 } : undefined, children: [!embedded && _jsx("h1", { children: "FD" }), canEdit && (_jsxs("div", { className: "actions", children: [_jsx("a", { className: "button-link secondary", href: `/api/fd/export.xlsx${query ?? ''}`, download: true, children: "Download Excel" }), _jsx("button", { type: "button", onClick: () => setPanel('new'), children: "Add FD" })] }))] }), _jsx(ListFilters, { searchHint: "Client, PAN, code, FD company or FD no.", platformKey: "platform", platforms: (suggestions.data?.platforms ?? []).map((p) => ({ value: p, label: p })) }), list.error && _jsx("p", { className: "error", children: list.error }), list.data && list.data.length === 0 && _jsx("p", { className: "muted", children: "No FDs match." }), list.data && list.data.length > 0 && (_jsxs(_Fragment, { children: [_jsx("div", { className: "table-scroll", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { className: "num", children: "SR.NO" }), view.th('month', 'Month'), view.th('deposit', 'Deposit'), view.th('client', 'Client'), view.th('fd', 'FD'), view.th('rm', 'RM'), view.th('amount', 'Amount', 'num'), view.th('gross', 'Gross', 'num'), view.th('net', 'Net', 'num'), view.th('invoice', 'Invoice', 'num'), view.th('certificate', 'Certificate'), view.th('payment', 'Payment')] }) }), _jsxs("tbody", { children: [view.rows.map((e, i) => (_jsxs("tr", { className: e.status === 'CANCELLED' ? 'muted' : undefined, children: [_jsx("td", { className: "num", children: i + 1 }), _jsx("td", { children: formatMonth(e.month) }), _jsx("td", { children: formatDate(e.depositDate) }), _jsxs("td", { children: [_jsx("button", { type: "button", className: "cell-link", onClick: () => setPanel(e.id), children: e.clientName }), e.status === 'CANCELLED' && _jsx("span", { className: "badge", children: "cancelled" })] }), _jsxs("td", { children: [e.fdName, e.fdNo ? _jsxs("span", { className: "muted", children: [" #", e.fdNo] }) : null] }), _jsx("td", { children: e.rmName }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.amount }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.gross }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.net }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.invoice }) }), _jsx("td", { children: CERTIFICATE_LABEL[e.certificate] }), _jsx("td", { children: e.paymentStatus === 'RECEIVED' ? PAYMENT_LABEL[e.paymentStatus] : _jsx("span", { className: "error", children: PAYMENT_LABEL[e.paymentStatus] }) })] }, e.id))), _jsxs("tr", { className: "total", children: [_jsxs("td", { colSpan: 6, children: ["Total (", active.length, " FDs)"] }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('amount') }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('gross') }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('net') }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('invoice') }) }), _jsx("td", { colSpan: 2 })] })] })] }) }), view.more] })), panel === 'new' && (_jsx(Modal, { title: "Add FD", onClose: () => setPanel(null), children: _jsx(FdForm, { month: month, onDone: done }) })), editing && (_jsx(Modal, { title: `${editing.clientName} — ${editing.fdName}`, onClose: () => setPanel(null), children: canEdit && editing.status === 'ACTIVE' ? _jsx(FdForm, { month: month, entry: editing, onDone: done }) : _jsx(FdFacts, { entry: editing }) }))] }));
}
function FdFacts({ entry: e }) {
    return (_jsxs("dl", { className: "facts", children: [_jsx("dt", { children: "Client" }), _jsxs("dd", { children: [e.clientName, " ", _jsx("span", { className: "mono", children: e.pan })] }), _jsx("dt", { children: "FD" }), _jsxs("dd", { children: [e.fdName, " ", e.fdNo] }), _jsx("dt", { children: "Amount" }), _jsx("dd", { children: formatMoney(e.amount) }), _jsx("dt", { children: "Gross / Net / Invoice" }), _jsxs("dd", { children: [formatMoney(e.gross), " / ", formatMoney(e.net), " / ", formatMoney(e.invoice)] }), _jsx("dt", { children: "Status" }), _jsx("dd", { children: e.status === 'CANCELLED' ? 'Cancelled' : PAYMENT_LABEL[e.paymentStatus] })] }));
}
/** Add or edit one FD, laid out as in the FD mapping sheet. */
function FdForm({ month: listMonth, entry, onDone }) {
    const party = useClientAndRm(entry, entry ? entry.month : listMonth);
    const { month, monthDay, clientMode } = party;
    const [fdName, setFdName] = useState(entry?.fdName ?? '');
    const [fdNo, setFdNo] = useState(entry?.fdNo ?? '');
    const [frequency, setFrequency] = useState(entry?.interestFrequency ?? '');
    const [platform, setPlatform] = useState(entry?.platform ?? '');
    const [depositDate, setDepositDate] = useState(entry?.depositDate ?? '');
    const [maturityDate, setMaturityDate] = useState(entry?.maturityDate ?? '');
    const [rate, setRate] = useState(entry ? String(entry.ratePercent) : '');
    const [amount, setAmount] = useState(entry ? indianDigits(String(entry.amount)) : '');
    const [compounding, setCompounding] = useState(entry?.compounding ?? 'YEARLY');
    const [months, setMonths] = useState(entry ? String(entry.periodMonths) : '');
    const [marginTouched, setMarginTouched] = useState(!!entry);
    const [certificate, setCertificate] = useState(entry?.certificate ?? 'PENDING');
    const [margin, setMargin] = useState(entry ? String(entry.marginPercent) : '');
    const [tdsApplied, setTdsApplied] = useState(entry?.tdsApplied ?? false);
    const [tdsPct, setTdsPct] = useState(entry ? String(entry.tdsPercent) : '');
    const [invoiceRaised, setInvoiceRaised] = useState(entry?.invoiceRaised ?? false);
    const [onPortal, setOnPortal] = useState(entry?.onPortal ?? false);
    const [payment, setPayment] = useState(entry?.paymentStatus ?? 'PENDING');
    const [remarks, setRemarks] = useState(entry?.remarks ?? '');
    const { busy, error, run } = useSubmit();
    const suggestions = useLoad('/api/fd/suggestions');
    const tax = useLoad(`/api/product-taxes/FD/on?date=${monthDay}`);
    // A new entry starts with the product's TDS default; a client's own partner and RM are suggested.
    useEffect(() => {
        if (!entry && tax.data) {
            setTdsApplied(tax.data.tdsByDefault);
            setTdsPct(String(tax.data.tdsPercent));
        }
    }, [entry, tax.data]);
    // One match: that is the client. Its RM comes from the Clients screen; its own partner is suggested.
    const num = (s) => Number(s.replace(/,/g, '')) || 0;
    const gstPct = entry && entry.month === month.slice(0, 7) ? entry.gstPercent : (tax.data?.gstPercent ?? 0);
    const [given, setGiven] = useGiven(entry);
    const worked = fdRevenue(num(amount), num(margin), tdsApplied, num(tdsPct), gstPct);
    const rev = shownFigures(worked, given);
    const maturity = maturityValue(num(amount), num(rate), num(months), frequency, compounding);
    // Company + platform + period known → the margin last used for them, until the margin is typed by hand.
    const lastMargin = suggestions.data?.margins.find((m) => m.fdName.toLowerCase() === fdName.trim().toLowerCase() && (m.platform ?? '').toLowerCase() === platform.trim().toLowerCase() && m.periodMonths === num(months));
    useEffect(() => {
        if (!marginTouched && lastMargin)
            setMargin(String(lastMargin.marginPercent));
    }, [lastMargin, marginTouched]);
    const changeDeposit = (d) => {
        setDepositDate(d);
        if (num(months) > 0)
            setMaturityDate(addMonths(d, num(months)));
    };
    const changeMonths = (m) => {
        setMonths(m);
        if (depositDate && Number(m) > 0)
            setMaturityDate(addMonths(depositDate, Number(m)));
    };
    const changeMaturity = (d) => {
        setMaturityDate(d);
        const m = monthsBetween(depositDate, d);
        if (m > 0)
            setMonths(String(m));
    };
    async function submit(e) {
        e.preventDefault();
        const body = {
            ...party.body(),
            fdName,
            fdNo,
            interestFrequency: frequency || null,
            platform,
            depositDate: depositDate || null,
            periodMonths: months === '' ? null : num(months),
            maturityDate: maturityDate || null,
            ratePercent: rate === '' ? null : num(rate),
            amount: amount === '' ? null : num(amount),
            compounding: frequency === 'CUMULATIVE' ? compounding : null,
            certificate,
            marginPercent: margin === '' ? null : num(margin),
            tdsApplied,
            tdsPercent: tdsApplied ? num(tdsPct) : 0,
            invoiceRaised,
            onPortal,
            paymentStatus: payment,
            remarks,
            ...givenBody(given),
        };
        const ok = await run(() => (entry ? api(`/api/fd/${entry.id}`, { method: 'PUT', body, ifMatch: entry.updatedAt }) : api('/api/fd', { method: 'POST', body })));
        if (ok)
            onDone();
    }
    return (_jsxs("form", { onSubmit: submit, className: "fd-form", children: [tax.error && _jsx("p", { className: "error", children: tax.error }), _jsx(ClientFieldset, { party: party, radioName: "fd-client", mobileLabel: "Mobile", emailLabel: "Email" }), _jsx(MonthRmFieldset, { party: party, partnerLabel: "Referral / sub-broker" }), _jsxs("fieldset", { children: [_jsx("legend", { children: "FD" }), _jsxs("label", { children: ["FD company (FD name)", _jsx("input", { value: fdName, onChange: (e) => setFdName(e.target.value), list: "fd-companies", placeholder: "e.g. BAJAJ FINANCE LIMITED", required: true })] }), _jsx("datalist", { id: "fd-companies", children: (suggestions.data?.fdNames ?? []).map((n) => (_jsx("option", { value: n }, n))) }), _jsxs("label", { children: ["FD no.", _jsx("input", { value: fdNo, onChange: (e) => setFdNo(e.target.value) })] }), _jsxs("label", { children: ["Partner (platform)", _jsx("input", { value: platform, onChange: (e) => setPlatform(e.target.value), list: "fd-platforms", placeholder: "e.g. J M Finance" })] }), _jsx("datalist", { id: "fd-platforms", children: (suggestions.data?.platforms ?? []).map((n) => (_jsx("option", { value: n }, n))) }), _jsxs("label", { children: ["Interest frequency", _jsxs("select", { value: frequency, onChange: (e) => setFrequency(e.target.value), required: true, children: [_jsx("option", { value: "", children: "Choose\u2026" }), Object.entries(INTEREST_FREQUENCY_LABEL).map(([k, v]) => (_jsx("option", { value: k, children: v }, k)))] })] }), _jsxs("label", { children: ["Deposit date", _jsx("input", { type: "date", value: depositDate, onChange: (e) => changeDeposit(e.target.value), required: true })] }), _jsxs("label", { children: ["Deposit period (months)", _jsx("input", { type: "number", min: 1, max: 600, step: 1, value: months, onChange: (e) => changeMonths(e.target.value), required: true })] }), _jsxs("label", { children: ["Maturity date", _jsx("input", { type: "date", value: maturityDate, onChange: (e) => changeMaturity(e.target.value), required: true })] }), _jsxs("label", { children: ["Interest rate %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: rate, onChange: (e) => setRate(e.target.value), required: true })] }), _jsxs("label", { children: ["Investment amount (\u20B9)", _jsx("input", { value: amount, onChange: (e) => setAmount(indianDigits(e.target.value)), inputMode: "decimal", required: true })] }), frequency === 'CUMULATIVE' && (_jsxs("label", { children: ["Compounding", _jsx("select", { value: compounding, onChange: (e) => setCompounding(e.target.value), children: Object.entries(COMPOUNDING_LABEL).map(([k, v]) => (_jsx("option", { value: k, children: v }, k))) })] })), _jsxs("label", { children: ["Maturity value (\u20B9)", _jsx("input", { value: maturity == null ? '' : formatMoney(maturity), readOnly: true, placeholder: "worked out from amount, rate and dates" })] }), _jsxs("label", { children: ["Certificate", _jsx("select", { value: certificate, onChange: (e) => setCertificate(e.target.value), children: Object.entries(CERTIFICATE_LABEL).map(([k, v]) => (_jsx("option", { value: k, children: v }, k))) })] })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Commission" }), _jsx(GivenFiguresFields, { value: given, onChange: setGiven }), _jsxs("label", { children: ["Margin %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.001", value: margin, onChange: (e) => {
                                    setMarginTouched(true);
                                    setMargin(e.target.value);
                                }, required: true })] }), lastMargin && _jsxs("p", { className: "muted", children: ["Last used for ", lastMargin.fdName, " \u00B7 ", lastMargin.platform ?? 'no platform', " \u00B7 ", lastMargin.periodMonths, " months: ", lastMargin.marginPercent, "%"] }), _jsxs("span", { className: "inline-form", children: [_jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: tdsApplied, onChange: (e) => setTdsApplied(e.target.checked) }), "TDS"] }), tdsApplied && (_jsxs("label", { children: ["TDS %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: tdsPct, onChange: (e) => setTdsPct(e.target.value), "aria-label": "TDS %", required: true })] }))] }), _jsxs("dl", { className: "facts", children: [_jsx("dt", { children: "Gross revenue" }), _jsx("dd", { children: formatMoney(rev.gross) }), _jsx("dt", { children: "TDS" }), _jsx("dd", { children: formatMoney(rev.tds) }), _jsxs("dt", { children: ["GST (", gstPct, "%)"] }), _jsx("dd", { children: formatMoney(rev.gst) }), _jsx("dt", { children: "Net revenue" }), _jsx("dd", { children: _jsx("strong", { children: formatMoney(rev.net) }) }), _jsx("dt", { children: "Invoice amount" }), _jsx("dd", { children: formatMoney(rev.invoice) })] })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Follow-up" }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: invoiceRaised, onChange: (e) => setInvoiceRaised(e.target.checked) }), "Invoice raised by accounts"] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: onPortal, onChange: (e) => setOnPortal(e.target.checked) }), "Updated on Epsilon portal"] }), _jsxs("label", { children: ["Payment status", _jsx("select", { value: payment, onChange: (e) => setPayment(e.target.value), children: Object.entries(PAYMENT_LABEL).map(([k, v]) => (_jsx("option", { value: k, children: v }, k))) })] }), _jsxs("label", { children: ["Remarks", _jsx("textarea", { value: remarks, onChange: (e) => setRemarks(e.target.value), rows: 2 })] })] }), error && _jsx("p", { className: "error", children: error }), _jsxs("span", { className: "inline-form", children: [_jsx("button", { type: "submit", disabled: busy || clientMode === 'lookup' || clientMode === 'pick', children: entry ? 'Save changes' : 'Add FD' }), entry && _jsx(CancelEntry, { url: `/api/fd/${entry.id}/cancel`, question: "Why cancel this FD entry?", label: "Cancel this FD", onDone: onDone })] })] }));
}
