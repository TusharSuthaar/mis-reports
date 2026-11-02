import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { MfBrokerage } from './MfBrokerage';
import { OpsMaster } from './OpsMaster';
import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api } from '../../api/client';
import { PARTNER_TYPE_LABEL } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { DatedChangeForm } from '../../components/DatedChangeForm';
import { HistoryTable } from '../../components/HistoryTable';
import { SidePanel } from '../../components/SidePanel';
import { Tabs, useTab } from '../../components/Tabs';
import { Amount } from '../../components/Amount';
import { formatDate, formatDateTime, todayIso, formatMonth } from '../../format';
const PAGE_SIZE = 50;
export function ClientsPage() {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const [urlParams, setUrlParams] = useSearchParams();
    const navigate = useNavigate();
    const [search, setSearch] = useState(urlParams.get('q') ?? '');
    const [query, setQuery] = useState(urlParams.get('q') ?? '');
    const show = (urlParams.get('show') ?? '');
    const setShow = (v) => {
        const next = new URLSearchParams(urlParams);
        if (v)
            next.set('show', v);
        else
            next.delete('show');
        setUrlParams(next, { replace: true });
    };
    const [adding, setAdding] = useState(false);
    const attention = useLoad('/api/clients/attention');
    const [page, setPage] = useState(0);
    const [selected, setSelected] = useState(new Set());
    const employees = useLoad('/api/employees');
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE), needsRm: String(show === 'NO_RM') });
    if (show === 'SHARED_PAN' || show === 'NO_PAN')
        params.set('attention', show);
    if (query)
        params.set('q', query);
    const clients = useLoad(`/api/clients?${params}`);
    const ids = clients.data?.items.map((c) => c.id).join(',') ?? '';
    const shares = useLoad(ids ? `/api/shares?clientIds=${ids}` : null);
    // search as you type, without a request per keystroke
    useEffect(() => {
        const t = window.setTimeout(() => {
            setPage(0);
            setQuery(search.trim());
        }, 300);
        return () => window.clearTimeout(t);
    }, [search]);
    const rms = (employees.data ?? []).filter((e) => e.status === 'ACTIVE');
    const data = clients.data;
    const lastPage = data ? Math.max(0, Math.ceil(data.total / PAGE_SIZE) - 1) : 0;
    const toggle = (id) => setSelected((s) => {
        const next = new Set(s);
        if (next.has(id))
            next.delete(id);
        else
            next.add(id);
        return next;
    });
    return (_jsxs("section", { children: [_jsxs("div", { className: "page-head", children: [_jsxs("div", { children: [_jsx("h1", { children: "Clients" }), _jsx("p", { className: "muted", style: { margin: 0 }, children: "The client master: one record per PAN, with its RM and partners. Open a client to change anything about it." })] }), canEdit && (_jsx("button", { type: "button", onClick: () => setAdding(true), children: "Add client" }))] }), _jsxs("div", { className: "filters list-filters", children: [_jsxs("label", { className: "grow", children: ["Search", _jsx("input", { value: search, onChange: (e) => setSearch(e.target.value), placeholder: "Name, client code or full PAN" })] }), _jsx("div", { className: "chip-row", role: "group", "aria-label": "Show", children: [
                            ['', 'All clients', null],
                            ['NO_RM', 'Without an RM', attention.data?.noRm],
                            ['SHARED_PAN', 'PAN on several records', attention.data?.sharedPan],
                            ['NO_PAN', 'Without a PAN', attention.data?.noPan],
                        ].map(([key, text, n]) => (_jsxs("button", { type: "button", className: show === key ? 'chip active' : 'chip', "aria-pressed": show === key, onClick: () => {
                                setPage(0);
                                setShow(key);
                            }, children: [text, n != null && _jsx("span", { className: "count", children: n })] }, key || 'all'))) })] }), show === 'SHARED_PAN' && (_jsx("p", { className: "muted", children: "One PAN, several records (old data). Usually a minor carries the guardian's PAN: open the minor and use \u201CIt's the guardian's PAN\u201D. A duplicate made by a file: open the right client, Edit PAN, and move it." })), canEdit && selected.size > 0 && (_jsx(BulkRmBar, { ids: [...selected], rms: rms, onDone: () => {
                    setSelected(new Set());
                    clients.reload();
                } })), clients.error && _jsx("p", { className: "error", children: clients.error }), data && data.total === 0 && _jsx("p", { className: "muted", children: query || show ? 'No clients match.' : 'No clients yet. Load them from the MF file under Setup → Load clients.' }), data && data.total > 0 && (_jsxs(_Fragment, { children: [_jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [canEdit && (_jsx("th", { children: _jsx("input", { type: "checkbox", "aria-label": "Select all on this page", checked: data.items.every((c) => selected.has(c.id)), onChange: (e) => setSelected(e.target.checked ? new Set([...selected, ...data.items.map((c) => c.id)]) : new Set()) }) })), _jsx("th", { children: "Code" }), _jsx("th", { children: "Client" }), _jsx("th", { children: "PAN" }), _jsx("th", { children: "RM" }), _jsx("th", { children: "Branch" }), _jsx("th", { children: "Partner" })] }) }), _jsx("tbody", { children: data.items.map((c) => (_jsxs("tr", { children: [canEdit && (_jsx("td", { children: _jsx("input", { type: "checkbox", "aria-label": `Select ${c.name}`, checked: selected.has(c.id), onChange: () => toggle(c.id) }) })), _jsx("td", { className: "mono", children: c.iwellCode }), _jsx("td", { children: _jsx(Link, { to: `/clients/${c.id}`, className: "cell-link", children: c.name }) }), _jsx("td", { className: "mono", children: c.pan }), _jsx("td", { children: c.rmName ?? _jsx("span", { className: "badge warn-badge", children: "needs RM" }) }), _jsx("td", { children: c.branchName }), _jsx("td", { children: shareText(shares.data?.[c.id]) })] }, c.id))) })] }), _jsxs("div", { className: "pager", children: [_jsx("button", { type: "button", className: "secondary", disabled: page === 0, onClick: () => setPage(page - 1), children: "Previous" }), _jsxs("span", { children: ["Page ", page + 1, " of ", lastPage + 1, " \u00B7 ", data.total, " clients"] }), _jsx("button", { type: "button", className: "secondary", disabled: page >= lastPage, onClick: () => setPage(page + 1), children: "Next" })] })] })), adding && (_jsx(SidePanel, { title: "Add client", onClose: () => setAdding(false), children: _jsx(AddClientForm, { rms: rms, onDone: (id) => {
                        setAdding(false);
                        clients.reload();
                        attention.reload();
                        navigate(`/clients/${id}`);
                    } }) }))] }));
}
/** A client not in any file yet: name, PAN (must be new), RM from a date. */
function AddClientForm({ rms, onDone }) {
    const [name, setName] = useState('');
    const [pan, setPan] = useState('');
    const [rmId, setRmId] = useState('');
    const [from, setFrom] = useState(todayIso());
    const [reason, setReason] = useState('');
    const { busy, error, run } = useSubmit();
    async function submit(e) {
        e.preventDefault();
        let id = 0;
        const ok = await run(async () => {
            const r = await api('/api/clients', {
                method: 'POST',
                body: { name, pan: pan.trim() || null, rmId: Number(rmId), from, reason },
            });
            id = r.id;
        });
        if (ok)
            onDone(id);
    }
    return (_jsxs("form", { onSubmit: submit, children: [_jsxs("label", { children: ["Client name", _jsx("input", { value: name, onChange: (e) => setName(e.target.value), required: true })] }), _jsxs("label", { children: ["PAN ", _jsx("span", { className: "muted", children: "(leave blank for a minor: matched by guardian PAN)" }), _jsx("input", { value: pan, onChange: (e) => setPan(e.target.value.toUpperCase()), placeholder: "ABCDE1234F", maxLength: 10 })] }), _jsx(RmSelect, { rms: rms, value: rmId, onChange: setRmId }), _jsxs("label", { children: ["RM from", _jsx("input", { type: "date", value: from, onChange: (e) => setFrom(e.target.value), required: true })] }), _jsxs("label", { children: ["Reason", _jsx("input", { value: reason, onChange: (e) => setReason(e.target.value), placeholder: "e.g. new account" })] }), error && _jsx("p", { className: "error", children: error }), _jsx("button", { type: "submit", disabled: busy || !name.trim() || !rmId, children: "Add client" })] }));
}
/** The record's own audited changes: added, PAN corrections (who, when, why). */
function RecordChanges({ clientId }) {
    const changes = useLoad(`/api/clients/${clientId}/changes`);
    if (!changes.data || changes.data.length === 0)
        return null;
    return (_jsxs("details", { className: "past", children: [_jsxs("summary", { children: ["Changes to this record (", changes.data.length, ")"] }), _jsxs("table", { className: "compact", children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "When" }), _jsx("th", { children: "What" }), _jsx("th", { children: "By" })] }) }), _jsx("tbody", { children: changes.data.map((a) => (_jsxs("tr", { children: [_jsx("td", { children: formatDateTime(a.at) }), _jsxs("td", { children: [a.action === 'CREATE' ? 'Added' : a.action === 'PAN_CHANGE' ? `PAN → ${a.after?.maskedPan ?? 'none'}` : a.action, a.after?.reason && _jsx("div", { className: "muted", children: a.after.reason })] }), _jsx("td", { children: a.by })] }, a.id))) })] })] }));
}
/** "ALPHA WEALTH 50% (Sub-broker) + PARTNER TWO 20% (Referral partner)" */
function shareText(list) {
    if (!list || list.length === 0)
        return _jsx("span", { className: "muted", children: "\u2014" });
    return list.map((s, i) => (_jsxs("span", { children: [i > 0 && ' + ', s.partnerName, " ", s.percent, "% ", _jsxs("span", { className: "muted", children: ["(", PARTNER_TYPE_LABEL[s.partnerRole], ")"] })] }, s.id)));
}
function RmSelect({ rms, value, onChange, exclude }) {
    return (_jsxs("label", { children: ["RM", _jsxs("select", { value: value, onChange: (e) => onChange(e.target.value), required: true, children: [_jsx("option", { value: "", children: "Choose\u2026" }), rms
                        .filter((e) => e.id !== exclude)
                        .map((e) => (_jsxs("option", { value: e.id, children: [e.name, e.branchName ? ` — ${e.branchName}` : ''] }, e.id)))] })] }));
}
/** Set the same RM, from the same date, for all selected clients. */
function BulkRmBar({ ids, rms, onDone }) {
    const [rmId, setRmId] = useState('');
    const [from, setFrom] = useState(todayIso());
    const { busy, error, run } = useSubmit();
    async function submit(e) {
        e.preventDefault();
        if (await run(() => api('/api/clients/rm', { method: 'POST', body: { clientIds: ids, rmId: Number(rmId), from } })))
            onDone();
    }
    return (_jsxs("form", { className: "bulk-bar", onSubmit: submit, children: [_jsxs("strong", { children: [ids.length, " selected"] }), _jsx(RmSelect, { rms: rms, value: rmId, onChange: setRmId }), _jsxs("label", { children: ["From", _jsx("input", { type: "date", value: from, onChange: (e) => setFrom(e.target.value), required: true })] }), _jsx("button", { type: "submit", disabled: busy || !rmId, children: "Set RM" }), error && _jsx("span", { className: "error", children: error })] }));
}
const PROFILE_TABS = [
    { key: 'profile', label: 'Profile' },
    { key: 'mf', label: 'MF brokerage' },
    { key: 'rm', label: 'RM history' },
    { key: 'partners', label: 'Partners & sharing' },
    { key: 'holdings', label: 'Holdings' },
    { key: 'changes', label: 'Changes' },
];
/** One client on its own page: profile (PAN, codes, RM), RM history, partners, holdings in other products, changes. */
export function ClientProfilePage() {
    const { id: idParam } = useParams();
    const id = Number(idParam);
    const navigate = useNavigate();
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const detail = useLoad(`/api/clients/${id}`);
    const history = useLoad(`/api/clients/${id}/rm`);
    const employees = useLoad('/api/employees');
    const rms = (employees.data ?? []).filter((e) => e.status === 'ACTIVE');
    const [rmId, setRmId] = useState('');
    const [tab, setTab] = useTab(PROFILE_TABS);
    const c = detail.data?.client;
    const changed = () => {
        detail.reload();
        history.reload();
    };
    return (_jsxs("section", { "aria-label": c?.name ?? 'Client', children: [_jsx("p", { style: { margin: '0 0 8px' }, children: _jsx(Link, { to: "/clients", children: "\u2190 Clients" }) }), detail.error && _jsx("p", { className: "error", children: detail.error }), c && detail.data && (_jsxs(_Fragment, { children: [_jsx("div", { className: "page-head", children: _jsxs("div", { children: [_jsx("h1", { children: c.name }), _jsxs("div", { className: "actions", children: [_jsx("span", { className: "badge", children: c.iwellCode ?? 'OFFLINE' }), _jsx("span", { className: "badge mono", children: c.pan ?? 'no PAN' }), c.rmName ? _jsxs("span", { className: "badge ok", children: ["RM ", c.rmName] }) : _jsx("span", { className: "badge warn", children: "needs RM" }), c.branchName && _jsx("span", { className: "badge", children: c.branchName })] })] }) }), _jsx(Tabs, { tabs: PROFILE_TABS, value: tab, onChange: setTab }), tab === 'profile' && (_jsxs("div", { className: "grid-2", children: [_jsxs("div", { className: "panel-card", children: [_jsx("h2", { children: "Details" }), _jsxs("dl", { className: "facts", children: [_jsx("dt", { children: "Code" }), _jsx("dd", { className: "mono", children: c.iwellCode ?? '—' }), _jsx("dt", { children: "Second code" }), _jsx("dd", { className: "mono", children: detail.data.iwellCode2 ?? '—' }), _jsx("dt", { children: "PAN" }), _jsxs("dd", { className: "mono", children: [c.pan ?? '—', canEdit && _jsx(PanEdit, { clientId: id, currentPan: c.pan, onDone: changed })] }), _jsx("dt", { children: "Family head" }), _jsx("dd", { children: detail.data.familyHeadName ?? detail.data.familyHeadCode ?? '—' }), _jsx("dt", { children: "RM now" }), _jsx("dd", { children: c.rmName ? `${c.rmName}${c.branchName ? ` — ${c.branchName}` : ''}` : 'none' })] })] }), canEdit && (_jsxs("div", { className: "panel-card", children: [_jsx(DatedChangeForm, { title: c.rmId ? 'Change RM' : 'Set RM', dateLabel: "From", submitLabel: "Save", canSubmit: rmId !== '', onSubmit: (from) => api(`/api/clients/${id}/rm`, { method: 'POST', body: { rmId: Number(rmId), from } }), onDone: () => {
                                            setRmId('');
                                            changed();
                                        }, children: _jsx(RmSelect, { rms: rms, value: rmId, onChange: setRmId, exclude: c.rmId }) }), _jsx(MergeForm, { client: c, onDone: () => navigate('/clients') })] }))] })), tab === 'profile' && _jsx(OpsMaster, { clientId: id }), tab === 'mf' && _jsx(MfBrokerage, { clientId: id }), tab === 'rm' && (_jsxs("div", { className: "panel-card", children: [_jsx("h2", { children: "RM over time" }), history.data && history.data.length > 0 ? (_jsx(HistoryTable, { label: "RM", rows: history.data.map((h) => ({ ...h, name: h.targetName })) })) : (_jsx("p", { className: "muted", children: "No RM yet." }))] })), tab === 'partners' && (_jsx("div", { className: "panel-card", children: _jsx(PartnerSection, { clientId: id, canEdit: canEdit, onChanged: changed }) })), tab === 'holdings' && _jsx(Holdings, { clientId: id }), tab === 'changes' && (_jsx("div", { className: "panel-card", children: _jsx(RecordChanges, { clientId: id }, `${id}-${c.pan}`) }))] }))] }));
}
const RELATION_LABEL = { THIS: 'this record', SAME_PAN: 'same PAN', MINOR: 'minor' };
/**
 * Everything the client's PAN holds (R-09): this record, any other record with the same PAN and minors mapped to it by
 * guardian PAN, across FD, bonds, AIF, loans, GIFT City, PMS, NPS, P2P, Unlisted, insurance and equity.
 */
