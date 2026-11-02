import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useCallback, useMemo, useState } from 'react';
import { api } from '../../api/client';
import { PARTNER_STATUS_LABEL, PARTNER_TYPE_LABEL, PARTNER_TYPES, PRODUCTS, } from '../../api/types';
import { formatDate } from '../../format';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { DatedChangeForm } from '../../components/DatedChangeForm';
import { HistoryTable } from '../../components/HistoryTable';
import { SidePanel } from '../../components/SidePanel';
export function PartnersPage() {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const partners = useLoad('/api/partners');
    const counts = useLoad('/api/shares/partner-counts');
    const rates = useLoad('/api/partners/product-percents');
    const [type, setType] = useState('');
    const [search, setSearch] = useState('');
    const [panel, setPanel] = useState(null);
    const close = useCallback(() => setPanel(null), []);
    const rows = useMemo(() => {
        const q = search.trim().toLowerCase();
        return (partners.data ?? []).filter((p) => (!type || p.type === type) && (!q || p.name.toLowerCase().includes(q)));
    }, [partners.data, type, search]);
    const selected = typeof panel === 'number' ? partners.data?.find((p) => p.id === panel) : undefined;
    return (_jsxs("section", { children: [_jsxs("div", { className: "page-head", children: [_jsx("h1", { children: "Partners" }), canEdit && (_jsx("button", { type: "button", onClick: () => setPanel('new'), children: "New partner" }))] }), _jsxs("div", { className: "filters", children: [_jsxs("label", { children: ["Type", _jsxs("select", { value: type, onChange: (e) => setType(e.target.value), children: [_jsx("option", { value: "", children: "All" }), PARTNER_TYPES.map((t) => (_jsx("option", { value: t, children: PARTNER_TYPE_LABEL[t] }, t)))] })] }), _jsxs("label", { children: ["Search", _jsx("input", { value: search, onChange: (e) => setSearch(e.target.value), placeholder: "Name" })] })] }), partners.error && _jsx("p", { className: "error", children: partners.error }), partners.data && partners.data.length === 0 && _jsx("p", { className: "muted", children: "No partners yet. Add one, or use Import." }), partners.data && partners.data.length > 0 && (_jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Type" }), _jsx("th", { children: "Name" }), _jsx("th", { children: "PAN" }), _jsx("th", { children: "ARN / POSP code" }), _jsx("th", { children: "Clients now" }), _jsx("th", { children: "MF %" }), _jsx("th", { children: "Status" })] }) }), _jsx("tbody", { children: rows.map((p) => (_jsxs("tr", { className: "clickable", onClick: () => setPanel(p.id), children: [_jsx("td", { children: PARTNER_TYPE_LABEL[p.type] }), _jsx("td", { children: p.name }), _jsx("td", { className: "mono", children: p.pan }), _jsx("td", { children: p.arnOrPospCode }), _jsx("td", { className: "num", children: counts.data?.[p.id] ?? 0 }), _jsx("td", { className: "num", children: rates.data?.[p.id]?.MF ?? _jsx("span", { className: "muted", children: "not set" }) }), _jsx("td", { children: _jsx(StatusText, { status: p.status }) })] }, p.id))) })] })), panel === 'new' && (_jsx(SidePanel, { title: "New partner", onClose: close, children: _jsx(PartnerForm, { onDone: () => {
                        close();
                        partners.reload();
                    } }) })), selected && (_jsx(SidePanel, { title: selected.name, onClose: close, children: _jsx(PartnerDetail, { partner: selected, others: (partners.data ?? []).filter((p) => p.id !== selected.id && p.type === selected.type && p.status === 'ACTIVE'), canEdit: canEdit, onChanged: () => {
                        partners.reload();
                        counts.reload();
                        rates.reload();
                    } }) }))] }));
}
/** Create (no partner) or edit. PAN: leave blank to keep the stored one. */
function PartnerForm({ partner, onDone }) {
    const [type, setType] = useState(partner?.type ?? 'SUB_BROKER');
    const [name, setName] = useState(partner?.name ?? '');
    const [pan, setPan] = useState('');
    const [gstin, setGstin] = useState(partner?.gstin ?? '');
    const [code, setCode] = useState(partner?.arnOrPospCode ?? '');
    const [bankRef, setBankRef] = useState(partner?.bankRef ?? '');
    const [status, setStatus] = useState(partner?.status ?? 'ACTIVE');
    const { busy, error, run } = useSubmit();
    async function submit(e) {
        e.preventDefault();
        const body = {
            name,
            pan: pan.trim() === '' ? undefined : pan,
            gstin,
            arnOrPospCode: code,
            bankRef,
            status,
        };
        const ok = await run(() => partner
            ? api(`/api/partners/${partner.id}`, { method: 'PATCH', body })
            : api('/api/partners', { method: 'POST', body: { ...body, type } }));
        if (ok) {
            setPan('');
            onDone();
        }
    }
    return (_jsxs("form", { onSubmit: submit, children: [!partner && (_jsxs("label", { children: ["Type", _jsx("select", { value: type, onChange: (e) => setType(e.target.value), children: PARTNER_TYPES.map((t) => (_jsx("option", { value: t, children: PARTNER_TYPE_LABEL[t] }, t))) })] })), _jsxs("label", { children: ["Name", _jsx("input", { value: name, onChange: (e) => setName(e.target.value), required: true })] }), _jsxs("label", { children: ["PAN", _jsx("input", { value: pan, onChange: (e) => setPan(e.target.value), placeholder: partner?.pan ? `${partner.pan} (leave blank to keep)` : 'ABCDE1234F' })] }), _jsxs("label", { children: ["GSTIN", _jsx("input", { value: gstin, onChange: (e) => setGstin(e.target.value) })] }), _jsxs("label", { children: ["ARN / POSP code", _jsx("input", { value: code, onChange: (e) => setCode(e.target.value) })] }), _jsxs("label", { children: ["Bank reference", _jsx("input", { value: bankRef, onChange: (e) => setBankRef(e.target.value) })] }), _jsxs("label", { children: ["Status", _jsx("select", { value: status, onChange: (e) => setStatus(e.target.value), children: Object.keys(PARTNER_STATUS_LABEL).map((s) => (_jsx("option", { value: s, children: PARTNER_STATUS_LABEL[s] }, s))) })] }), status === 'PENDING' && _jsx("p", { className: "muted", children: "Pending approval: stays linked to its clients but is paid nothing until set to Active." }), error && _jsx("p", { className: "error", children: error }), _jsx("button", { type: "submit", disabled: busy, children: partner ? 'Save details' : 'Create partner' })] }));
}
function PartnerDetail({ partner, others, canEdit, onChanged }) {
    const links = useLoad(`/api/partners/${partner.id}/rm-links`);
    const employees = useLoad(canEdit ? '/api/employees' : null);
    const [rmId, setRmId] = useState('');
    const refresh = () => {
        links.reload();
        onChanged();
    };
    const linkedNow = new Set((links.data ?? []).filter((l) => l.validTo === null).map((l) => l.rmId));
    const rms = (employees.data ?? []).filter((e) => e.type === 'RM' && e.status === 'ACTIVE' && !linkedNow.has(e.id));
    return (_jsxs(_Fragment, { children: [canEdit ? (_jsx(PartnerForm, { partner: partner, onDone: onChanged }, partner.id + partner.name + partner.status)) : (_jsxs("p", { children: [PARTNER_TYPE_LABEL[partner.type], " \u00B7 PAN ", partner.pan ?? 'not set', " \u00B7 GSTIN ", partner.gstin ?? 'not set'] })), _jsx(RateCard, { partner: partner, canEdit: canEdit, onChanged: onChanged }), _jsx("h3", { children: "RMs this partner works with" }), links.error && _jsx("p", { className: "error", children: links.error }), links.data && (_jsx(HistoryTable, { label: "RM", rows: links.data.map((l) => ({ ...l, name: `${l.rmName} (${l.rmCode})` })), actions: canEdit
                    ? (row) => (row.validTo === null ? _jsx(EndLinkButton, { partnerId: partner.id, linkId: row.id, onDone: refresh }) : null)
                    : undefined })), canEdit && partner.status !== 'INACTIVE' && (_jsx(DatedChangeForm, { title: "Link an RM", dateLabel: "From", submitLabel: "Link RM", canSubmit: rmId !== '', onSubmit: (from) => api(`/api/partners/${partner.id}/rm-links`, { method: 'POST', body: { rmId: Number(rmId), from } }), onDone: () => {
                    setRmId('');
                    refresh();
                }, children: _jsxs("label", { children: ["RM", _jsxs("select", { value: rmId, onChange: (e) => setRmId(e.target.value), required: true, children: [_jsx("option", { value: "", children: "Choose\u2026" }), rms.map((e) => (_jsxs("option", { value: e.id, children: [e.name, " (", e.empCode, ")"] }, e.id)))] })] }) })), canEdit && partner.status === 'ACTIVE' && others.length > 0 && _jsx(MergeForm, { partner: partner, others: others, onDone: onChanged })] }));
}
function StatusText({ status }) {
    if (status === 'ACTIVE')
        return _jsx(_Fragment, { children: "Active" });
    if (status === 'PENDING')
        return _jsx("span", { className: "badge warn-badge", children: "Pending approval" });
    return _jsx("span", { className: "muted", children: "Inactive" });
}
/**
 * The partner's % per product, from a date. Its clients follow these unless a client has its own % (Clients screen).
 * Insurance stays 0 for sub-brokers and referral partners: POSPs get the insurance share.
 */
