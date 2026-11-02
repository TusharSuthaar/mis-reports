import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { CancelEntry, ClientFieldset, MonthRmFieldset, useClientAndRm } from '../entry/ClientAndRm';
import { useEffect, useState } from 'react';
import { useTableView } from '../../components/useTableView';
import { api } from '../../api/client';
import { LOAN_PAYMENT_LABEL, } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { Amount } from '../../components/Amount';
import { GivenFiguresFields, givenBody, shownFigures, useGiven } from '../../components/GivenFigures';
import { ListFilters, listPath, useFilterQuery } from '../../components/ListFilters';
import { Modal } from '../../components/Modal';
import { useEntryPanel } from '../../components/useEntryPanel';
import { formatDate, formatMoney, todayIso, formatMonth, indianDigits } from '../../format';
const round2 = (n) => Math.round((n + Number.EPSILON) * 100) / 100;
/** The payout as the server works it out (R-80), for the live preview while typing. */
export function loanRevenue(base, payoutPct, fixed, tdsApplied, tdsPct, gstPct) {
    const gross = round2(round2((base * payoutPct) / 100) + fixed);
    const tds = tdsApplied ? round2((gross * tdsPct) / 100) : 0;
    const gst = round2((gross * gstPct) / 100);
    return { gross, tds, gst, net: round2(gross - tds), invoice: round2(gross + gst) };
}
/** "2026-08" + 3 → "2026-11". */
export function monthPlus(ym, n) {
    const [y, m] = ym.split('-').map(Number);
    const d = new Date(y, m - 1 + n, 1);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}
