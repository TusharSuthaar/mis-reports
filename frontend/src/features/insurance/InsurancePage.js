import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { PAN_FORMAT } from '../entry/ClientAndRm';
import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
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
import { formatMoney, todayIso, formatMonth, indianDigits } from '../../format';
const round2 = (n) => Math.round((n + Number.EPSILON) * 100) / 100;
/**
 * The commissions as the server works them out (R-95): expected = net × expected %; the POSP's % = expected % × agreed %;
 * the POSP's commission = net × the POSP's %. A typed amount stands instead of either calculation.
 */
export function insuranceFigures(net, expectedPct, expectedTyped, hasPosp, agreedPct, pospTyped) {
    const expected = expectedTyped != null ? round2(expectedTyped) : round2((net * (expectedPct ?? 0)) / 100);
    const pospPct = hasPosp && agreedPct != null && expectedPct != null ? (expectedPct * agreedPct) / 100 : null;
    const posp = !hasPosp ? 0 : pospTyped != null ? round2(pospTyped) : pospPct != null ? round2((net * pospPct) / 100) : agreedPct != null ? round2((expected * agreedPct) / 100) : 0;
    return { expected, pospPct, posp, ours: round2(expected - posp) };
}
export const CATEGORY_LABEL = { LIFE: 'Life', HEALTH: 'Health', MOTOR: 'Motor', GENERAL: 'General' };
const TYPE_LABEL = { NEW: 'New', RENEWAL: 'Renewal', PORT: 'Port', ENDORSEMENT: 'Endorsement', FYCI: 'FYCI' };
const OTHER = '__other__';
const thisMonth = () => todayIso().slice(0, 7);
/** Insurance: one row per policy, as in the insurance team's policy sheet (R-94), typed here or uploaded for the month. */
export function InsurancePage({ embedded = false } = {}) {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const query = useFilterQuery();
    const list = useLoad(listPath('/api/insurance', query));
    const choices = useLoad('/api/insurance/choices');
    const [panel, setPanel] = useEntryPanel();
    const [uploading, setUploading] = useState(false);
    const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
    const active = (list.data ?? []).filter((e) => e.status === 'ACTIVE');
    const done = () => {
        setPanel(null);
        setUploading(false);
        list.reload();
        choices.reload();
    };
    const view = useTableView(list.data, {
        month: (e) => e.month, client: (e) => e.clientName, rm: (e) => e.rmName, insurer: (e) => e.insurer, product: (e) => e.productName,
        category: (e) => e.category, type: (e) => e.businessType, net: (e) => e.netPremium, pct: (e) => e.expectedPercent, expected: (e) => e.expectedComm,
        posp: (e) => e.pospName, posp_comm: (e) => e.pospComm,
    });
    return (_jsxs("section", { children: [_jsxs("div", { className: "page-head", style: embedded ? { justifyContent: 'flex-end', marginBottom: 12 } : undefined, children: [!embedded && _jsx("h1", { children: "Insurance" }), canEdit && (_jsxs("div", { className: "actions", children: [_jsx("a", { className: "button-link secondary", href: `/api/insurance/export.xlsx${query ?? ''}`, download: true, children: "Download Excel" }), _jsx("button", { type: "button", className: "secondary", onClick: () => setUploading(true), children: "Upload policy sheet" }), _jsx("button", { type: "button", onClick: () => setPanel('new'), children: "Add policy" })] }))] }), _jsx("p", { className: "muted", children: "Each policy counts in its business month: the END shows the expected commission in the Insurance column, and the POSP's commission as POSP Sharing. The client is found by PAN and the RM always comes from the client master." }), _jsx(ListFilters, { searchHint: "Client, PAN, code, policy no., product or POSP", platformKey: "platform", platformLabel: "Insurer", platforms: (choices.data?.insurers ?? []).map((p) => ({ value: p, label: p })), partnerFilter: false, entryFilters: false }), list.error && _jsx("p", { className: "error", children: list.error }), list.data && list.data.length === 0 && _jsx("p", { className: "muted", children: "No policies match." }), list.data && list.data.length > 0 && (_jsxs(_Fragment, { children: [_jsx("div", { className: "table-scroll", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [view.th('month', 'Month'), view.th('client', 'Client'), view.th('rm', 'RM'), view.th('insurer', 'Insurer'), view.th('product', 'Product'), view.th('category', 'CAT'), view.th('type', 'Business type'), _jsx("th", { children: "Policy no." }), view.th('net', 'Net premium', 'num'), view.th('pct', 'Exp. %', 'num'), view.th('expected', 'Expected comm.', 'num'), view.th('posp', 'POSP'), view.th('posp_comm', 'POSP comm.', 'num')] }) }), _jsxs("tbody", { children: [view.rows.map((e) => (_jsxs("tr", { className: e.status === 'CANCELLED' ? 'muted' : undefined, children: [_jsx("td", { children: formatMonth(e.month) }), _jsxs("td", { children: [_jsx("button", { type: "button", className: "cell-link", onClick: () => setPanel(e.id), children: e.clientName }), e.status === 'CANCELLED' && _jsx("span", { className: "badge", children: "cancelled" })] }), _jsx("td", { children: e.rmName }), _jsx("td", { children: e.insurer }), _jsx("td", { children: e.productName }), _jsx("td", { children: CATEGORY_LABEL[e.category] }), _jsx("td", { children: TYPE_LABEL[e.businessType] }), _jsx("td", { className: "mono", children: e.policyNo }), _jsx("td", { className: "num", children: _jsx(Amount, { value: e.netPremium }) }), _jsx("td", { className: "num", children: e.expectedPercent != null ? `${e.expectedPercent}%` : '' }), _jsxs("td", { className: "num", children: [formatMoney(e.expectedComm), e.expectedAsGiven && _jsx("span", { className: "muted", title: "Typed amount, not net \u00D7 %", children: " *" })] }), _jsx("td", { children: e.pospName ?? _jsx("span", { className: "muted", children: "\u2014" }) }), _jsx("td", { className: "num", children: e.pospComm ? formatMoney(e.pospComm) : '' })] }, e.id))), _jsxs("tr", { className: "total", children: [_jsxs("td", { colSpan: 8, children: ["Total (", active.length, " policies)"] }), _jsx("td", { className: "num", children: _jsx(Amount, { value: active.reduce((s, e) => s + e.netPremium, 0) }) }), _jsx("td", {}), _jsx("td", { className: "num", children: _jsx(Amount, { value: active.reduce((s, e) => s + e.expectedComm, 0) }) }), _jsx("td", {}), _jsx("td", { className: "num", children: _jsx(Amount, { value: active.reduce((s, e) => s + e.pospComm, 0) }) })] })] })] }) }), view.more] })), panel === 'new' && (_jsx(Modal, { title: "Add insurance policy", onClose: () => setPanel(null), children: _jsx(PolicyForm, { onDone: done, choices: choices.data ?? undefined }) })), editing && (_jsx(Modal, { title: `${editing.clientName} — ${editing.insurer} ${editing.policyNo}`, onClose: () => setPanel(null), children: canEdit && editing.status === 'ACTIVE' ? (_jsx(PolicyForm, { policy: editing, onDone: done, choices: choices.data ?? undefined })) : (_jsxs("dl", { className: "facts", children: [_jsx("dt", { children: "Product" }), _jsx("dd", { children: editing.productName }), _jsx("dt", { children: "Expected commission" }), _jsx("dd", { children: formatMoney(editing.expectedComm) })] })) })), uploading && (_jsx(Modal, { title: "Upload the month's policy sheet", onClose: () => setUploading(false), children: _jsx(UploadPolicies, { onDone: done }) }))] }));
}
/**
 * Add or edit one policy, in short blocks: who (client found by PAN / code / name, shown with its RM before saving), the
 * policy, the chosen kind of insurance's own fields (R-96), and the money. The expected % and the POSP's agreed % fill in from earlier policies; the rest is worked out.
 */
