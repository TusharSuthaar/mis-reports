import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { CancelEntry, ClientFieldset, MonthRmFieldset, useClientAndRm } from '../entry/ClientAndRm';
import { useEffect, useState } from 'react';
import { useTableView } from '../../components/useTableView';
import { api } from '../../api/client';
import { CERTIFICATE_LABEL, PAYMENT_LABEL, } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { ListFilters, listPath, useFilterQuery } from '../../components/ListFilters';
import { GivenFiguresFields, givenBody, shownFigures, useGiven } from '../../components/GivenFigures';
import { Modal } from '../../components/Modal';
import { useEntryPanel } from '../../components/useEntryPanel';
import { formatMoney, todayIso, formatMonth, indianDigits } from '../../format';
import { Amount } from '../../components/Amount';
import { bondRevenue } from '../bonds/BondsPage';
const round2 = (n) => Math.round((n + Number.EPSILON) * 100) / 100;
const thisMonth = () => todayIso().slice(0, 7);
/** AIF: one row per commitment, with its drawdowns; revenue on the commitment from the mark-up and the platform's terms. */
export function AifPage({ embedded = false } = {}) {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const month = thisMonth();
    const query = useFilterQuery();
    const list = useLoad(listPath('/api/aif', query));
    const platforms = useLoad('/api/platforms');
    const [panel, setPanel] = useEntryPanel();
    const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
    const active = (list.data ?? []).filter((e) => e.status === 'ACTIVE');
    const total = (k) => active.reduce((s, e) => s + e[k], 0);
    const done = () => {
        setPanel(null);
        list.reload();
    };
    const view = useTableView(list.data, { month: (e) => e.month, client: (e) => e.clientName, fund: (e) => e.fundName, platform: (e) => e.platformName, rm: (e) => e.rmName, ref_sharing: (e) => e.partnerName, commitment: (e) => e.commitment, called: (e) => e.called, gross: (e) => e.gross, invoice: (e) => e.invoice, payment: (e) => e.paymentStatus });
    return (_jsxs("section", { children: [_jsxs("div", { className: "page-head", style: embedded ? { justifyContent: 'flex-end', marginBottom: 12 } : undefined, children: [!embedded && _jsx("h1", { children: "AIF" }), canEdit && (_jsxs("div", { className: "actions", children: [_jsx("a", { className: "button-link secondary", href: `/api/aif/export.xlsx${query ?? ''}`, download: true, children: "Download Excel" }), _jsx("button", { type: "button", onClick: () => setPanel('new'), children: "Add AIF" })] }))] }), _jsx(ListFilters, { searchHint: "Client, PAN, code or fund name", platformKey: "platformId", platforms: (platforms.data ?? []).map((p) => ({ value: String(p.id), label: p.name })) }), list.error && _jsx("p", { className: "error", children: list.error }), list.data && list.data.length === 0 && _jsx("p", { className: "muted", children: "No AIF commitments match." }), list.data && list.data.length > 0 && (_jsxs(_Fragment, { children: [_jsx("div", { className: "table-scroll", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { className: "num", children: "SR.NO" }), view.th('month', 'Month'), view.th('client', 'Client'), view.th('fund', 'Fund'), view.th('platform', 'Platform'), view.th('rm', 'RM'), view.th('ref_sharing', 'Ref / sharing'), view.th('commitment', 'Commitment', 'num'), view.th('called', 'Called', 'num'), view.th('gross', 'Gross', 'num'), view.th('invoice', 'Invoice', 'num'), view.th('payment', 'Payment')] }) }), _jsxs("tbody", { children: [view.rows.map((e, i) => (_jsxs("tr", { className: e.status === 'CANCELLED' ? 'muted' : undefined, children: [_jsx("td", { className: "num", children: i + 1 }), _jsx("td", { children: formatMonth(e.month) }), _jsxs("td", { children: [_jsx("button", { type: "button", className: "cell-link", onClick: () => setPanel(e.id), children: e.clientName }), e.status === 'CANCELLED' && _jsx("span", { className: "badge", children: "cancelled" })] }), _jsx("td", { children: e.fundName }), _jsx("td", { children: e.platformName }), _jsx("td", { children: e.rmName }), _jsx("td", { children: e.partnerName ? `${e.partnerName}${e.sharingPercent != null ? ` · ${e.sharingPercent}%` : ''}` : _jsx("span", { className: "muted", children: "\u2014" }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.commitment }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.called }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.gross }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.invoice }) }), _jsx("td", { children: e.paymentStatus === 'RECEIVED' ? PAYMENT_LABEL[e.paymentStatus] : _jsx("span", { className: "error", children: PAYMENT_LABEL[e.paymentStatus] }) })] }, e.id))), _jsxs("tr", { className: "total", children: [_jsxs("td", { colSpan: 7, children: ["Total (", active.length, " commitments)"] }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('commitment') }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('called') }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('gross') }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: total('invoice') }) }), _jsx("td", {})] })] })] }) }), view.more] })), panel === 'new' && (_jsx(Modal, { title: "Add AIF", onClose: () => setPanel(null), children: _jsx(AifForm, { month: month, onDone: done }) })), editing && (_jsx(Modal, { title: `${editing.clientName} — ${editing.fundName}`, onClose: () => setPanel(null), children: canEdit && editing.status === 'ACTIVE' ? (_jsx(AifForm, { month: month, entry: editing, onDone: done })) : (_jsxs("p", { children: [editing.fundName, " \u00B7 ", formatMoney(editing.commitment), " \u00B7 ", editing.status === 'CANCELLED' ? 'Cancelled' : PAYMENT_LABEL[editing.paymentStatus]] })) }))] }));
}
/** Add or edit one AIF commitment, laid out as in the AIF register. */
function AifForm({ month: startMonth, entry, onDone }) {
    const num = (s) => Number(s.replace(/,/g, '')) || 0;
    const str = (n) => (n == null ? '' : String(n));
    const party = useClientAndRm(entry, entry ? entry.month : startMonth);
    const { month, monthDay, partnerId, clientMode } = party;
    const [sharing, setSharing] = useState(str(entry?.sharingPercent));
    const [platformId, setPlatformId] = useState(entry ? String(entry.platformId) : '');
    const [fundName, setFundName] = useState(entry?.fundName ?? '');
    const [commitment, setCommitment] = useState(entry ? indianDigits(String(entry.commitment)) : '');
    const [drawdowns, setDrawdowns] = useState(entry && entry.instalments.length > 0
        ? entry.instalments.map((i) => ({ amount: indianDigits(String(i.amount)), paidOn: i.paidOn ?? '' }))
        : [{ amount: '', paidOn: '' }]);
    const [agreementDate, setAgreementDate] = useState(entry?.agreementDate ?? '');
    const [tenure, setTenure] = useState(entry?.tenure ?? '');
    const [maturityDate, setMaturityDate] = useState(entry?.maturityDate ?? '');
    const [soa, setSoa] = useState(entry?.soaReceived ?? false);
    const [appForm, setAppForm] = useState(entry?.applicationForm ?? false);
    const [mail, setMail] = useState(entry?.investmentMail ?? false);
    const [qty, setQty] = useState(str(entry?.quantity));
    const [price, setPrice] = useState(entry?.price != null ? indianDigits(String(entry.price)) : '');
    const [certificate, setCertificate] = useState(entry?.certificate ?? 'PENDING');
    const [payoutTerms, setPayoutTerms] = useState(entry?.payoutTerms ?? '');
    const [markup, setMarkup] = useState(str(entry?.markupPercent));
    const [markupTouched, setMarkupTouched] = useState(!!entry);
    const [tdsApplied, setTdsApplied] = useState(entry?.tdsApplied ?? true);
    const [tdsPct, setTdsPct] = useState(str(entry?.tdsPercent));
    const [invoiceRaised, setInvoiceRaised] = useState(entry?.invoiceRaised ?? false);
    const [invoiceDate, setInvoiceDate] = useState(entry?.invoiceDate ?? '');
    const [receivedOn, setReceivedOn] = useState(entry?.paymentReceivedOn ?? '');
    const [payment, setPayment] = useState(entry?.paymentStatus ?? 'PENDING');
    const [partnerPaid, setPartnerPaid] = useState(entry?.partnerPaid ?? false);
    const [onInvestwell, setOnInvestwell] = useState(entry?.onInvestwell ?? false);
    const [remarks, setRemarks] = useState(entry?.remarks ?? '');
    const { busy, error, run } = useSubmit();
    const platforms = useLoad('/api/platforms');
    const suggestions = useLoad('/api/aif/suggestions');
    const tax = useLoad(`/api/product-taxes/AIF/on?date=${monthDay}`);
    const terms = useLoad(platformId ? `/api/platforms/${platformId}/terms/on?date=${monthDay}` : null);
    useEffect(() => {
        if (!entry && terms.data) {
            setTdsPct(String(terms.data.tdsPercent));
            setTdsApplied(terms.data.tdsPercent > 0);
        }
    }, [entry, terms.data]);
    const lastMarkup = suggestions.data?.markups.find((m) => String(m.platformId) === platformId && m.fundName.toLowerCase() === fundName.trim().toLowerCase());
    useEffect(() => {
        if (!markupTouched && lastMarkup)
            setMarkup(String(lastMarkup.markupPercent));
    }, [lastMarkup, markupTouched]);
    const sameMonth = entry && entry.month === month.slice(0, 7);
    const share = sameMonth ? entry.ourShare : (terms.data?.ourShare ?? 100);
    const inclusive = sameMonth ? entry.gstInclusive : (terms.data?.gstInclusive ?? false);
    const gstPct = sameMonth ? entry.gstPercent : (tax.data?.gstPercent ?? 0);
    const [given, setGiven] = useGiven(entry);
    const worked = bondRevenue(num(commitment), num(markup), share, inclusive, gstPct, tdsApplied, num(tdsPct));
    const rev = shownFigures(worked, given);
    const called = round2(drawdowns.reduce((s, d) => s + num(d.amount), 0));
    const partnerShare = partnerId && sharing !== '' ? round2((rev.gross * num(sharing)) / 100) : null;
    const setDrawdown = (i, patch) => setDrawdowns(drawdowns.map((d, j) => (j === i ? { ...d, ...patch } : d)));
    async function submit(e) {
        e.preventDefault();
        const n = (s) => (s === '' ? null : num(s));
        const body = {
            ...party.body(),
            sharingPercent: partnerId ? n(sharing) : null,
            platformId: platformId ? Number(platformId) : null,
            fundName,
            commitment: n(commitment),
            agreementDate: agreementDate || null,
            tenure,
            maturityDate: maturityDate || null,
            soaReceived: soa,
            applicationForm: appForm,
            investmentMail: mail,
            quantity: n(qty),
            price: n(price),
            certificate,
            payoutTerms,
            markupPercent: n(markup),
            tdsApplied,
            tdsPercent: tdsApplied ? num(tdsPct) : 0,
            invoiceRaised,
            invoiceDate: invoiceDate || null,
            paymentReceivedOn: receivedOn || null,
            paymentStatus: payment,
            partnerPaid,
            onInvestwell,
            remarks,
            ...givenBody(given),
            instalments: drawdowns.filter((d) => num(d.amount) > 0).map((d) => ({ amount: num(d.amount), paidOn: d.paidOn || null })),
        };
        const ok = await run(() => (entry ? api(`/api/aif/${entry.id}`, { method: 'PUT', body, ifMatch: entry.updatedAt }) : api('/api/aif', { method: 'POST', body })));
        if (ok)
            onDone();
    }
    return (_jsxs("form", { onSubmit: submit, className: "fd-form", children: [tax.error && _jsx("p", { className: "error", children: tax.error }), terms.error && _jsx("p", { className: "error", children: terms.error }), _jsx(ClientFieldset, { party: party, radioName: "aif-client", mobileLabel: "Mobile", emailLabel: "Email", codeHint: "EM1234", showClientType: true }), _jsx(MonthRmFieldset, { party: party, legend: "Month, RM and revenue sharing", partnerLabel: "Referral / sub-broker", note: partnerId && (sharing === '' ? " · Partner's general AIF % applies." : ` · Partner gets ${sharing}% of gross${partnerShare != null ? ` = ${formatMoney(partnerShare)}` : ''}.`), children: partnerId && (_jsxs("label", { children: ["Revenue sharing % (this deal)", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: sharing, onChange: (e) => setSharing(e.target.value), placeholder: "partner's AIF %" })] })) }), _jsxs("fieldset", { children: [_jsx("legend", { children: "AIF" }), _jsxs("label", { children: ["Partner (platform)", _jsxs("select", { value: platformId, onChange: (e) => setPlatformId(e.target.value), required: true, children: [_jsx("option", { value: "", children: "Choose\u2026" }), (platforms.data ?? []).map((p) => (_jsx("option", { value: p.id, children: p.name }, p.id)))] })] }), _jsxs("label", { children: ["AIF (fund name)", _jsx("input", { value: fundName, onChange: (e) => setFundName(e.target.value), list: "aif-funds", required: true })] }), _jsx("datalist", { id: "aif-funds", children: (suggestions.data?.fundNames ?? []).map((n) => (_jsx("option", { value: n }, n))) }), _jsxs("label", { children: ["Commitment (\u20B9)", _jsx("input", { value: commitment, onChange: (e) => setCommitment(indianDigits(e.target.value)), inputMode: "decimal", required: true })] }), _jsxs("label", { children: ["Date of agreement signing", _jsx("input", { type: "date", value: agreementDate, onChange: (e) => setAgreementDate(e.target.value) })] }), _jsxs("label", { children: ["Tenure", _jsx("input", { value: tenure, onChange: (e) => setTenure(e.target.value), placeholder: "e.g. Five Years" })] }), _jsxs("label", { children: ["Maturity date", _jsx("input", { type: "date", value: maturityDate, onChange: (e) => setMaturityDate(e.target.value) })] }), _jsxs("label", { children: ["Quantity", _jsx("input", { type: "number", min: 0, step: "any", value: qty, onChange: (e) => setQty(e.target.value) })] }), _jsxs("label", { children: ["Price (\u20B9)", _jsx("input", { value: price, onChange: (e) => setPrice(indianDigits(e.target.value)), inputMode: "decimal" })] })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Instalments (drawdowns)" }), drawdowns.map((d, i) => (_jsxs("span", { className: "inline-form", children: [_jsxs("label", { children: [ordinal(i + 1), " instalment (\u20B9)", _jsx("input", { value: d.amount, onChange: (e) => setDrawdown(i, { amount: indianDigits(e.target.value) }), inputMode: "decimal" })] }), _jsxs("label", { children: ["Date of payment", _jsx("input", { type: "date", value: d.paidOn, onChange: (e) => setDrawdown(i, { paidOn: e.target.value }), "aria-label": `${ordinal(i + 1)} instalment date of payment` })] }), drawdowns.length > 1 && (_jsx("button", { type: "button", className: "link", onClick: () => setDrawdowns(drawdowns.filter((_, j) => j !== i)), children: "Remove" }))] }, i))), _jsxs("span", { className: "inline-form", children: [_jsx("button", { type: "button", className: "secondary", onClick: () => setDrawdowns([...drawdowns, { amount: '', paidOn: '' }]), children: "Add instalment" }), _jsxs("span", { className: "muted", children: ["Called so far: ", _jsx("strong", { children: formatMoney(called) }), num(commitment) > 0 && ` of ${formatMoney(num(commitment))} (${Math.round((called * 100) / num(commitment))}%)`] })] }), num(commitment) > 0 && called > num(commitment) && _jsx("p", { className: "error", children: "The instalments add up to more than the commitment." })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Documents" }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: soa, onChange: (e) => setSoa(e.target.checked) }), "SOA received"] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: appForm, onChange: (e) => setAppForm(e.target.checked) }), "Application form"] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: mail, onChange: (e) => setMail(e.target.checked) }), "Investment mail"] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: onInvestwell, onChange: (e) => setOnInvestwell(e.target.checked) }), "Updated on Investwell"] }), _jsxs("label", { children: ["Certificate status", _jsx("select", { value: certificate, onChange: (e) => setCertificate(e.target.value), children: Object.entries(CERTIFICATE_LABEL).map(([k, v]) => (_jsx("option", { value: k, children: v }, k))) })] })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Revenue" }), _jsx(GivenFiguresFields, { value: given, onChange: setGiven }), _jsxs("label", { children: ["Mark-up %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.0001", value: markup, onChange: (e) => {
                                    setMarkupTouched(true);
                                    setMarkup(e.target.value);
                                }, required: true })] }), _jsxs("span", { className: "inline-form", children: [_jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: tdsApplied, onChange: (e) => setTdsApplied(e.target.checked) }), "TDS"] }), tdsApplied && (_jsxs("label", { children: ["TDS %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: tdsPct, onChange: (e) => setTdsPct(e.target.value), "aria-label": "TDS %", required: true })] }))] }), _jsxs("label", { children: ["Payout terms", _jsx("input", { value: payoutTerms, onChange: (e) => setPayoutTerms(e.target.value), placeholder: "e.g. Will be paid quarterly - Oct" })] }), lastMarkup && _jsxs("p", { className: "muted", children: ["Mark-up last used for this fund on this platform: ", lastMarkup.markupPercent, "%"] }), _jsx("p", { className: "muted", children: platformId ? `Platform terms: brokerage ${inclusive ? 'includes GST' : 'plus GST'}, our share ${share}%. Revenue is on the commitment.` : 'Choose the platform to see its terms.' }), _jsxs("dl", { className: "facts", children: [_jsx("dt", { children: "Gross revenue" }), _jsx("dd", { children: formatMoney(rev.gross) }), _jsx("dt", { children: "TDS" }), _jsx("dd", { children: formatMoney(rev.tds) }), _jsxs("dt", { children: ["GST (", gstPct, "%)"] }), _jsx("dd", { children: formatMoney(rev.gst) }), _jsx("dt", { children: "Net revenue" }), _jsx("dd", { children: _jsx("strong", { children: formatMoney(rev.net) }) }), _jsx("dt", { children: "Invoice amount" }), _jsx("dd", { children: formatMoney(rev.invoice) })] })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Follow-up" }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: invoiceRaised, onChange: (e) => setInvoiceRaised(e.target.checked) }), "Invoice raised by accounts"] }), _jsxs("label", { children: ["Invoice raised on", _jsx("input", { type: "date", value: invoiceDate, onChange: (e) => setInvoiceDate(e.target.value) })] }), _jsxs("label", { children: ["Amount received on", _jsx("input", { type: "date", value: receivedOn, onChange: (e) => setReceivedOn(e.target.value) })] }), _jsxs("label", { children: ["Payment status", _jsx("select", { value: payment, onChange: (e) => setPayment(e.target.value), children: Object.entries(PAYMENT_LABEL).map(([k, v]) => (_jsx("option", { value: k, children: v }, k))) })] }), partnerId && (_jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: partnerPaid, onChange: (e) => setPartnerPaid(e.target.checked) }), "Revenue share paid to the partner"] })), _jsxs("label", { children: ["Remarks", _jsx("textarea", { value: remarks, onChange: (e) => setRemarks(e.target.value), rows: 2 })] })] }), error && _jsx("p", { className: "error", children: error }), _jsxs("span", { className: "inline-form", children: [_jsx("button", { type: "submit", disabled: busy || clientMode === 'lookup' || clientMode === 'pick', children: entry ? 'Save changes' : 'Add AIF' }), entry && _jsx(CancelEntry, { url: `/api/aif/${entry.id}/cancel`, question: "Why cancel this AIF entry?", label: "Cancel this AIF", onDone: onDone })] })] }));
}
function ordinal(n) {
    return n === 1 ? '1st' : n === 2 ? '2nd' : n === 3 ? '3rd' : `${n}th`;
}