const thisMonth = () => todayIso().slice(0, 7);
/** The register's Payment Status column, as it reads. */
export function paymentText(e) {
    if (e.paymentNote)
        return e.paymentNote;
    if (e.loanClosed)
        return 'Loan Closed';
    return e.paymentStatus === 'RECEIVED' ? 'CREDITED' : 'Pending';
}
/** Loans: one row per payout (a disbursal payout, or an LAS trail payout each quarter), as in the LOANS register. */
export function LoansPage({ embedded = false } = {}) {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const query = useFilterQuery();
    const list = useLoad(listPath('/api/loans', query));
    const suggestions = useLoad('/api/loans/suggestions');
    const [panel, setPanel] = useEntryPanel();
    const [nextOf, setNextOf] = useState(null);
    const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
    // As in the register: open loans first, then a Closed section.
    const openLoans = (list.data ?? []).filter((e) => !e.loanClosed);
    const closedLoans = (list.data ?? []).filter((e) => e.loanClosed);
    const done = () => {
        setPanel(null);
        setNextOf(null);
        list.reload();
        suggestions.reload();
    };
    return (_jsxs("section", { children: [_jsxs("div", { className: "page-head", style: embedded ? { justifyContent: 'flex-end', marginBottom: 12 } : undefined, children: [!embedded && _jsx("h1", { children: "Loans" }), canEdit && (_jsxs("div", { className: "actions", children: [_jsx("a", { className: "button-link secondary", href: `/api/loans/export.xlsx${query ?? ''}`, download: true, children: "Download Excel" }), _jsx("button", { type: "button", onClick: () => setPanel('new'), children: "Add loan payout" })] }))] }), _jsx(ListFilters, { searchHint: "Client, PAN, code, lender or loan type", platformKey: "lender", platformLabel: "Lender", platforms: (suggestions.data?.lenders ?? []).map((p) => ({ value: p, label: p })), payments: LOAN_PAYMENT_LABEL }), list.error && _jsx("p", { className: "error", children: list.error }), list.data && list.data.length === 0 && _jsx("p", { className: "muted", children: "No loan payouts match." }), list.data && list.data.length > 0 && (_jsxs(_Fragment, { children: [openLoans.length > 0 ? _jsx(LoanTable, { rows: openLoans, onOpen: setPanel }) : _jsx("p", { className: "muted", children: "No open loans match." }), closedLoans.length > 0 && (_jsxs("div", { className: "loan-closed", children: [_jsxs("h2", { children: ["Closed ", _jsx("span", { className: "badge", children: closedLoans.length })] }), _jsx("p", { className: "muted", style: { marginTop: 0 }, children: "Loans marked closed (tick \u201CLoan closed\u201D on the entry), as in the register\u2019s Closed section. They stay in their months\u2019 figures." }), _jsx(LoanTable, { rows: closedLoans, onOpen: setPanel })] }))] })), (panel === 'new' || nextOf) && (_jsx(Modal, { title: nextOf ? `Next payout — ${nextOf.clientName}` : 'Add loan payout', onClose: () => {
                    setPanel(null);
                    setNextOf(null);
                }, children: _jsx(LoanForm, { month: thisMonth(), copyOf: nextOf ?? undefined, onDone: done }) })), editing && !nextOf && (_jsx(Modal, { title: `${editing.clientName} — ${editing.lender} ${editing.loanType}`, onClose: () => setPanel(null), children: canEdit && editing.status === 'ACTIVE' ? (_jsxs(_Fragment, { children: [editing.loanClosed && _jsx("p", { className: "muted", children: "This loan is closed: untick \u201CLoan closed\u201D below to move it back to the open loans." }), _jsxs("p", { hidden: editing.loanClosed, children: [_jsx("button", { type: "button", className: "secondary", onClick: () => {
                                        setNextOf(editing);
                                        setPanel(null);
                                    }, children: "Next payout for this loan\u2026" }), ' ', _jsx("span", { className: "muted", children: "e.g. the next quarter\u2019s LAS trail: a new entry with the same client, lender and amounts." })] }), _jsx(LoanForm, { month: thisMonth(), entry: editing, onDone: done })] })) : (_jsx(LoanFacts, { entry: editing })) }))] }));
}
/** One section of the Loans list (open or closed), numbered as the register does, with its totals. */
function LoanTable({ rows, onOpen }) {
    const active = rows.filter((e) => e.status === 'ACTIVE');
    const total = (k) => active.reduce((s, e) => s + e[k], 0);
    const view = useTableView(rows, { month: (e) => e.month, region: (e) => e.region, client: (e) => e.clientName, rm: (e) => e.rmName, lender: (e) => e.lender, loan_type: (e) => e.loanType, invt_date: (e) => e.disbursalDate, loan_sanctioned: (e) => e.sanctionedAmount, payout: (e) => e.payoutPercent, without_gst: (e) => e.gross, net_revenue: (e) => e.net, invoice: (e) => e.invoice, payment_status: (e) => e.paymentStatus });
    return (_jsxs(_Fragment, { children: [_jsx("div", { className: "table-scroll", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { className: "num", children: "SR .NO" }), view.th('month', 'Month'), view.th('region', 'Region'), view.th('client', 'Client'), view.th('rm', 'RM'), view.th('lender', 'Lender'), view.th('loan_type', 'Loan type'), view.th('invt_date', 'Invt date'), view.th('loan_sanctioned', 'Loan / sanctioned', 'num'), view.th('payout', 'Payout %', 'num'), view.th('without_gst', 'Without GST', 'num'), view.th('net_revenue', 'Net revenue', 'num'), view.th('invoice', 'Invoice', 'num'), view.th('payment_status', 'Payment status')] }) }), _jsxs("tbody", { children: [view.rows.map((e, i) => (_jsxs("tr", { className: e.status === 'CANCELLED' ? 'muted' : undefined, children: [_jsx("td", { className: "num", children: i + 1 }), _jsx("td", { children: formatMonth(e.month) }), _jsx("td", { children: e.region ?? _jsx("span", { className: "muted", children: "\u2014" }) }), _jsxs("td", { children: [_jsx("button", { type: "button", className: "cell-link", onClick: () => onOpen(e.id), children: e.clientName }), e.status === 'CANCELLED' && _jsx("span", { className: "badge", children: "cancelled" }), e.partnerName && _jsx("div", { className: "muted", children: e.partnerName })] }), _jsx("td", { children: e.rmName }), _jsx("td", { children: e.lender }), _jsxs("td", { children: [e.loanType, e.payoutKind === 'TRAIL' && _jsx("span", { className: "badge", children: "trail" })] }), _jsx("td", { children: formatDate(e.disbursalDate) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.sanctionedAmount }) }), _jsx("td", { className: "num", children: e.payoutPercent ?? '' }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.gross }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.net }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.invoice }) }), _jsxs("td", { children: [_jsx("span", { className: e.paymentStatus === 'RECEIVED' ? 'badge ok' : e.loanClosed ? 'badge' : 'badge warn', children: paymentText(e) }), e.receivedOn && _jsxs("div", { className: "muted", children: ["on ", formatDate(e.receivedOn)] })] })] }, e.id))), _jsxs("tr", { className: "total", children: [_jsxs("td", { colSpan: 8, children: ["Total (", active.length, " payouts)"] }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('sanctionedAmount') }) }), _jsx("td", {}), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('gross') }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('net') }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('invoice') }) }), _jsx("td", {})] })] })] }) }), view.more] }));
}
function LoanFacts({ entry: e }) {
    return (_jsxs("dl", { className: "facts", children: [_jsx("dt", { children: "Client" }), _jsxs("dd", { children: [e.clientName, " ", _jsx("span", { className: "mono", children: e.pan })] }), _jsx("dt", { children: "Loan" }), _jsxs("dd", { children: [e.lender, " \u00B7 ", e.loanType, " \u00B7 ", formatMoney(e.sanctionedAmount)] }), _jsx("dt", { children: "Without GST / Net / Invoice" }), _jsxs("dd", { children: [formatMoney(e.gross), " / ", formatMoney(e.net), " / ", formatMoney(e.invoice)] }), _jsx("dt", { children: "Status" }), _jsx("dd", { children: e.status === 'CANCELLED' ? 'Cancelled' : paymentText(e) })] }));
}
/**
 * Add or edit one loan payout, in the LOANS register's order. {@code copyOf}: a new payout for the same loan (the next
 * quarter's trail): same client, RM, partner, lender, type and amounts, three months on, as a trail on the utilised amount.
 */
