import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { CancelEntry, ClientFieldset, MonthRmFieldset, useClientAndRm } from '../entry/ClientAndRm';
import { useEffect, useState } from 'react';
import { useTableView } from '../../components/useTableView';
import { api } from '../../api/client';
import { GIFT_PAYMENT_LABEL, } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { Amount } from '../../components/Amount';
import { ListFilters, listPath, useFilterQuery } from '../../components/ListFilters';
import { Modal } from '../../components/Modal';
import { useEntryPanel } from '../../components/useEntryPanel';
import { formatMoney, todayIso, formatMonth, indianDigits } from '../../format';
const round2 = (n) => Math.round((n + Number.EPSILON) * 100) / 100;
/** The rupee amount and revenue as the server works them out (R-82), for the live preview while typing. */
export function giftFigures(usd, rate, inrTyped, brokerage, tdsApplied, tdsPct, gstPct) {
    const inr = usd > 0 && rate > 0 ? round2(usd * rate) : inrTyped;
    const gross = round2(brokerage);
    const tds = tdsApplied ? round2((gross * tdsPct) / 100) : 0;
    const gst = round2((gross * gstPct) / 100);
    return { inr, gross, tds, gst, net: round2(gross - tds), invoice: round2(gross + gst) };
}
/** The day a new entry's rate is for: today in the current month, else the month's last day. */
export function defaultRateDay(month, today) {
    const ym = month.slice(0, 7);
    if (today.slice(0, 7) === ym)
        return today;
    const [y, m] = ym.split('-').map(Number);
    const last = new Date(y, m, 0).getDate();
    return `${ym}-${String(last).padStart(2, '0')}`;
}
const thisMonth = () => todayIso().slice(0, 7);
const usd = (n) => (n == null ? '' : `USD ${n.toLocaleString('en-IN')}`);
/** GIFT CITY: one row per investment, as in the GIFT CITY register; brokerage typed for now. */
export function GiftPage({ embedded = false } = {}) {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const query = useFilterQuery();
    const list = useLoad(listPath('/api/gift', query));
    const funds = useLoad('/api/gift/funds');
    const [panel, setPanel] = useEntryPanel();
    const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
    const active = (list.data ?? []).filter((e) => e.status === 'ACTIVE');
    const total = (k) => active.reduce((s, e) => s + (e[k] ?? 0), 0);
    const done = () => {
        setPanel(null);
        list.reload();
        funds.reload();
    };
    const view = useTableView(list.data, { month: (e) => e.month, region: (e) => e.region, client: (e) => e.clientName, rm: (e) => e.rmName, partner_fund: (e) => e.partnerName, amt: (e) => e.amountUsd, amt_in_rs: (e) => e.amountInr, trxn_status: (e) => e.trxnStatus, brokerage: (e) => e.brokerage, invoice: (e) => e.invoice, payment: (e) => e.paymentStatus });
    return (_jsxs("section", { children: [_jsxs("div", { className: "page-head", style: embedded ? { justifyContent: 'flex-end', marginBottom: 12 } : undefined, children: [!embedded && _jsx("h1", { children: "GIFT CITY" }), canEdit && (_jsxs("div", { className: "actions", children: [_jsx("a", { className: "button-link secondary", href: `/api/gift/export.xlsx${query ?? ''}`, download: true, children: "Download Excel" }), _jsx("button", { type: "button", onClick: () => setPanel('new'), children: "Add GIFT City entry" })] }))] }), _jsx(ListFilters, { searchHint: "Client, PAN, code or fund", platformKey: "fund", platformLabel: "Fund", platforms: (funds.data ?? []).map((p) => ({ value: p, label: p })), payments: GIFT_PAYMENT_LABEL }), list.error && _jsx("p", { className: "error", children: list.error }), list.data && list.data.length === 0 && _jsx("p", { className: "muted", children: "No GIFT City entries match." }), list.data && list.data.length > 0 && (_jsxs(_Fragment, { children: [_jsx("div", { className: "table-scroll", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { className: "num", children: "SR .NO" }), view.th('month', 'Month'), view.th('region', 'Region'), view.th('client', 'Client'), view.th('rm', 'RM'), view.th('partner_fund', 'Partner (fund)'), view.th('amt', 'AMT', 'num'), view.th('amt_in_rs', 'AMT in Rs.', 'num'), _jsx("th", { children: "Entry status" }), view.th('trxn_status', 'Trxn status'), view.th('brokerage', 'Brokerage', 'num'), view.th('invoice', 'Invoice', 'num'), view.th('payment', 'Payment')] }) }), _jsxs("tbody", { children: [view.rows.map((e, i) => (_jsxs("tr", { className: e.status === 'CANCELLED' ? 'muted' : undefined, children: [_jsx("td", { className: "num", children: i + 1 }), _jsx("td", { children: formatMonth(e.month) }), _jsx("td", { children: e.region ?? _jsx("span", { className: "muted", children: "\u2014" }) }), _jsxs("td", { children: [_jsx("button", { type: "button", className: "cell-link", onClick: () => setPanel(e.id), children: e.clientName }), e.status === 'CANCELLED' && _jsx("span", { className: "badge", children: "cancelled" }), e.partnerName && _jsx("div", { className: "muted", children: e.partnerName })] }), _jsx("td", { children: e.rmName }), _jsx("td", { children: e.fund }), _jsx("td", { className: "num", children: usd(e.amountUsd) }), _jsx("td", { className: "num", children: e.amountInr == null ? '' : _jsx(Amount, { value: e.amountInr }) }), _jsx("td", { children: e.onPortal ? _jsx("span", { className: "badge ok", children: "Updated" }) : _jsx("span", { className: "badge warn", children: "Not yet" }) }), _jsx("td", { children: e.trxnStatus === 'DONE' ? _jsx("span", { className: "badge ok", children: "Done" }) : _jsx("span", { className: "badge warn", children: "Pending" }) }), _jsx("td", { className: "num", children: e.brokerage == null ? _jsx("span", { className: "muted", children: "not known" }) : _jsx(Amount, { value: e.gross }) }), _jsx("td", { className: "num", children: e.brokerage == null ? '' : _jsx(Amount, { value: e.invoice }) }), _jsx("td", { children: _jsx("span", { className: e.paymentStatus === 'RECEIVED' ? 'badge ok' : 'badge warn', children: GIFT_PAYMENT_LABEL[e.paymentStatus] }) })] }, e.id))), _jsxs("tr", { className: "total", children: [_jsxs("td", { colSpan: 7, children: ["Total (", active.length, " entries)"] }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('amountInr') }) }), _jsx("td", { colSpan: 2 }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('gross') }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('invoice') }) }), _jsx("td", {})] })] })] }) }), view.more] })), panel === 'new' && (_jsx(Modal, { title: "Add GIFT City entry", onClose: () => setPanel(null), children: _jsx(GiftForm, { month: thisMonth(), onDone: done }) })), editing && (_jsx(Modal, { title: `${editing.clientName} — ${editing.fund}`, onClose: () => setPanel(null), children: canEdit && editing.status === 'ACTIVE' ? (_jsx(GiftForm, { month: thisMonth(), entry: editing, onDone: done })) : (_jsxs("dl", { className: "facts", children: [_jsx("dt", { children: "Fund" }), _jsx("dd", { children: editing.fund }), _jsx("dt", { children: "Amount" }), _jsxs("dd", { children: [usd(editing.amountUsd), " ", editing.amountInr != null && `· ${formatMoney(editing.amountInr)}`] }), _jsx("dt", { children: "Brokerage / Invoice" }), _jsx("dd", { children: editing.brokerage == null ? 'not known yet' : `${formatMoney(editing.gross)} / ${formatMoney(editing.invoice)}` })] })) }))] }));
}
/** Add or edit one GIFT City entry, in the register's order, with a Brokerage section. */
function GiftForm({ month: listMonth, entry, onDone }) {
    const party = useClientAndRm(entry, entry ? entry.month : listMonth);
    const { month, monthDay, clientMode } = party;
    const [fund, setFund] = useState(entry?.fund ?? '');
    const [amountUsd, setAmountUsd] = useState(entry?.amountUsd != null ? indianDigits(String(entry.amountUsd)) : '');
    const [fxRate, setFxRate] = useState(entry?.fxRate != null ? String(entry.fxRate) : '');
    const [rateDay, setRateDay] = useState(entry?.fxRateDate ?? defaultRateDay(entry ? entry.month : listMonth, todayIso()));
    const [fxSource, setFxSource] = useState(entry?.fxSource ?? '');
    const [fxNote, setFxNote] = useState('');
    const [fetching, setFetching] = useState(false);
    const [amountInr, setAmountInr] = useState(entry?.amountInr != null && entry.fxRate == null ? indianDigits(String(entry.amountInr)) : '');
    const [onPortal, setOnPortal] = useState(entry?.onPortal ?? false);
    const [trxn, setTrxn] = useState(entry?.trxnStatus ?? 'PENDING');
    const [brokerage, setBrokerage] = useState(entry?.brokerage != null ? String(entry.brokerage) : '');
    const [tdsApplied, setTdsApplied] = useState(entry?.tdsApplied ?? false);
    const [tdsPct, setTdsPct] = useState(entry ? String(entry.tdsPercent) : '');
    const [invoiceRaised, setInvoiceRaised] = useState(entry?.invoiceRaised ?? false);
    const [payment, setPayment] = useState(entry?.paymentStatus ?? 'PENDING');
    const [receivedOn, setReceivedOn] = useState(entry?.receivedOn ?? '');
    const [remarks, setRemarks] = useState(entry?.remarks ?? '');
    const { busy, error, run } = useSubmit();
    const funds = useLoad('/api/gift/funds');
    const tax = useLoad(`/api/product-taxes/GIFT/on?date=${monthDay}`);
    useEffect(() => {
        if (!entry && tax.data) {
            setTdsApplied(tax.data.tdsByDefault);
            setTdsPct(String(tax.data.tdsPercent));
        }
    }, [entry, tax.data]);
    // The USD → ₹ rate for the day: from the app's list, or fetched from Frankfurter (ECB) and kept (R-84).
    const fetchRate = async (day) => {
        if (!day)
            return;
        setFetching(true);
        setFxNote('');
        try {
            const r = await api(`/api/exchange-rates/USD/lookup?date=${day}`, { method: 'POST' });
            if (r.rate != null) {
                setFxRate(String(r.rate));
                setFxSource(r.source === 'MANUAL' ? `set by hand for ${r.day}` : `ECB rate of ${r.rateDate}`);
                setFxNote(r.note);
            }
            else {
                setFxNote(r.note);
            }
        }
        catch {
            setFxNote('Could not fetch the rate: type it');
        }
        finally {
            setFetching(false);
        }
    };
    // A new entry with a USD amount and no rate yet gets the day's rate by itself.
    useEffect(() => {
        if (!entry && amountUsd && !fxRate && !fetching && !fxNote)
            void fetchRate(rateDay);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [amountUsd]);
    const num = (s) => Number(s.replace(/,/g, '')) || 0;
    const gstPct = entry && entry.month === month.slice(0, 7) ? entry.gstPercent : (tax.data?.gstPercent ?? 0);
    const fig = giftFigures(num(amountUsd), num(fxRate), num(amountInr), num(brokerage), tdsApplied, num(tdsPct), gstPct);
    const rateGiven = num(amountUsd) > 0 && num(fxRate) > 0;
    async function submit(e) {
        e.preventDefault();
        const body = {
            ...party.body(),
            fund,
            amountUsd: amountUsd === '' ? null : num(amountUsd),
            fxRate: fxRate === '' ? null : num(fxRate),
            fxRateDate: fxRate === '' ? null : rateDay || null,
            fxSource: fxRate === '' ? null : fxSource || 'entered by hand',
            amountInr: rateGiven || amountInr === '' ? null : num(amountInr),
            onPortal,
            trxnStatus: trxn,
            brokerage: brokerage === '' ? null : num(brokerage),
            tdsApplied,
            tdsPercent: tdsApplied ? num(tdsPct) : 0,
            invoiceRaised,
            paymentStatus: payment,
            receivedOn: payment === 'RECEIVED' && receivedOn ? receivedOn : null,
            remarks,
        };
        const ok = await run(() => (entry ? api(`/api/gift/${entry.id}`, { method: 'PUT', body, ifMatch: entry.updatedAt }) : api('/api/gift', { method: 'POST', body })));
        if (ok)
            onDone();
    }
    return (_jsxs("form", { onSubmit: submit, className: "fd-form", children: [tax.error && _jsx("p", { className: "error", children: tax.error }), _jsx(ClientFieldset, { party: party, radioName: "gift-client" }), _jsx(MonthRmFieldset, { party: party, partnerLabel: "Referral partner / sub-broker" }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Investment" }), _jsxs("label", { children: ["Partner (fund)", _jsx("input", { value: fund, onChange: (e) => setFund(e.target.value), list: "gift-funds", placeholder: "e.g. DSP GIFT CITY", required: true })] }), _jsx("datalist", { id: "gift-funds", children: (funds.data ?? []).map((n) => (_jsx("option", { value: n }, n))) }), _jsxs("label", { children: ["AMT (USD)", _jsx("input", { value: amountUsd, onChange: (e) => setAmountUsd(indianDigits(e.target.value)), inputMode: "decimal", placeholder: "e.g. 10,000" })] }), _jsxs("label", { children: ["Rate date", _jsx("input", { type: "date", value: rateDay, onChange: (e) => {
                                    setRateDay(e.target.value);
                                    if (e.target.value && amountUsd)
                                        void fetchRate(e.target.value);
                                } })] }), _jsxs("span", { className: "inline-form", children: [_jsxs("label", { children: ["Exchange rate (\u20B9 per USD)", _jsx("input", { type: "number", min: 0, step: "0.0001", value: fxRate, onChange: (e) => {
                                            setFxRate(e.target.value);
                                            setFxSource('entered by hand');
                                            setFxNote('');
                                        }, placeholder: "e.g. 95.97" })] }), _jsx("button", { type: "button", className: "secondary", disabled: !rateDay || fetching, onClick: () => fetchRate(rateDay), children: fetching ? 'Fetching…' : 'Fetch rate' })] }), (fxSource || fxNote) && (_jsxs("p", { className: "muted", style: { marginTop: 0 }, children: [fxRate && fxSource ? `Rate: ${fxSource}` : fxNote, fxRate && fxSource !== 'entered by hand' && ' · type over it if the fund statement shows another rate'] })), _jsxs("label", { children: ["AMT in Rs.", rateGiven ? (_jsx("input", { value: formatMoney(fig.inr), readOnly: true })) : (_jsx("input", { value: amountInr, onChange: (e) => setAmountInr(indianDigits(e.target.value)), inputMode: "decimal", placeholder: "or USD \u00D7 rate" }))] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: onPortal, onChange: (e) => setOnPortal(e.target.checked) }), "Entry status: updated on portal"] }), _jsxs("label", { children: ["Trxn status", _jsxs("select", { value: trxn, onChange: (e) => setTrxn(e.target.value), children: [_jsx("option", { value: "PENDING", children: "Pending" }), _jsx("option", { value: "DONE", children: "Done" })] })] })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Brokerage" }), _jsx("p", { className: "muted", style: { marginTop: 0 }, children: "How GIFT City pays is not settled yet: type the brokerage (excl. GST) when it is known. TDS and GST are worked out from it." }), _jsxs("label", { children: ["Brokerage (\u20B9, excl. GST)", _jsx("input", { type: "number", min: 0, step: "0.01", value: brokerage, onChange: (e) => setBrokerage(e.target.value), placeholder: "not known yet" })] }), _jsxs("span", { className: "inline-form", children: [_jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: tdsApplied, onChange: (e) => setTdsApplied(e.target.checked) }), "TDS"] }), tdsApplied && (_jsxs("label", { children: ["TDS %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: tdsPct, onChange: (e) => setTdsPct(e.target.value), "aria-label": "TDS %", required: true })] }))] }), _jsxs("dl", { className: "facts", children: [_jsx("dt", { children: "Brokerage" }), _jsx("dd", { children: formatMoney(fig.gross) }), _jsx("dt", { children: "TDS" }), _jsx("dd", { children: formatMoney(fig.tds) }), _jsxs("dt", { children: ["GST (", gstPct, "%)"] }), _jsx("dd", { children: formatMoney(fig.gst) }), _jsx("dt", { children: "Net revenue" }), _jsx("dd", { children: _jsx("strong", { children: formatMoney(fig.net) }) }), _jsx("dt", { children: "Invoice amount" }), _jsx("dd", { children: formatMoney(fig.invoice) })] })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Payment" }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: invoiceRaised, onChange: (e) => setInvoiceRaised(e.target.checked) }), "Invoice raised by accounts"] }), _jsxs("label", { children: ["Payment status", _jsx("select", { value: payment, onChange: (e) => setPayment(e.target.value), children: Object.entries(GIFT_PAYMENT_LABEL).map(([k, v]) => (_jsx("option", { value: k, children: v }, k))) })] }), payment === 'RECEIVED' && (_jsxs("label", { children: ["Amt received date", _jsx("input", { type: "date", value: receivedOn, onChange: (e) => setReceivedOn(e.target.value) })] })), _jsxs("label", { children: ["Remarks", _jsx("textarea", { value: remarks, onChange: (e) => setRemarks(e.target.value), rows: 2 })] })] }), error && _jsx("p", { className: "error", children: error }), _jsxs("span", { className: "inline-form", children: [_jsx("button", { type: "submit", disabled: busy || clientMode === 'lookup' || clientMode === 'pick', children: entry ? 'Save changes' : 'Add GIFT City entry' }), entry && _jsx(CancelEntry, { url: `/api/gift/${entry.id}/cancel`, question: "Why cancel this GIFT City entry?", onDone: onDone })] })] }));
}
