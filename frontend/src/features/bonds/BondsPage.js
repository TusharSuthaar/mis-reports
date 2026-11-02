import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { CancelEntry, ClientFieldset, MonthRmFieldset, useClientAndRm } from '../entry/ClientAndRm';
import { useEffect, useState } from 'react';
import { useTableView } from '../../components/useTableView';
import { api } from '../../api/client';
import { BOND_PAYOUT_LABEL, PAYMENT_LABEL, } from '../../api/types';
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
/** The brokerage as the server works it out (R-76), for the live preview while typing. */
export function bondRevenue(amount, markup, share, gstInclusive, gstPct, tdsApplied, tdsPct) {
    const brokerage = round2((amount * markup * share) / 10000);
    const gross = gstInclusive ? round2((brokerage * 100) / (100 + gstPct)) : brokerage;
    const gst = round2((gross * gstPct) / 100);
    const tds = tdsApplied ? round2((gross * tdsPct) / 100) : 0;
    return { brokerage, gross, gst, tds, net: round2(gross - tds), invoice: round2(gross + gst) };
}
const thisMonth = () => todayIso().slice(0, 7);
/** Bonds: one row per bond deal, entered by hand; brokerage from the mark-up and the platform's terms. */
export function BondsPage({ embedded = false } = {}) {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const month = thisMonth(); // the month a new deal starts in
    const query = useFilterQuery();
    const list = useLoad(listPath('/api/bonds', query));
    const platforms = useLoad('/api/platforms');
    const [panel, setPanel] = useEntryPanel();
    const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
    const active = (list.data ?? []).filter((e) => e.status === 'ACTIVE');
    const total = (k) => active.reduce((s, e) => s + e[k], 0);
    const done = () => {
        setPanel(null);
        list.reload();
    };
    const view = useTableView(list.data, { month: (e) => e.month, inv_date: (e) => e.investmentDate, client: (e) => e.clientName, bond: (e) => e.bondName, platform: (e) => e.platformName, rm: (e) => e.rmName, consideration: (e) => e.consideration, gross: (e) => e.gross, net: (e) => e.net, invoice: (e) => e.invoice, payment: (e) => e.paymentStatus });
    return (_jsxs("section", { children: [_jsxs("div", { className: "page-head", style: embedded ? { justifyContent: 'flex-end', marginBottom: 12 } : undefined, children: [!embedded && _jsx("h1", { children: "Bonds" }), canEdit && (_jsxs("div", { className: "actions", children: [_jsx("a", { className: "button-link secondary", href: `/api/bonds/export.xlsx${query ?? ''}`, download: true, children: "Download Excel" }), _jsx("button", { type: "button", onClick: () => setPanel('new'), children: "Add bond" })] }))] }), _jsx(ListFilters, { searchHint: "Client, PAN, code, bond name or ISIN", platformKey: "platformId", platforms: (platforms.data ?? []).map((p) => ({ value: String(p.id), label: p.name })) }), list.error && _jsx("p", { className: "error", children: list.error }), list.data && list.data.length === 0 && _jsx("p", { className: "muted", children: "No bond deals match." }), list.data && list.data.length > 0 && (_jsxs(_Fragment, { children: [_jsx("div", { className: "table-scroll", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { className: "num", children: "SR.NO" }), view.th('month', 'Month'), view.th('inv_date', 'Inv date'), view.th('client', 'Client'), view.th('bond', 'Bond'), view.th('platform', 'Platform'), view.th('rm', 'RM'), view.th('consideration', 'Consideration', 'num'), view.th('gross', 'Gross', 'num'), view.th('net', 'Net', 'num'), view.th('invoice', 'Invoice', 'num'), view.th('payment', 'Payment')] }) }), _jsxs("tbody", { children: [view.rows.map((e, i) => (_jsxs("tr", { className: e.status === 'CANCELLED' ? 'muted' : undefined, children: [_jsx("td", { className: "num", children: i + 1 }), _jsx("td", { children: formatMonth(e.month) }), _jsx("td", { children: formatDate(e.investmentDate) }), _jsxs("td", { children: [_jsx("button", { type: "button", className: "cell-link", onClick: () => setPanel(e.id), children: e.clientName }), e.status === 'CANCELLED' && _jsx("span", { className: "badge", children: "cancelled" })] }), _jsxs("td", { children: [e.bondName, e.isin ? _jsxs("span", { className: "muted mono", children: [" ", e.isin] }) : null] }), _jsx("td", { children: e.platformName }), _jsx("td", { children: e.rmName }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.consideration }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.gross }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.net }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.invoice }) }), _jsx("td", { children: e.paymentStatus === 'RECEIVED' ? PAYMENT_LABEL[e.paymentStatus] : _jsx("span", { className: "error", children: PAYMENT_LABEL[e.paymentStatus] }) })] }, e.id))), _jsxs("tr", { className: "total", children: [_jsxs("td", { colSpan: 7, children: ["Total (", active.length, " deals)"] }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('consideration') }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('gross') }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('net') }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('invoice') }) }), _jsx("td", {})] })] })] }) }), view.more] })), panel === 'new' && (_jsx(Modal, { title: "Add bond", onClose: () => setPanel(null), children: _jsx(BondForm, { month: month, onDone: done }) })), editing && (_jsx(Modal, { title: `${editing.clientName} — ${editing.bondName}`, onClose: () => setPanel(null), children: canEdit && editing.status === 'ACTIVE' ? (_jsx(BondForm, { month: month, entry: editing, onDone: done })) : (_jsxs("p", { children: [editing.bondName, " \u00B7 ", formatMoney(editing.consideration), " \u00B7 ", editing.status === 'CANCELLED' ? 'Cancelled' : PAYMENT_LABEL[editing.paymentStatus]] })) }))] }));
}
/** Add or edit one bond deal, laid out as in the bond register. */
function BondForm({ month: listMonth, entry, onDone }) {
    const num = (s) => Number(s.replace(/,/g, '')) || 0; // keeps a leading minus (accrued interest can be negative)
    const str = (n) => (n == null ? '' : String(n));
    const party = useClientAndRm(entry, entry ? entry.month : listMonth);
    const { month, monthDay, clientMode } = party;
    const [platformId, setPlatformId] = useState(entry ? String(entry.platformId) : '');
    const [bondName, setBondName] = useState(entry?.bondName ?? '');
    const [isin, setIsin] = useState(entry?.isin ?? '');
    const [coupon, setCoupon] = useState(str(entry?.couponPercent));
    const [ytm, setYtm] = useState(str(entry?.ytmPercent));
    const [payout, setPayout] = useState(entry?.payoutFrequency ?? '');
    const [schedule, setSchedule] = useState(entry?.paymentSchedule ?? '');
    const [invDate, setInvDate] = useState(entry?.investmentDate ?? '');
    const [allotDate, setAllotDate] = useState(entry?.allotmentDate ?? '');
    const [maturityDate, setMaturityDate] = useState(entry?.maturityDate ?? '');
    const [qty, setQty] = useState(str(entry?.quantity));
    const [rate, setRate] = useState(entry?.ratePerUnit != null ? indianDigits(String(entry.ratePerUnit)) : '');
    const [faceValue, setFaceValue] = useState(entry?.faceValue != null ? indianDigits(String(entry.faceValue)) : '');
    const [amount, setAmount] = useState(entry ? indianDigits(String(entry.investmentAmount)) : '');
    const [amountTouched, setAmountTouched] = useState(!!entry);
    const [accrued, setAccrued] = useState(entry ? indianDigits(String(entry.accruedInterest)) : '');
    const [markup, setMarkup] = useState(str(entry?.markupPercent));
    const [markupTouched, setMarkupTouched] = useState(!!entry);
    const [tdsApplied, setTdsApplied] = useState(entry?.tdsApplied ?? true);
    const [tdsPct, setTdsPct] = useState(str(entry?.tdsPercent));
    const [invoiceRaised, setInvoiceRaised] = useState(entry?.invoiceRaised ?? false);
    const [invoiceDate, setInvoiceDate] = useState(entry?.invoiceDate ?? '');
    const [receivedOn, setReceivedOn] = useState(entry?.paymentReceivedOn ?? '');
    const [payment, setPayment] = useState(entry?.paymentStatus ?? 'PENDING');
    const [onPortal, setOnPortal] = useState(entry?.onPortal ?? false);
    const [remarks, setRemarks] = useState(entry?.remarks ?? '');
    const { busy, error, run } = useSubmit();
    const platforms = useLoad('/api/platforms');
    const suggestions = useLoad('/api/bonds/suggestions');
    const tax = useLoad(`/api/product-taxes/BOND/on?date=${monthDay}`);
    const terms = useLoad(platformId ? `/api/platforms/${platformId}/terms/on?date=${monthDay}` : null);
    // A new deal takes the platform's TDS %.
    useEffect(() => {
        if (!entry && terms.data) {
            setTdsPct(String(terms.data.tdsPercent));
            setTdsApplied(terms.data.tdsPercent > 0);
        }
    }, [entry, terms.data]);
    // Qty × rate → investment amount, until the amount is typed by hand.
    useEffect(() => {
        if (!amountTouched && num(qty) > 0 && num(rate) > 0)
            setAmount(indianDigits(String(round2(num(qty) * num(rate)))));
    }, [qty, rate, amountTouched]);
    // Bond name known → its ISIN; platform + ISIN (or name) known → the mark-up last used.
    useEffect(() => {
        if (!isin && bondName) {
            const hit = suggestions.data?.isins.find((x) => x.bondName.toLowerCase() === bondName.trim().toLowerCase());
            if (hit)
                setIsin(hit.isin);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [bondName, suggestions.data]);
    const lastMarkup = suggestions.data?.markups.find((m) => String(m.platformId) === platformId &&
        ((isin && m.isin?.toUpperCase() === isin.trim().toUpperCase()) || m.bondName.toLowerCase() === bondName.trim().toLowerCase()));
    useEffect(() => {
        if (!markupTouched && lastMarkup)
            setMarkup(String(lastMarkup.markupPercent));
    }, [lastMarkup, markupTouched]);
    const share = entry && entry.month === month.slice(0, 7) ? entry.ourShare : (terms.data?.ourShare ?? 100);
    const inclusive = entry && entry.month === month.slice(0, 7) ? entry.gstInclusive : (terms.data?.gstInclusive ?? false);
    const gstPct = entry && entry.month === month.slice(0, 7) ? entry.gstPercent : (tax.data?.gstPercent ?? 0);
    const [given, setGiven] = useGiven(entry);
    const worked = bondRevenue(num(amount), num(markup), share, inclusive, gstPct, tdsApplied, num(tdsPct));
    const rev = shownFigures(worked, given);
    const consideration = round2(num(amount) + num(accrued));
    async function submit(e) {
        e.preventDefault();
        const n = (s) => (s === '' ? null : num(s));
        const body = {
            ...party.body(),
            platformId: platformId ? Number(platformId) : null,
            bondName,
            isin,
            couponPercent: n(coupon),
            ytmPercent: n(ytm),
            payoutFrequency: payout || null,
            paymentSchedule: schedule,
            investmentDate: invDate || null,
            allotmentDate: allotDate || null,
            maturityDate: maturityDate || null,
            quantity: n(qty),
            ratePerUnit: n(rate),
            faceValue: n(faceValue),
            investmentAmount: n(amount),
            accruedInterest: n(accrued) ?? 0,
            markupPercent: n(markup),
            tdsApplied,
            tdsPercent: tdsApplied ? num(tdsPct) : 0,
            invoiceRaised,
            invoiceDate: invoiceDate || null,
            paymentReceivedOn: receivedOn || null,
            paymentStatus: payment,
            onPortal,
            remarks,
            ...givenBody(given),
        };
        const ok = await run(() => (entry ? api(`/api/bonds/${entry.id}`, { method: 'PUT', body, ifMatch: entry.updatedAt }) : api('/api/bonds', { method: 'POST', body })));
        if (ok)
            onDone();
    }
    return (_jsxs("form", { onSubmit: submit, className: "fd-form", children: [tax.error && _jsx("p", { className: "error", children: tax.error }), terms.error && _jsx("p", { className: "error", children: terms.error }), _jsx(ClientFieldset, { party: party, radioName: "bond-client", mobileLabel: "Mobile", emailLabel: "Email", codeHint: "EM1234", showClientType: true }), _jsx(MonthRmFieldset, { party: party, partnerLabel: "Referral / sub-broker" }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Bond" }), _jsxs("label", { children: ["Partner (platform)", _jsxs("select", { value: platformId, onChange: (e) => setPlatformId(e.target.value), required: true, children: [_jsx("option", { value: "", children: "Choose\u2026" }), (platforms.data ?? []).map((p) => (_jsx("option", { value: p.id, children: p.name }, p.id)))] })] }), _jsxs("label", { children: ["Bond name", _jsx("input", { value: bondName, onChange: (e) => setBondName(e.target.value), list: "bond-names", required: true })] }), _jsx("datalist", { id: "bond-names", children: (suggestions.data?.bondNames ?? []).map((n) => (_jsx("option", { value: n }, n))) }), _jsxs("label", { children: ["ISIN", _jsx("input", { value: isin, onChange: (e) => setIsin(e.target.value.toUpperCase()) })] }), _jsxs("label", { children: ["Coupon rate %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.0001", value: coupon, onChange: (e) => setCoupon(e.target.value) })] }), _jsxs("label", { children: ["YTM %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.0001", value: ytm, onChange: (e) => setYtm(e.target.value) })] }), _jsxs("label", { children: ["Interest payout frequency", _jsxs("select", { value: payout, onChange: (e) => setPayout(e.target.value), children: [_jsx("option", { value: "", children: "\u2014" }), Object.entries(BOND_PAYOUT_LABEL).map(([k, v]) => (_jsx("option", { value: k, children: v }, k)))] })] }), _jsxs("label", { children: ["Interest payment schedule", _jsx("input", { value: schedule, onChange: (e) => setSchedule(e.target.value), placeholder: "e.g. 10th of every month" })] }), _jsxs("label", { children: ["Investment date", _jsx("input", { type: "date", value: invDate, onChange: (e) => setInvDate(e.target.value), required: true })] }), _jsxs("label", { children: ["Date of allotment", _jsx("input", { type: "date", value: allotDate, onChange: (e) => setAllotDate(e.target.value) })] }), _jsxs("label", { children: ["Maturity / sell date", _jsx("input", { type: "date", value: maturityDate, onChange: (e) => setMaturityDate(e.target.value) })] })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Amounts" }), _jsxs("label", { children: ["Quantity", _jsx("input", { type: "number", min: 0, step: "any", value: qty, onChange: (e) => setQty(e.target.value), required: true })] }), _jsxs("label", { children: ["Purchase rate per unit (\u20B9)", _jsx("input", { value: rate, onChange: (e) => setRate(indianDigits(e.target.value)), inputMode: "decimal" })] }), _jsxs("label", { children: ["Face value (\u20B9)", _jsx("input", { value: faceValue, onChange: (e) => setFaceValue(indianDigits(e.target.value)), inputMode: "decimal" })] }), _jsxs("label", { children: ["Investment amount (\u20B9)", _jsx("input", { value: amount, onChange: (e) => {
                                    setAmountTouched(true);
                                    setAmount(indianDigits(e.target.value));
                                }, inputMode: "decimal", required: true })] }), _jsxs("label", { children: ["Accrued interest (\u20B9)", _jsx("input", { value: accrued, onChange: (e) => setAccrued((e.target.value.trim().startsWith('-') ? '-' : '') + indianDigits(e.target.value)), inputMode: "decimal", title: "Negative when the deal is between the record date and the coupon date" })] }), _jsxs("label", { children: ["Consideration (\u20B9)", _jsx("input", { value: consideration ? formatMoney(consideration) : '', readOnly: true })] })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Brokerage" }), _jsx(GivenFiguresFields, { value: given, onChange: setGiven }), _jsxs("label", { children: ["Mark-up %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.0001", value: markup, onChange: (e) => {
                                    setMarkupTouched(true);
                                    setMarkup(e.target.value);
                                }, required: true })] }), _jsxs("span", { className: "inline-form", children: [_jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: tdsApplied, onChange: (e) => setTdsApplied(e.target.checked) }), "TDS"] }), tdsApplied && (_jsxs("label", { children: ["TDS %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: tdsPct, onChange: (e) => setTdsPct(e.target.value), "aria-label": "TDS %", required: true })] }))] }), lastMarkup && _jsxs("p", { className: "muted", children: ["Mark-up last used for this bond on this platform: ", lastMarkup.markupPercent, "%"] }), _jsx("p", { className: "muted", children: platformId ? `Platform terms: brokerage ${inclusive ? 'includes GST' : 'plus GST'}, our share ${share}%` : 'Choose the platform to see its terms.' }), _jsxs("dl", { className: "facts", children: [_jsx("dt", { children: "Brokerage" }), _jsx("dd", { children: formatMoney(rev.brokerage) }), _jsx("dt", { children: "Gross revenue" }), _jsx("dd", { children: formatMoney(rev.gross) }), _jsx("dt", { children: "TDS" }), _jsx("dd", { children: formatMoney(rev.tds) }), _jsxs("dt", { children: ["GST (", gstPct, "%)"] }), _jsx("dd", { children: formatMoney(rev.gst) }), _jsx("dt", { children: "Net revenue" }), _jsx("dd", { children: _jsx("strong", { children: formatMoney(rev.net) }) }), _jsx("dt", { children: "Invoice amount" }), _jsx("dd", { children: formatMoney(rev.invoice) })] })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Follow-up" }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: invoiceRaised, onChange: (e) => setInvoiceRaised(e.target.checked) }), "Invoice raised by accounts"] }), _jsxs("label", { children: ["Invoice raised on", _jsx("input", { type: "date", value: invoiceDate, onChange: (e) => setInvoiceDate(e.target.value) })] }), _jsxs("label", { children: ["Amount received on", _jsx("input", { type: "date", value: receivedOn, onChange: (e) => setReceivedOn(e.target.value) })] }), _jsxs("label", { children: ["Payment status", _jsx("select", { value: payment, onChange: (e) => setPayment(e.target.value), children: Object.entries(PAYMENT_LABEL).map(([k, v]) => (_jsx("option", { value: k, children: v }, k))) })] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: onPortal, onChange: (e) => setOnPortal(e.target.checked) }), "Updated on Epsilon portal"] }), _jsxs("label", { children: ["Remarks", _jsx("textarea", { value: remarks, onChange: (e) => setRemarks(e.target.value), rows: 2 })] })] }), error && _jsx("p", { className: "error", children: error }), _jsxs("span", { className: "inline-form", children: [_jsx("button", { type: "submit", disabled: busy || clientMode === 'lookup' || clientMode === 'pick', children: entry ? 'Save changes' : 'Add bond' }), entry && _jsx(CancelEntry, { url: `/api/bonds/${entry.id}/cancel`, question: "Why cancel this bond entry?", label: "Cancel this bond", onDone: onDone })] })] }));
}