function PolicyForm({ policy, onDone, choices }) {
    const num = (s) => Number(s.replace(/,/g, '')) || 0;
    const str = (n) => (n == null ? '' : String(n));
    // Who
    const [lookup, setLookup] = useState('');
    const [looked, setLooked] = useState('');
    const [client, setClient] = useState(null);
    const [changeClient, setChangeClient] = useState(false);
    const [newName, setNewName] = useState('');
    const [newPan, setNewPan] = useState('');
    const [rmId, setRmId] = useState('');
    // Policy
    const [month, setMonth] = useState(policy?.month ?? thisMonth());
    const [category, setCategory] = useState(policy?.category ?? '');
    const [insurer, setInsurer] = useState(policy?.insurer ?? '');
    const [productName, setProductName] = useState(policy?.productName ?? '');
    const [policyNo, setPolicyNo] = useState(policy?.policyNo ?? '');
    const [businessType, setBusinessType] = useState(policy?.businessType ?? '');
    const [subType, setSubType] = useState(policy?.subType ?? '');
    const [sumAssured, setSumAssured] = useState(policy?.sumAssured != null ? indianDigits(String(policy.sumAssured)) : '');
    const [ppt, setPpt] = useState(policy?.ppt ?? '');
    const [policyTerm, setPolicyTerm] = useState(str(policy?.policyTerm));
    const [od, setOd] = useState(policy?.odPremium != null ? indianDigits(String(policy.odPremium)) : '');
    const [tp, setTp] = useState(policy?.tpPremium != null ? indianDigits(String(policy.tpPremium)) : '');
    // Money
    const [total, setTotal] = useState(policy ? indianDigits(String(policy.totalPremium)) : '');
    const [net, setNet] = useState(policy ? indianDigits(String(policy.netPremium)) : '');
    const [expectedPct, setExpectedPct] = useState(str(policy?.expectedPercent));
    const [expectedTyped, setExpectedTyped] = useState(policy?.expectedAsGiven ? str(policy.expectedComm) : '');
    const [typeExpected, setTypeExpected] = useState(!!policy?.expectedAsGiven);
    const [posp, setPosp] = useState(policy?.pospName ?? '');
    const [agreedPct, setAgreedPct] = useState(str(policy?.pospShare));
    const [pospTyped, setPospTyped] = useState(policy?.pospAsGiven ? str(policy.pospComm) : '');
    const [typePosp, setTypePosp] = useState(!!policy?.pospAsGiven);
    const [actualPct, setActualPct] = useState(str(policy?.actualPercent));
    const [remarks, setRemarks] = useState(policy?.remarks ?? '');
    const [netEdited, setNetEdited] = useState(!!policy);
    const [cover, setCover] = useState(policy?.odPremium && !policy.tpPremium ? 'OD' : !policy?.odPremium && policy?.tpPremium ? 'TP' : 'COMP');
    const { busy, error, run } = useSubmit();
    const cats = useLoad('/api/insurance/categories');
    const rule = cats.data?.find((r) => r.category === category);
    // A sub type not in the list (typed, or from the sheet) shows as "Other…" with its text.
    const subTypeOptions = rule?.subTypes ?? [];
    // Insurers of the chosen kind (before one is chosen: all of them); another one is typed under "Other…".
    const insurerOptions = rule?.insurers ?? [...new Set((cats.data ?? []).flatMap((c) => c.insurers))].sort((a, b) => a.localeCompare(b));
    const showOd = cover !== 'TP';
    const showTp = cover !== 'OD';
    // The net premium follows the category (R-96) until it is typed over: Motor = OD + TP, Life / Health = total, General = total less GST.
    const autoNet = !rule ? null
        : rule.premium === 'OD_TP' ? (od || tp ? round2((showOd ? num(od) : 0) + (showTp ? num(tp) : 0)) : null)
            : total === '' ? null
                : rule.premium === 'TOTAL' ? num(total) : round2((num(total) * 100) / (100 + rule.gstPercent));
    const netValue = netEdited || autoNet == null ? net : indianDigits(String(autoNet));
    // A new category: its first policy period, and no business type it does not have.
    useEffect(() => {
        if (!rule)
            return;
        if (businessType && !rule.businessTypes.includes(businessType))
            setBusinessType('');
        if (!ppt && rule.pptChoices.length)
            setPpt(rule.pptChoices[0]);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [rule?.category]);
    const lookingUp = !policy || changeClient;
    const employees = useLoad('/api/employees');
    const partners = useLoad('/api/partners');
    const found = useLoad(lookingUp && looked ? `/api/clients?q=${encodeURIComponent(looked)}&size=20` : null);
    // A PAN or client code must match exactly; anything else is a name: every client whose name has it, to pick from.
    const matches = useMemo(() => {
        if (!looked || !found.data)
            return null;
        const q = looked.toUpperCase();
        if (PAN_FORMAT.test(q))
            return found.data.items.filter((c) => c.pan != null && (c.pan.toUpperCase() === q || (c.pan.includes('*') && c.pan.slice(0, 5) === q.slice(0, 5) && c.pan.slice(-1) === q.slice(-1))));
        const byCode = found.data.items.filter((c) => c.iwellCode != null && c.iwellCode.toUpperCase() === q);
        return byCode.length ? byCode : found.data.items;
    }, [found.data, looked]);
    const clientMode = !lookingUp ? 'found' : !matches ? 'lookup' : matches.length === 0 ? 'new' : client ? 'found' : 'pick';
    const posps = (partners.data ?? []).filter((p) => p.type === 'POSP');
    const pospMatch = posps.find((p) => p.name.trim().toLowerCase() === posp.trim().toLowerCase());
    const noPosp = posp.trim() === '' || ['NA', 'N/A', 'NONE', '-'].includes(posp.trim().toUpperCase());
    const needsRm = clientMode === 'new' || (clientMode === 'found' && lookingUp && client != null && !client.rmId);
    const defaults = useLoad(!policy && (insurer && productName || pospMatch)
        ? `/api/insurance/defaults?insurer=${encodeURIComponent(insurer)}&productName=${encodeURIComponent(productName)}${pospMatch ? `&pospId=${pospMatch.id}` : ''}`
        : null);
    useEffect(() => {
        if (matches && matches.length === 1)
            setClient(matches[0]);
        if (matches && matches.length === 0 && PAN_FORMAT.test(looked))
            setNewPan(looked);
    }, [matches, looked]);
    // Fill in from earlier policies only what is still empty.
    useEffect(() => {
        const d = defaults.data;
        if (!d)
            return;
        if (d.category && !category)
            setCategory(d.category);
        if (d.subType && !subType)
            setSubType(d.subType);
        if (d.expectedPercent != null && expectedPct === '')
            setExpectedPct(String(d.expectedPercent));
        if (d.pospShare != null && agreedPct === '')
            setAgreedPct(String(d.pospShare));
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [defaults.data]);
    const findClient = () => {
        setClient(null);
        setLooked(lookup.trim().toUpperCase());
    };
    const fig = insuranceFigures(num(netValue), expectedPct === '' ? null : num(expectedPct), typeExpected && expectedTyped !== '' ? num(expectedTyped) : null, !noPosp, agreedPct === '' ? null : num(agreedPct), typePosp && pospTyped !== '' ? num(pospTyped) : null);
    const people = (employees.data ?? []).filter((e) => e.status === 'ACTIVE');
    async function submit(e) {
        e.preventDefault();
        const body = {
            clientId: policy && !changeClient ? policy.clientId : clientMode === 'found' ? client?.id : null,
            newClientName: clientMode === 'new' ? newName : null,
            newClientPan: clientMode === 'new' && newPan ? newPan : null,
            rmId: needsRm && rmId ? Number(rmId) : null,
            month: `${month.slice(0, 7)}-01`,
            pospId: noPosp ? null : pospMatch?.id ?? null,
            newPospName: noPosp || pospMatch ? null : posp.trim(),
            insurer,
            category: category || null,
            subType: subType.trim(),
            productName,
            policyNo,
            businessType: businessType || null,
            ppt,
            policyTerm: category === 'LIFE' && policyTerm !== '' ? num(policyTerm) : null,
            sumAssured: sumAssured === '' ? null : num(sumAssured),
            odPremium: category === 'MOTOR' && showOd && od !== '' ? num(od) : null,
            tpPremium: category === 'MOTOR' && showTp && tp !== '' ? num(tp) : null,
            totalPremium: total === '' ? null : num(total),
            netPremium: netValue === '' ? null : num(netValue),
            expectedPercent: expectedPct === '' ? null : num(expectedPct),
            expectedComm: typeExpected && expectedTyped !== '' ? num(expectedTyped) : null,
            actualPercent: actualPct === '' ? null : num(actualPct),
            actualComm: null,
            pospShare: noPosp || agreedPct === '' ? null : num(agreedPct),
            pospComm: !noPosp && typePosp && pospTyped !== '' ? num(pospTyped) : null,
            remarks,
        };
        const ok = await run(() => (policy ? api(`/api/insurance/${policy.id}`, { method: 'PUT', body, ifMatch: policy.updatedAt }) : api('/api/insurance', { method: 'POST', body })));
        if (ok)
            onDone();
    }
    return (_jsxs("form", { onSubmit: submit, className: "fd-form", children: [_jsxs("fieldset", { children: [_jsx("legend", { children: "1 \u00B7 Client" }), !lookingUp && policy ? (_jsxs("p", { className: "ok", children: [policy.clientName, " \u00B7 ", _jsx("span", { className: "mono", children: policy.pan ?? 'no PAN' }), " \u00B7 ", policy.clientCode, " \u00B7 RM ", policy.rmName, policy.branchName ? ` · ${policy.branchName}` : '', ' ', _jsx("button", { type: "button", className: "link", onClick: () => setChangeClient(true), children: "Wrong client?" })] })) : (_jsxs("span", { className: "inline-form", children: [_jsxs("label", { children: ["PAN, client code or name", _jsx("input", { value: lookup, onChange: (e) => setLookup(e.target.value.toUpperCase()), onKeyDown: (e) => {
                                            if (e.key === 'Enter') {
                                                e.preventDefault();
                                                findClient();
                                            }
                                        }, placeholder: "ABCDE1234F, FC01234 or a name", autoFocus: true })] }), _jsx("button", { type: "button", className: "secondary", disabled: lookup.trim().length < 3, onClick: findClient, children: "Find" })] })), clientMode === 'pick' && (_jsxs(_Fragment, { children: [_jsx("p", { className: "muted", children: "Pick the client:" }), (matches ?? []).map((c) => (_jsxs("label", { className: "check", children: [_jsx("input", { type: "radio", name: "ins-client", checked: client?.id === c.id, onChange: () => setClient(c) }), c.name, " \u00B7 ", _jsx("span", { className: "mono", children: c.pan ?? 'no PAN' }), " \u00B7 ", c.iwellCode ?? 'OFFLINE', " \u00B7 ", c.rmName ?? 'no RM'] }, c.id)))] })), lookingUp && client && (_jsxs("p", { className: "ok", children: ["\u2713 ", client.name, " \u00B7 ", _jsx("span", { className: "mono", children: client.pan ?? 'no PAN' }), " \u00B7 ", client.iwellCode ?? 'OFFLINE', " \u00B7", ' ', client.rmId ? `RM ${client.rmName}${client.branchName ? ` · ${client.branchName}` : ''}` : 'no RM yet'] })), clientMode === 'new' && (_jsxs(_Fragment, { children: [_jsx("p", { className: "muted", children: "No client has this PAN or code. Check it, or add the client here: they are added to Clients with the RM below." }), _jsxs("label", { children: ["Client name", _jsx("input", { value: newName, onChange: (e) => setNewName(e.target.value), required: true })] }), _jsxs("label", { children: ["PAN", _jsx("input", { value: newPan, onChange: (e) => setNewPan(e.target.value.toUpperCase().replace(/\s/g, '')), maxLength: 10, placeholder: "ABCDE1234F" })] })] })), needsRm && (_jsxs("label", { children: ["RM (saved on the client)", _jsxs("select", { value: rmId, onChange: (e) => setRmId(e.target.value), required: true, children: [_jsx("option", { value: "", children: "Choose\u2026" }), people.map((p) => (_jsx("option", { value: p.id, children: p.branchName ? `${p.name} — ${p.branchName}` : p.name }, p.id)))] })] }))] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "2 \u00B7 Policy" }), _jsxs("div", { className: "chip-row", style: { gridColumn: '1 / -1' }, children: [_jsx("span", { className: "muted", children: "Type of insurance" }), _jsx("span", { className: "segmented", style: { marginBottom: 0 }, children: Object.keys(CATEGORY_LABEL).map((c) => (_jsx("button", { type: "button", className: category === c ? 'on' : undefined, onClick: () => {
                                        if (c === category)
                                            return;
                                        // Another kind of insurance: its own sub types and policy periods.
                                        setCategory(c);
                                        setSubType('');
                                        setPpt('');
                                    }, children: CATEGORY_LABEL[c] }, c))) }), rule && (_jsxs(_Fragment, { children: [_jsx("span", { className: "muted", children: "Business type" }), _jsx("span", { className: "segmented", style: { marginBottom: 0 }, children: rule.businessTypes.map((t) => (_jsx("button", { type: "button", className: businessType === t ? 'on' : undefined, onClick: () => setBusinessType(t), children: TYPE_LABEL[t] }, t))) })] }))] }), _jsxs("label", { children: ["Business month", _jsx("input", { type: "month", value: month.slice(0, 7), onChange: (e) => e.target.value && setMonth(e.target.value), required: true })] }), _jsxs("label", { children: ["Insurance company", _jsxs("select", { value: insurerOptions.includes(insurer) ? insurer : insurer ? OTHER : '', onChange: (e) => setInsurer(e.target.value === OTHER ? ' ' : e.target.value), required: true, children: [_jsx("option", { value: "", children: "Choose\u2026" }), insurerOptions.map((n) => (_jsx("option", { value: n, children: n }, n))), _jsx("option", { value: OTHER, children: "Other\u2026" })] })] }), insurer !== '' && !insurerOptions.includes(insurer) && (_jsxs("label", { children: ["Insurance company (other)", _jsx("input", { value: insurer.trim(), onChange: (e) => setInsurer(e.target.value || ' '), list: "ins-insurers", required: true, autoFocus: true })] })), _jsxs("label", { children: ["Product name", _jsx("input", { value: productName, onChange: (e) => setProductName(e.target.value), list: "ins-products", required: true })] }), _jsxs("label", { children: ["Policy no.", _jsx("input", { value: policyNo, onChange: (e) => setPolicyNo(e.target.value), required: true })] }), _jsx("datalist", { id: "ins-insurers", children: (choices?.insurers ?? []).map((n) => _jsx("option", { value: n }, n)) }), _jsx("datalist", { id: "ins-products", children: (choices?.products ?? []).map((n) => _jsx("option", { value: n }, n)) })] }), rule && (_jsxs("fieldset", { children: [_jsxs("legend", { children: ["3 \u00B7 ", rule.label, " details"] }), _jsxs("label", { children: [rule.subTypeLabel, _jsxs("select", { value: subTypeOptions.includes(subType) ? subType : subType ? OTHER : '', onChange: (e) => setSubType(e.target.value === OTHER ? ' ' : e.target.value), required: !policy || policy.source === 'SCREEN', children: [_jsx("option", { value: "", children: "Choose\u2026" }), subTypeOptions.map((s) => (_jsx("option", { value: s, children: s }, s))), _jsx("option", { value: OTHER, children: "Other\u2026" })] })] }), subType !== '' && !subTypeOptions.includes(subType) && (_jsxs("label", { children: [rule.subTypeLabel, " (other)", _jsx("input", { value: subType.trim(), onChange: (e) => setSubType(e.target.value || ' '), required: true, autoFocus: true })] })), category === 'MOTOR' && (_jsxs("label", { children: ["Cover", _jsxs("select", { value: cover, onChange: (e) => setCover(e.target.value), children: [_jsx("option", { value: "COMP", children: "Comprehensive (OD + TP)" }), _jsx("option", { value: "OD", children: "Own damage only" }), _jsx("option", { value: "TP", children: "Third party only" })] })] })), _jsxs("label", { children: [rule.sumLabel, " (\u20B9)", rule.sumRequired ? '' : ' — optional', _jsx("input", { value: sumAssured, onChange: (e) => setSumAssured(indianDigits(e.target.value)), inputMode: "decimal", required: rule.sumRequired })] }), _jsxs("label", { children: [rule.pptLabel, rule.pptChoices.length ? (_jsx("select", { value: ppt, onChange: (e) => setPpt(e.target.value), required: true, children: [...rule.pptChoices, ...(ppt && !rule.pptChoices.includes(ppt) ? [ppt] : [])].map((p) => (_jsx("option", { value: p, children: p }, p))) })) : (_jsx("input", { value: ppt, onChange: (e) => setPpt(e.target.value), inputMode: "numeric", placeholder: "e.g. 10", required: true }))] }), category === 'LIFE' && (_jsxs("label", { children: ["Policy term (years)", _jsx("input", { type: "number", min: 1, value: policyTerm, onChange: (e) => setPolicyTerm(e.target.value), required: true })] })), category === 'MOTOR' && showOd && (_jsxs("label", { children: ["OD premium (\u20B9)", _jsx("input", { value: od, onChange: (e) => setOd(indianDigits(e.target.value)), inputMode: "decimal", required: true })] })), category === 'MOTOR' && showTp && (_jsxs("label", { children: ["TP premium (\u20B9)", _jsx("input", { value: tp, onChange: (e) => setTp(indianDigits(e.target.value)), inputMode: "decimal", required: true })] }))] })), _jsxs("fieldset", { children: [_jsxs("legend", { children: [rule ? '4' : '3', " \u00B7 Premium and commission"] }), _jsxs("label", { children: ["Total premium (\u20B9, with GST)", _jsx("input", { value: total, onChange: (e) => setTotal(indianDigits(e.target.value)), inputMode: "decimal", required: true })] }), _jsxs("label", { children: ["Net premium (\u20B9, without GST)", _jsx("input", { value: netValue, onChange: (e) => {
                                    setNetEdited(true);
                                    setNet(indianDigits(e.target.value));
                                }, inputMode: "decimal", required: true })] }), rule && !netEdited && autoNet != null && (_jsxs("p", { className: "muted", children: [rule.premium === 'OD_TP' ? 'Net = OD + TP premium.' : rule.premium === 'TOTAL' ? `Net = total (no GST on ${rule.label.toLowerCase()}).` : `Net = total less ${rule.gstPercent}% GST.`, " Type over it if the insurer's figure differs."] })), _jsxs("label", { children: ["Expected commission %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.0001", value: expectedPct, onChange: (e) => setExpectedPct(e.target.value), required: !typeExpected })] }), typeExpected && (_jsxs("label", { children: ["Expected commission (\u20B9, typed)", _jsx("input", { type: "number", step: "0.01", value: expectedTyped, onChange: (e) => setExpectedTyped(e.target.value), required: true })] })), _jsxs("label", { children: ["POSP (blank or NA = none)", _jsx("input", { value: posp, onChange: (e) => setPosp(e.target.value), list: "ins-posps", placeholder: "Pick or type a new POSP" })] }), _jsx("datalist", { id: "ins-posps", children: posps.map((p) => _jsx("option", { value: p.name }, p.id)) }), !noPosp && (_jsxs("label", { children: ["% agreed with the POSP", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: agreedPct, onChange: (e) => setAgreedPct(e.target.value), required: !typePosp })] })), !noPosp && typePosp && (_jsxs("label", { children: ["POSP commission (\u20B9, typed)", _jsx("input", { type: "number", step: "0.01", value: pospTyped, onChange: (e) => setPospTyped(e.target.value), required: true })] })), !noPosp && fig.posp > fig.expected && _jsx("p", { className: "error", children: "The POSP's commission is more than the expected commission: check the amounts (it is saved as typed)." }), !noPosp && !pospMatch && _jsxs("p", { className: "muted", children: ["New POSP: \u201C", posp.trim(), "\u201D is added as a POSP partner when you save."] }), _jsxs("div", { className: "figures", children: [_jsxs("div", { className: "figure key", children: [_jsxs("span", { children: ["Expected commission", typeExpected ? ' (typed)' : ''] }), _jsx("b", { children: formatMoney(fig.expected) })] }), !noPosp && (_jsxs("div", { className: "figure", children: [_jsx("span", { children: "POSP % of premium" }), _jsx("b", { children: fig.pospPct != null ? `${round2(fig.pospPct * 10000) / 10000}%` : '—' })] })), !noPosp && (_jsxs("div", { className: "figure", children: [_jsxs("span", { children: ["POSP commission", typePosp ? ' (typed)' : ''] }), _jsx("b", { children: formatMoney(fig.posp) })] })), _jsxs("div", { className: "figure", children: [_jsx("span", { children: "Epsilon keeps" }), _jsx("b", { children: formatMoney(fig.ours) })] })] }), _jsxs("p", { className: "muted", children: [_jsx("button", { type: "button", className: "link", onClick: () => setTypeExpected(!typeExpected), children: typeExpected ? 'Work out the expected commission from the %' : 'Type the expected commission amount instead' }), !noPosp && (_jsxs(_Fragment, { children: [' · ', _jsx("button", { type: "button", className: "link", onClick: () => setTypePosp(!typePosp), children: typePosp ? "Work out the POSP's commission" : "Type the POSP's commission instead" })] }))] }), _jsxs("details", { style: { gridColumn: '1 / -1' }, open: !!(policy?.actualPercent != null || policy?.remarks), children: [_jsx("summary", { className: "muted", children: "Actual commission and remarks (optional, can be added later)" }), _jsxs("div", { className: "inline-form", style: { flexWrap: 'wrap', marginTop: 8 }, children: [_jsxs("label", { children: ["Actual commission %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.0001", value: actualPct, onChange: (e) => setActualPct(e.target.value) })] }), _jsxs("label", { style: { flex: 1 }, children: ["Remarks", _jsx("input", { value: remarks, onChange: (e) => setRemarks(e.target.value) })] })] })] })] }), error && _jsx("p", { className: "error", children: error }), _jsxs("span", { className: "inline-form", children: [_jsx("button", { type: "submit", disabled: busy || clientMode === 'lookup' || clientMode === 'pick' || !category || !businessType, children: policy ? 'Save changes' : 'Add policy' }), (!category || !businessType) && _jsx("span", { className: "muted", children: "Choose the category and business type." }), policy && _jsx(CancelPolicy, { id: policy.id, onDone: onDone })] })] }));
}
function CancelPolicy({ id, onDone }) {
    const { busy, run } = useSubmit();
    return (_jsx("button", { type: "button", className: "link", disabled: busy, onClick: async () => {
            const reason = window.prompt('Why cancel this policy?');
            if (reason === null)
                return;
            if (await run(() => api(`/api/insurance/${id}/cancel`, { method: 'POST', body: { reason } })))
                onDone();
        }, children: "Cancel this policy" }));
}
/** The month's "Policy level data" sheet: rows are mapped to clients by PAN; those that cannot be are listed by row. */
function UploadPolicies({ onDone }) {
    const [file, setFile] = useState(null);
    const [month, setMonth] = useState(thisMonth());
    const [result, setResult] = useState(null);
    const { busy, error, run } = useSubmit();
    async function submit(e) {
        e.preventDefault();
        if (!file)
            return;
        const form = new FormData();
        form.append('file', file);
        form.append('month', `${month}-01`);
        await run(async () => setResult(await api('/api/insurance/upload', { method: 'POST', body: form })));
    }
    if (result) {
        return (_jsxs("div", { children: [_jsxs("p", { className: "ok", children: [result.saved, " of ", result.rows, " policies saved for ", formatMonth(result.month), ": expected commission ", formatMoney(result.expectedTotal), ", POSP", ' ', formatMoney(result.pospTotal), "."] }), result.replaced > 0 && _jsxs("p", { className: "muted", children: [result.replaced, " policies from the earlier upload of this month were replaced."] }), result.newPosps > 0 && _jsxs("p", { className: "muted", children: [result.newPosps, " new POSPs were added as POSP partners."] }), result.byName > 0 && (_jsxs("p", { className: "warn-note", children: [result.byName, " policies had no PAN and went to the one client with that name. Confirm each is the right person on", ' ', _jsx(Link, { to: `/month?m=${result.month}`, children: "Month close" }), " (step 3) before the month is processed."] })), result.notSaved.length > 0 && (_jsxs(_Fragment, { children: [_jsxs("p", { className: "error", children: [result.notSaved.length, " rows were not saved. Fix them (Clients screen or the sheet) and upload again, or add those policies by hand:"] }), _jsx("div", { className: "table-scroll", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { className: "num", children: "Row" }), _jsx("th", { children: "Client (as in the sheet)" }), _jsx("th", { children: "PAN" }), _jsx("th", { children: "Why" })] }) }), _jsx("tbody", { children: result.notSaved.map((r) => (_jsxs("tr", { children: [_jsx("td", { className: "num", children: r.row }), _jsx("td", { children: r.client ?? '' }), _jsx("td", { className: "mono", children: r.pan ?? '—' }), _jsx("td", { children: r.reason })] }, r.row))) })] }) })] })), _jsx("button", { type: "button", onClick: onDone, children: "Done" })] }));
    }
    return (_jsxs("form", { onSubmit: submit, className: "fd-form", children: [_jsx("p", { className: "muted", children: "The insurance team's monthly file, sheet \u201CPolicy level data\u201D (Business Month, Policy No, POSP Name, Insurance Company, CAT, Client, PAN, \u2026). Each row is matched to a client by PAN; the RM comes from the client master, not the sheet. Uploading the month again replaces its earlier upload; policies added by hand stay." }), _jsxs("label", { children: ["Business month", _jsx("input", { type: "month", value: month, onChange: (e) => e.target.value && setMonth(e.target.value), required: true })] }), _jsxs("label", { children: ["File", _jsx("input", { type: "file", accept: ".xlsx", onChange: (e) => setFile(e.target.files?.[0] ?? null), required: true })] }), error && _jsx("p", { className: "error", children: error }), _jsx("button", { type: "submit", disabled: busy || !file, children: "Upload" })] }));
}