function Holdings({ clientId }) {
    const { data } = useLoad(`/api/clients/${clientId}/holdings`);
    if (!data)
        return _jsx("p", { className: "muted", children: "Loading\u2026" });
    const several = data.members.length > 1;
    const products = [...new Set(data.rows.map((r) => r.product))];
    return (_jsxs(_Fragment, { children: [_jsxs("p", { className: "muted", children: [several ? 'Holdings of every record under this PAN: ' : 'Holdings under this PAN. ', several &&
                        data.members.map((m, i) => (_jsxs("span", { children: [i > 0 && ', ', m.relation === 'THIS' ? m.name : _jsx(Link, { to: `/clients/${m.clientId}?tab=holdings`, children: m.name }), _jsx("span", { className: "badge", children: RELATION_LABEL[m.relation] })] }, m.clientId))), !several && data.members[0]?.pan == null && 'This record has no PAN, so only its own entries show.'] }), data.rows.length === 0 ? (_jsx("div", { className: "empty", children: "No FD, bond, AIF, loan, GIFT City, PMS, NPS, P2P, Unlisted, insurance or equity holdings under this PAN." })) : (_jsxs(_Fragment, { children: [_jsx("p", { className: "muted", children: products.map((p) => `${p} ${data.rows.filter((r) => r.product === p).length}`).join(' · ') }), _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Product" }), _jsx("th", { children: "Month" }), several && _jsx("th", { children: "Client" }), _jsx("th", { children: "Holding" }), _jsx("th", { className: "num", children: "Amount" }), _jsx("th", { className: "num", children: "Gross" })] }) }), _jsx("tbody", { children: data.rows.map((r, i) => (_jsxs("tr", { children: [_jsx("td", { children: _jsx("span", { className: "badge", children: r.product }) }), _jsx("td", { children: formatMonth(r.month) }), several && _jsx("td", { children: r.clientName ?? _jsx("span", { className: "muted", children: "not a client yet" }) }), _jsx("td", { children: _jsx(Link, { to: r.id == null ? `/products?tab=${r.tab}` : `/products?tab=${r.tab}&open=${r.id}`, children: r.item }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: r.amount }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: r.gross }) })] }, `${r.tab}-${r.id ?? i}`))) })] })] }))] }));
}
/** The client's partners: a sub-broker and/or a referral partner, each with its own % and dates. */
function PartnerSection({ clientId, canEdit, onChanged }) {
    const history = useLoad(`/api/clients/${clientId}/partner`);
    const partners = useLoad(canEdit ? '/api/partners' : null);
    const today = useLoad(`/api/shares?clientIds=${clientId}`);
    const refresh = () => {
        history.reload();
        today.reload();
        onChanged();
    };
    const all = history.data ?? [];
    /** Today's partners with the % that counts today, branch rule included. */
    const current = today.data?.[clientId] ?? [];
    return (_jsxs("div", { className: "separated", children: [_jsx("h3", { children: "Partners" }), _jsx("p", { className: "muted", children: "A branch partner is paid first, then the sub-broker; the referral partner's % is taken from what is left." }), ['SUB_BROKER', 'REFERRAL'].map((role) => (_jsx(RoleShare, { clientId: clientId, role: role, now: current.find((s) => s.partnerRole === role && !s.branch) ?? all.find((s) => s.partnerRole === role && s.validTo === null), branchShare: current.find((s) => s.partnerRole === role && s.branch), partners: (partners.data ?? []).filter((p) => p.type === role && p.status !== 'INACTIVE'), canEdit: canEdit, onChanged: refresh }, role))), all.length > 0 && (_jsxs("details", { className: "past", children: [_jsx("summary", { children: "Past partner changes" }), _jsxs("table", { className: "compact", children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Partner" }), _jsx("th", { children: "Type" }), _jsx("th", { children: "%" }), _jsx("th", { children: "From" }), _jsx("th", { children: "To" })] }) }), _jsx("tbody", { children: all.map((s) => (_jsxs("tr", { children: [_jsx("td", { children: s.partnerName }), _jsx("td", { children: PARTNER_TYPE_LABEL[s.partnerRole] }), _jsx("td", { className: "num", children: s.clientPercent !== null ? `${s.clientPercent}% (this client)` : `${s.percent}% (partner)` }), _jsx("td", { children: formatDate(s.validFrom) }), _jsx("td", { children: s.validTo ? formatDate(s.validTo) : _jsx("span", { className: "badge", children: "current" }) })] }, s.id))) })] })] }))] }));
}
function RoleShare({ clientId, role, now, branchShare, partners, canEdit, onChanged }) {
    const [partnerId, setPartnerId] = useState('');
    const [percent, setPercent] = useState('');
    const [own, setOwn] = useState(now?.source === 'CLIENT');
    const [lastDay, setLastDay] = useState('');
    const { busy, error, run } = useSubmit();
    const label = PARTNER_TYPE_LABEL[role];
    return (_jsxs("div", { className: "role-share", children: [_jsx("h4", { children: label }), branchShare && (_jsxs("p", { children: [_jsx(ShareText, { share: branchShare }), " ", _jsx("span", { className: "muted", children: "\u2014 paid first" })] })), _jsx("p", { children: now ? _jsx(ShareText, { share: now }) : _jsx("span", { className: "muted", children: branchShare ? 'No own ' + label.toLowerCase() : 'None' }) }), canEdit && (_jsxs(DatedChangeForm, { title: now ? `Change % or ${label.toLowerCase()}` : `Add a ${label.toLowerCase()}`, dateLabel: "From", submitLabel: "Save", canSubmit: (partnerId !== '' || now !== undefined) && (!own || percent !== ''), onSubmit: (from) => api(`/api/clients/${clientId}/partner`, {
                    method: 'POST',
                    body: { partnerId: Number(partnerId || now?.partnerId), percent: own ? Number(percent) : null, from },
                }), onDone: () => {
                    setPartnerId('');
                    setPercent('');
                    onChanged();
                }, children: [_jsxs("label", { children: [label, _jsxs("select", { value: partnerId, onChange: (e) => setPartnerId(e.target.value), required: !now, children: [_jsx("option", { value: "", children: now ? `Keep ${now.partnerName}` : 'Choose…' }), partners.map((p) => (_jsx("option", { value: p.id, children: p.name }, p.id)))] })] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "radio", name: `pct-${role}`, checked: !own, onChange: () => setOwn(false) }), "Use the partner\u2019s MF %"] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "radio", name: `pct-${role}`, checked: own, onChange: () => setOwn(true) }), "A different % for this client only"] }), own && (_jsxs("label", { children: [now ? 'New sharing %' : 'Sharing %', _jsx("input", { type: "number", min: "0", max: "100", step: "0.01", value: percent, onChange: (e) => setPercent(e.target.value), required: true })] })), now && (_jsxs("p", { className: "muted", children: ["From a later date: the old % stays for the months before. From ", now.validFrom, " (its first day): the % is corrected."] }))] })), canEdit && now && (_jsxs("form", { className: "inline-form", onSubmit: async (e) => {
                    e.preventDefault();
                    if (await run(() => api(`/api/clients/${clientId}/partner/end`, { method: 'POST', body: { role, lastDay } })))
                        onChanged();
                }, children: [_jsxs("span", { children: ["No ", label.toLowerCase(), " after"] }), _jsx("input", { type: "date", value: lastDay, onChange: (e) => setLastDay(e.target.value), "aria-label": `Last day with ${label.toLowerCase()}`, required: true }), _jsx("button", { type: "submit", className: "secondary", disabled: busy || !lastDay, children: "Remove" }), error && _jsx("span", { className: "error", children: error })] }))] }));
}
/** A partner and the % that counts, saying where the % comes from. */
function ShareText({ share }) {
    if (share.branch) {
        return (_jsxs(_Fragment, { children: [share.partnerName, " \u00B7 ", share.percent, "% ", _jsxs("span", { className: "badge", children: ["all ", share.branch, " clients"] })] }));
    }
    switch (share.source) {
        case 'CLIENT':
            return (_jsxs(_Fragment, { children: [share.partnerName, " \u00B7 ", share.percent, "% ", _jsx("span", { className: "badge", children: "this client only" })] }));
        case 'PARTNER':
            return (_jsxs(_Fragment, { children: [share.partnerName, " \u00B7 ", share.percent, "% ", _jsx("span", { className: "muted", children: "(partner\u2019s MF %)" })] }));
        case 'PENDING':
            return (_jsxs(_Fragment, { children: [share.partnerName, " \u00B7 0% ", _jsx("span", { className: "badge warn-badge", children: "partner approval pending" })] }));
        default:
            return (_jsxs(_Fragment, { children: [share.partnerName, " \u00B7 ", _jsx("span", { className: "badge warn-badge", children: "no MF % set for this partner" })] }));
    }
}
const PAN_FORMAT = /^[A-Z]{5}[0-9]{4}[A-Z]$/;
/** Corrects the client's PAN (a typo in the master or in Finesse). Audited; refused if another client has the PAN. */
function PanEdit({ clientId, currentPan, onDone }) {
    // Start from the PAN on file when it is shown in full, so a typo is fixed rather than retyped.
    const startPan = currentPan && !currentPan.includes('*') ? currentPan : '';
    const [open, setOpen] = useState(false);
    const [pan, setPan] = useState(startPan);
    const [reason, setReason] = useState('');
    const [move, setMove] = useState(false);
    const { busy, error, run } = useSubmit();
    if (!open) {
        return (_jsxs(_Fragment, { children: [_jsx("button", { type: "button", className: "link", onClick: () => {
                        setPan(startPan);
                        setOpen(true);
                    }, children: currentPan ? 'Edit PAN' : 'Add PAN' }), _jsx("button", { type: "button", className: "link", disabled: busy, title: "For a minor: the guardian's record keeps the PAN; this client is then matched by guardian PAN", onClick: async () => {
                        if (!window.confirm("This PAN is the guardian's? It will be removed from this client; the guardian keeps it."))
                            return;
                        if (await run(() => api(`/api/clients/${clientId}/pan/guardian`, { method: 'POST', body: { reason: "Guardian's PAN" } })))
                            onDone();
                    }, children: "It's the guardian's PAN" }), error && _jsx("span", { className: "error", children: error })] }));
    }
    return (_jsxs("form", { className: "inline-form", onSubmit: async (e) => {
            e.preventDefault();
            if (await run(() => api(`/api/clients/${clientId}/pan`, { method: 'POST', body: { pan, reason, takeFromOther: move } }))) {
                setOpen(false);
                setPan('');
                setReason('');
                setMove(false);
                onDone();
            }
        }, children: [_jsx("input", { value: pan, onChange: (e) => setPan(e.target.value.toUpperCase().replace(/\s/g, '')), placeholder: "ABCDE1234F", "aria-label": "New PAN", maxLength: 10, required: true }), _jsx("input", { value: reason, onChange: (e) => setReason(e.target.value), placeholder: "Reason", "aria-label": "Reason for the PAN change" }), _jsx("button", { type: "submit", className: "secondary", disabled: busy || !PAN_FORMAT.test(pan) || pan === startPan, children: "Save PAN" }), _jsx("button", { type: "button", className: "link", onClick: () => setOpen(false), children: "Cancel" }), pan.length === 10 && !PAN_FORMAT.test(pan) && _jsx("span", { className: "error", children: "A PAN looks like ABCDE1234F" }), _jsx("span", { className: "muted", children: "The old PAN is remembered: a later Master MIS or Finesse file that still has it finds this client." }), error && _jsx("span", { className: "error", children: error }), error?.includes('is already on') && (_jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: move, onChange: (e) => setMove(e.target.checked) }), "That record is a duplicate: move the PAN to this client (only if it has no RM)"] }))] }));
}
/**
 * Two records of one person (e.g. a file's PAN had swapped digits): this record is merged into the one picked and removed.
 * Its codes and unsaved upload rows move across; the PAN chosen is kept and the other is remembered (R-07).
 */
function MergeForm({ client, onDone }) {
    const [open, setOpen] = useState(false);
    const [q, setQ] = useState(client.name);
    const [targetId, setTargetId] = useState(null);
    const [keepThisPan, setKeepThisPan] = useState(false);
    const [reason, setReason] = useState('');
    const found = useLoad(open && q.trim().length >= 3 ? `/api/clients?q=${encodeURIComponent(q.trim())}&size=8` : null);
    const { busy, error, run } = useSubmit();
    const others = (found.data?.items ?? []).filter((x) => x.id !== client.id);
    const target = others.find((x) => x.id === targetId) ?? null;
    if (!open) {
        return (_jsx("div", { className: "separated", children: _jsx("button", { type: "button", className: "link", onClick: () => setOpen(true), children: "Same person as another client? Merge\u2026" }) }));
    }
    return (_jsxs("form", { className: "separated", onSubmit: async (e) => {
            e.preventDefault();
            if (!target)
                return;
            const kept = keepThisPan ? client.pan : target.pan;
            if (!window.confirm(`Merge ${client.name} into ${target.name} (PAN ${kept ?? 'none'})? This record will be removed.`))
                return;
            if (await run(() => api(`/api/clients/${client.id}/merge`, { method: 'POST', body: { intoClientId: target.id, keepThisPan, reason } })))
                onDone();
        }, children: [_jsx("h3", { children: "Merge into another client" }), _jsx("p", { className: "muted", children: "This record is removed. Its codes and unsaved upload rows move to the client you pick, which keeps its RM history. The PAN not kept is remembered, so later files that still have it find the right client." }), _jsxs("label", { children: ["Find the other record (name, code or full PAN)", _jsx("input", { value: q, onChange: (e) => setQ(e.target.value), "aria-label": "Find the client to merge into" })] }), q.trim().length >= 3 && found.data && others.length === 0 && _jsx("p", { className: "muted", children: "No other client matches." }), others.map((x) => (_jsxs("label", { className: "check", children: [_jsx("input", { type: "radio", name: "merge-target", checked: targetId === x.id, onChange: () => setTargetId(x.id) }), x.name, " \u00B7 ", _jsx("span", { className: "mono", children: x.pan ?? 'no PAN' }), " \u00B7 ", x.rmName ?? 'no RM', " ", x.iwellCode ? `· ${x.iwellCode}` : ''] }, x.id))), target && (_jsxs("fieldset", { children: [_jsx("legend", { children: "PAN to keep" }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "radio", name: "merge-pan", checked: !keepThisPan, onChange: () => setKeepThisPan(false) }), _jsx("span", { className: "mono", children: target.pan ?? 'none' }), " (the client you picked)"] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "radio", name: "merge-pan", checked: keepThisPan, onChange: () => setKeepThisPan(true), disabled: !client.pan }), _jsx("span", { className: "mono", children: client.pan ?? 'none' }), " (this record)"] })] })), _jsx("input", { value: reason, onChange: (e) => setReason(e.target.value), placeholder: "Reason", "aria-label": "Reason for the merge" }), error && _jsx("p", { className: "error", children: error }), _jsxs("span", { className: "inline-form", children: [_jsx("button", { type: "submit", className: "secondary", disabled: busy || !target, children: "Merge" }), _jsx("button", { type: "button", className: "link", onClick: () => setOpen(false), children: "Cancel" })] })] }));
}