function LoanForm({ month: listMonth, entry, copyOf, onDone }) {
    const src = entry ?? copyOf;
    const party = useClientAndRm(src, entry ? entry.month : copyOf ? monthPlus(copyOf.month, 3) : listMonth);
    const { month, monthDay, clientMode } = party;
    const [lender, setLender] = useState(src?.lender ?? '');
    const [loanType, setLoanType] = useState(src?.loanType ?? '');
    const [disbursalDate, setDisbursalDate] = useState(src?.disbursalDate ?? '');
    const [sanctioned, setSanctioned] = useState(src ? indianDigits(String(src.sanctionedAmount)) : '');
    const [utilised, setUtilised] = useState(src?.utilisedAmount != null ? indianDigits(String(src.utilisedAmount)) : '');
    const [rate, setRate] = useState(src?.ratePercent != null ? String(src.ratePercent) : '');
    const [kind, setKind] = useState(entry ? entry.payoutKind : copyOf ? 'TRAIL' : 'DISBURSAL');
    const [on, setOn] = useState(entry ? entry.payoutOn : copyOf ? 'UTILISED' : 'SANCTIONED');
    const [payoutPct, setPayoutPct] = useState(entry?.payoutPercent != null ? String(entry.payoutPercent) : copyOf?.payoutKind === 'TRAIL' && copyOf.payoutPercent != null ? String(copyOf.payoutPercent) : '');
    const [payoutTouched, setPayoutTouched] = useState(!!entry);
    const [fixed, setFixed] = useState(entry?.fixedAmount != null ? String(entry.fixedAmount) : '');
    const [tdsApplied, setTdsApplied] = useState(entry?.tdsApplied ?? true);
    const [tdsPct, setTdsPct] = useState(entry ? String(entry.tdsPercent) : '');
    const [invoiceRaised, setInvoiceRaised] = useState(entry?.invoiceRaised ?? false);
    const [payment, setPayment] = useState(entry?.paymentStatus ?? 'PENDING');
    const [paymentNote, setPaymentNote] = useState(entry?.paymentNote ?? '');
    const [receivedOn, setReceivedOn] = useState(entry?.receivedOn ?? '');
    const [loanClosed, setLoanClosed] = useState(entry?.loanClosed ?? false);
    const [remarks, setRemarks] = useState(entry?.remarks ?? '');
    const { busy, error, run } = useSubmit();
    const suggestions = useLoad('/api/loans/suggestions');
    const tax = useLoad(`/api/product-taxes/LOAN/on?date=${monthDay}`);
    // A new payout starts with the month's Loans TDS % (5% until Sep 2024, 2% after); it can be changed for this entry.
    useEffect(() => {
        if (!entry && tax.data) {
            setTdsApplied(tax.data.tdsByDefault || tax.data.tdsPercent > 0);
            setTdsPct(String(tax.data.tdsPercent));
        }
    }, [entry, tax.data]);
    const num = (s) => Number(s.replace(/,/g, '')) || 0;
    const gstPct = entry && entry.month === month.slice(0, 7) ? entry.gstPercent : (tax.data?.gstPercent ?? 0);
    const [given, setGiven] = useGiven(entry);
    const base = on === 'UTILISED' ? num(utilised) : num(sanctioned);
    const worked = loanRevenue(base, num(payoutPct), num(fixed), tdsApplied, num(tdsPct), gstPct);
    const rev = shownFigures(worked, given);
    // Lender + type + kind known → the payout % last used for them, until it is typed by hand.
    const lastPayout = suggestions.data?.payouts.find((p) => p.lender.toLowerCase() === lender.trim().toLowerCase() && p.loanType.toLowerCase() === loanType.trim().toLowerCase() && p.payoutKind === kind);
    useEffect(() => {
        if (!payoutTouched && lastPayout) {
            setPayoutPct(String(lastPayout.payoutPercent));
            setOn(lastPayout.payoutOn);
        }
    }, [lastPayout, payoutTouched]);
    async function submit(e) {
        e.preventDefault();
        const body = {
            ...party.body(),
            lender,
            loanType,
            disbursalDate: disbursalDate || null,
            sanctionedAmount: sanctioned === '' ? null : num(sanctioned),
            utilisedAmount: utilised === '' ? null : num(utilised),
            ratePercent: rate === '' ? null : num(rate),
            payoutKind: kind,
            payoutOn: on,
            payoutPercent: payoutPct === '' ? null : num(payoutPct),
            fixedAmount: fixed === '' ? null : num(fixed),
            tdsApplied,
            tdsPercent: tdsApplied ? num(tdsPct) : 0,
            invoiceRaised,
            paymentStatus: payment,
            paymentNote,
            receivedOn: payment === 'RECEIVED' && receivedOn ? receivedOn : null,
            loanClosed,
            remarks,
            ...givenBody(given),
        };
        const ok = await run(() => (entry ? api(`/api/loans/${entry.id}`, { method: 'PUT', body, ifMatch: entry.updatedAt }) : api('/api/loans', { method: 'POST', body })));
        if (ok)
            onDone();
    }
    return (_jsxs("form", { onSubmit: submit, className: "fd-form", children: [tax.error && _jsx("p", { className: "error", children: tax.error }), _jsx(ClientFieldset, { party: party, radioName: "loan-client" }), _jsx(MonthRmFieldset, { party: party, partnerLabel: "Referral partner / sub-broker" }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Loan" }), _jsxs("label", { children: ["Partner (lender)", _jsx("input", { value: lender, onChange: (e) => setLender(e.target.value), list: "loan-lenders", placeholder: "e.g. TATA", required: true })] }), _jsx("datalist", { id: "loan-lenders", children: (suggestions.data?.lenders ?? []).map((n) => (_jsx("option", { value: n }, n))) }), _jsxs("label", { children: ["Loan type", _jsx("input", { value: loanType, onChange: (e) => setLoanType(e.target.value), list: "loan-types", placeholder: "Home Loan, LAS, Personal Loan\u2026", required: true })] }), _jsx("datalist", { id: "loan-types", children: ['Home Loan', 'LAS', 'Personal Loan', 'Business Loan', 'Loan Against Property', ...(suggestions.data?.loanTypes ?? [])]
                            .filter((v, i, a) => a.findIndex((x) => x.toLowerCase() === v.toLowerCase()) === i)
                            .map((n) => (_jsx("option", { value: n }, n))) }), _jsxs("label", { children: ["Invt date (disbursal)", _jsx("input", { type: "date", value: disbursalDate, onChange: (e) => setDisbursalDate(e.target.value), required: true })] }), _jsxs("label", { children: ["Loan amt / sanctioned amt (\u20B9)", _jsx("input", { value: sanctioned, onChange: (e) => setSanctioned(indianDigits(e.target.value)), inputMode: "decimal", required: true })] }), _jsxs("label", { children: ["Utilised amt (\u20B9)", _jsx("input", { value: utilised, onChange: (e) => setUtilised(indianDigits(e.target.value)), inputMode: "decimal", required: on === 'UTILISED' })] }), _jsxs("label", { children: ["Rate of interest %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: rate, onChange: (e) => setRate(e.target.value) })] })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Revenue" }), _jsx(GivenFiguresFields, { value: given, onChange: setGiven }), _jsxs("label", { children: ["Payout", _jsxs("select", { value: kind, onChange: (e) => setKind(e.target.value), children: [_jsx("option", { value: "DISBURSAL", children: "On disbursal (one-time)" }), _jsx("option", { value: "TRAIL", children: "Trail (e.g. LAS, each quarter)" })] })] }), _jsxs("label", { children: ["Revenue sharing % (payout %)", _jsx("input", { type: "number", min: 0, max: 100, step: "0.001", value: payoutPct, onChange: (e) => {
                                    setPayoutTouched(true);
                                    setPayoutPct(e.target.value);
                                } })] }), _jsxs("label", { children: ["On", _jsxs("select", { value: on, onChange: (e) => setOn(e.target.value), children: [_jsx("option", { value: "SANCTIONED", children: "Loan / sanctioned amount" }), _jsx("option", { value: "UTILISED", children: "Utilised amount" })] })] }), _jsxs("label", { children: ["Fixed amount (\u20B9, optional)", _jsx("input", { type: "number", min: 0, step: "0.01", value: fixed, onChange: (e) => setFixed(e.target.value), placeholder: "added to the payout, or the whole payout when there is no %" })] }), lastPayout && (_jsxs("p", { className: "muted", children: ["Last used for ", lastPayout.lender, " \u00B7 ", lastPayout.loanType, ": ", lastPayout.payoutPercent, "% on the ", lastPayout.payoutOn === 'UTILISED' ? 'utilised' : 'sanctioned', " amount"] })), _jsxs("span", { className: "inline-form", children: [_jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: tdsApplied, onChange: (e) => setTdsApplied(e.target.checked) }), "TDS"] }), tdsApplied && (_jsxs("label", { children: ["TDS %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: tdsPct, onChange: (e) => setTdsPct(e.target.value), "aria-label": "TDS %", required: true })] }))] }), _jsxs("dl", { className: "facts", children: [_jsx("dt", { children: "Without GST" }), _jsx("dd", { children: formatMoney(rev.gross) }), _jsx("dt", { children: "TDS" }), _jsx("dd", { children: formatMoney(rev.tds) }), _jsxs("dt", { children: ["GST (", gstPct, "%)"] }), _jsx("dd", { children: formatMoney(rev.gst) }), _jsx("dt", { children: "Net revenue" }), _jsx("dd", { children: _jsx("strong", { children: formatMoney(rev.net) }) }), _jsx("dt", { children: "Invoice amount" }), _jsx("dd", { children: formatMoney(rev.invoice) })] })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Payment" }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: invoiceRaised, onChange: (e) => setInvoiceRaised(e.target.checked) }), "Invoice raised by accounts"] }), _jsxs("label", { children: ["Payment status", _jsx("select", { value: payment, onChange: (e) => setPayment(e.target.value), children: Object.entries(LOAN_PAYMENT_LABEL).map(([k, v]) => (_jsx("option", { value: k, children: v }, k))) })] }), payment === 'RECEIVED' && (_jsxs("label", { children: ["Amt received date", _jsx("input", { type: "date", value: receivedOn, onChange: (e) => setReceivedOn(e.target.value) })] })), _jsxs("label", { children: ["Payment note (as in the register)", _jsx("input", { value: paymentNote, onChange: (e) => setPaymentNote(e.target.value), placeholder: "e.g. Invoice will be raised in Oct" })] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: loanClosed, onChange: (e) => setLoanClosed(e.target.checked) }), "Loan closed"] }), _jsxs("label", { children: ["Remarks", _jsx("textarea", { value: remarks, onChange: (e) => setRemarks(e.target.value), rows: 2, placeholder: "e.g. Quarterly 0.25% on utilised amt on trail basis" })] })] }), error && _jsx("p", { className: "error", children: error }), _jsxs("span", { className: "inline-form", children: [_jsx("button", { type: "submit", disabled: busy || clientMode === 'lookup' || clientMode === 'pick', children: entry ? 'Save changes' : 'Add loan payout' }), entry && _jsx(CancelEntry, { url: `/api/loans/${entry.id}/cancel`, question: "Why cancel this loan entry?", onDone: onDone })] })] }));
}
