import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useState } from 'react';
import { api } from '../../api/client';
import { useLoad } from '../../api/useLoad';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { DatedChangeForm } from '../../components/DatedChangeForm';
import { NameForm } from '../../components/NameForm';
import { SidePanel } from '../../components/SidePanel';
import { formatDate } from '../../format';
/** Setup → Platforms: where deals are booked (GoldenPi, SMC…) and their brokerage terms, changed "from a date" (R-75). */
export function PlatformsPage() {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const list = useLoad('/api/platforms');
    const [open, setOpen] = useState(null);
    const platform = typeof open === 'number' ? list.data?.find((p) => p.id === open) : undefined;
    return (_jsxs("section", { children: [_jsxs("div", { className: "page-head", children: [_jsx("h1", { children: "Platforms" }), canEdit && (_jsx("div", { className: "actions", children: _jsx("button", { type: "button", onClick: () => setOpen('new'), children: "Add platform" }) }))] }), _jsx("p", { className: "muted", children: "Each platform's brokerage terms: whether its brokerage already includes GST, the TDS it deducts, and the share of the mark-up it pays Epsilon. A bond deal takes the terms of its month." }), list.error && _jsx("p", { className: "error", children: list.error }), list.data && list.data.length === 0 && _jsx("p", { className: "muted", children: "No platforms yet." }), list.data && list.data.length > 0 && (_jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Platform" }), _jsx("th", { children: "Brokerage" }), _jsx("th", { className: "num", children: "TDS %" }), _jsx("th", { className: "num", children: "Our share %" }), _jsx("th", { children: "Since" })] }) }), _jsx("tbody", { children: list.data.map((p) => (_jsxs("tr", { children: [_jsx("td", { children: _jsx("button", { type: "button", className: "cell-link", onClick: () => setOpen(p.id), children: p.name }) }), _jsx("td", { children: p.now ? (p.now.gstInclusive ? 'Includes GST' : 'GST on top') : _jsx("span", { className: "muted", children: "no terms" }) }), _jsx("td", { className: "num", children: p.now?.tdsPercent ?? '' }), _jsx("td", { className: "num", children: p.now?.ourShare ?? '' }), _jsx("td", { children: p.now ? formatDate(p.now.validFrom) : '' })] }, p.id))) })] })), open === 'new' && (_jsx(SidePanel, { title: "Add platform", onClose: () => setOpen(null), children: _jsx(NameForm, { label: "Platform name", submitLabel: "Add platform", onSubmit: (name) => api('/api/platforms', { method: 'POST', body: { name } }), onDone: () => {
                        setOpen(null);
                        list.reload();
                    } }) })), platform && _jsx(TermsPanel, { platform: platform, canEdit: canEdit, onClose: () => setOpen(null), onChanged: list.reload })] }));
}
function TermsPanel({ platform, canEdit, onClose, onChanged }) {
    const history = useLoad(`/api/platforms/${platform.id}/terms`);
    const [inclusive, setInclusive] = useState(platform.now?.gstInclusive ?? false);
    const [tds, setTds] = useState(String(platform.now?.tdsPercent ?? 5));
    const [share, setShare] = useState(String(platform.now?.ourShare ?? 100));
    const [reason, setReason] = useState('');
    return (_jsxs(SidePanel, { title: platform.name, onClose: onClose, children: [canEdit && (_jsxs(_Fragment, { children: [_jsxs(DatedChangeForm, { title: platform.now ? 'Change terms' : 'Set terms', dateLabel: "From", submitLabel: "Save", canSubmit: tds !== '' && share !== '', onSubmit: (from) => api(`/api/platforms/${platform.id}/terms`, {
                            method: 'POST',
                            body: { gstInclusive: inclusive, tdsPercent: Number(tds), ourShare: Number(share), from, reason },
                        }), onDone: () => {
                            setReason('');
                            history.reload();
                            onChanged();
                        }, children: [_jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: inclusive, onChange: (e) => setInclusive(e.target.checked) }), "Brokerage includes GST (gross = brokerage \u00F7 1.18)"] }), _jsxs("label", { children: ["TDS %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: tds, onChange: (e) => setTds(e.target.value), required: true })] }), _jsxs("label", { children: ["Our share of the mark-up %", _jsx("input", { type: "number", min: 0.01, max: 100, step: "0.01", value: share, onChange: (e) => setShare(e.target.value), required: true })] }), _jsxs("label", { children: ["Reason", _jsx("input", { value: reason, onChange: (e) => setReason(e.target.value) })] })] }), _jsx(NameForm, { label: "Rename platform", initial: platform.name, submitLabel: "Rename", onSubmit: (name) => api(`/api/platforms/${platform.id}`, { method: 'PATCH', body: { name } }), onDone: onChanged }, platform.name)] })), _jsx("h3", { children: "History" }), history.data && history.data.length === 0 && _jsx("p", { className: "muted", children: "No terms yet." }), history.data && history.data.length > 0 && (_jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "From" }), _jsx("th", { children: "To" }), _jsx("th", { children: "GST" }), _jsx("th", { className: "num", children: "TDS %" }), _jsx("th", { className: "num", children: "Share %" })] }) }), _jsx("tbody", { children: history.data.map((h) => (_jsxs("tr", { children: [_jsx("td", { children: formatDate(h.validFrom) }), _jsx("td", { children: h.validTo ? formatDate(h.validTo) : 'now' }), _jsx("td", { children: h.gstInclusive ? 'incl.' : 'on top' }), _jsx("td", { className: "num", children: h.tdsPercent }), _jsx("td", { className: "num", children: h.ourShare })] }, h.id))) })] }))] }));
}