function RateCard({ partner, canEdit, onChanged }) {
    const history = useLoad(`/api/partners/${partner.id}/products`);
    const [product, setProduct] = useState('MF');
    const [percent, setPercent] = useState('');
    const current = new Map((history.data ?? []).filter((s) => s.validTo === null).map((s) => [s.product, s]));
    const past = (history.data ?? []).filter((s) => s.validTo !== null);
    return (_jsxs("div", { className: "card-block", children: [_jsx("h3", { children: "Sharing % by product" }), history.error && _jsx("p", { className: "error", children: history.error }), _jsxs("table", { className: "compact", children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Product" }), _jsx("th", { className: "num", children: "%" }), _jsx("th", { children: "From" })] }) }), _jsx("tbody", { children: PRODUCTS.map((p) => {
                            const s = current.get(p.code);
                            return (_jsxs("tr", { children: [_jsx("td", { children: p.label }), _jsx("td", { className: "num", children: s ? `${s.percent}%` : _jsx("span", { className: "muted", children: "not set" }) }), _jsx("td", { children: s ? formatDate(s.validFrom) : '' })] }, p.code));
                        }) })] }), past.length > 0 && (_jsxs("details", { children: [_jsxs("summary", { children: ["Earlier %s (", past.length, ")"] }), _jsx("table", { className: "compact", children: _jsx("tbody", { children: past.map((s) => (_jsxs("tr", { children: [_jsx("td", { children: PRODUCTS.find((p) => p.code === s.product)?.label ?? s.product }), _jsxs("td", { className: "num", children: [s.percent, "%"] }), _jsxs("td", { children: [formatDate(s.validFrom), " \u2013 ", formatDate(s.validTo)] })] }, s.id))) }) })] })), canEdit && (_jsxs(DatedChangeForm, { title: "Change a product %", dateLabel: "From", submitLabel: "Save %", canSubmit: percent !== '', onSubmit: (from) => api(`/api/partners/${partner.id}/products`, { method: 'POST', body: { product, percent: Number(percent), from } }), onDone: () => {
                    setPercent('');
                    history.reload();
                    onChanged();
                }, children: [_jsxs("label", { children: ["Product", _jsx("select", { value: product, onChange: (e) => setProduct(e.target.value), children: PRODUCTS.map((p) => (_jsx("option", { value: p.code, children: p.label }, p.code))) })] }), _jsxs("label", { children: ["Sharing %", _jsx("input", { type: "number", min: "0", max: "100", step: "0.01", value: percent, onChange: (e) => setPercent(e.target.value), required: true })] }), _jsx("p", { className: "muted", children: "Clients with their own % keep it. From the % row\u2019s first day, the % is corrected instead." })] }))] }));
}
/** A duplicate (another spelling of the same person) hands its clients to the right partner and becomes inactive. */
function MergeForm({ partner, others, onDone }) {
    const [intoId, setIntoId] = useState('');
    const [result, setResult] = useState(null);
    const { busy, error, run } = useSubmit();
    const into = others.find((p) => String(p.id) === intoId);
    async function submit(e) {
        e.preventDefault();
        if (!into || !window.confirm(`Merge ${partner.name} into ${into.name}? Its clients move to ${into.name} and ${partner.name} becomes inactive.`))
            return;
        await run(async () => {
            const r = await api(`/api/partners/${partner.id}/merge`, { method: 'POST', body: { intoId: Number(intoId), reason: 'Duplicate spelling' } });
            setResult(`Merged: ${r.shares} client share(s) moved to ${into.name}.`);
            onDone();
        });
    }
    return (_jsxs("form", { onSubmit: submit, children: [_jsx("h3", { children: "Duplicate of another partner?" }), _jsx("p", { className: "muted", children: "Merging moves this partner's clients to the right one and remembers this spelling for the next file." }), _jsxs("label", { children: ["Merge into", _jsxs("select", { value: intoId, onChange: (e) => setIntoId(e.target.value), required: true, children: [_jsx("option", { value: "", children: "Choose\u2026" }), others.map((p) => (_jsx("option", { value: p.id, children: p.name }, p.id)))] })] }), error && _jsx("p", { className: "error", children: error }), result && _jsx("p", { children: result }), _jsx("button", { type: "submit", className: "secondary", disabled: busy || !intoId, children: "Merge" })] }));
}
/** Ends a current link, asking for the last day inline. */
function EndLinkButton({ partnerId, linkId, onDone }) {
    const [open, setOpen] = useState(false);
    const [lastDay, setLastDay] = useState('');
    const { busy, error, run } = useSubmit();
    if (!open) {
        return (_jsx("button", { type: "button", className: "link", onClick: () => setOpen(true), children: "End\u2026" }));
    }
    return (_jsxs("span", { className: "inline-form", children: [_jsx("input", { type: "date", value: lastDay, onChange: (e) => setLastDay(e.target.value), "aria-label": "Last day" }), _jsx("button", { type: "button", className: "secondary", disabled: busy || !lastDay, onClick: async () => {
                    if (await run(() => api(`/api/partners/${partnerId}/rm-links/${linkId}/end`, { method: 'POST', body: { lastDay } })))
                        onDone();
                }, children: "End" }), error && _jsx("span", { className: "error", children: error })] }));
}
