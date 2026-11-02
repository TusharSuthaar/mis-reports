import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useCallback, useMemo, useState } from 'react';
import { api, errorText } from '../../api/client';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { DatedChangeForm } from '../../components/DatedChangeForm';
import { HistoryTable } from '../../components/HistoryTable';
import { NameForm } from '../../components/NameForm';
import { SidePanel } from '../../components/SidePanel';
import { formatDate, todayIso } from '../../format';
/**
 * The org as a tree: Region (RD) → Branch → RMs, in the regions' set order. A search keeps a whole region when the region or
 * its RD matches, otherwise only the branches that match (by name or by one of their RMs).
 */
export function filterOrg(chart, search) {
    const q = search.trim().toLowerCase();
    if (!q)
        return chart;
    const has = (v) => !!v && v.toLowerCase().includes(q);
    const branchMatches = (b) => has(b.name) || b.people.some((p) => has(p.name));
    return {
        ...chart,
        regions: chart.regions
            .map((r) => (has(r.name) || has(r.rd?.name) ? r : { ...r, branches: r.branches.filter(branchMatches) }))
            .filter((r) => has(r.name) || has(r.rd?.name) || r.branches.length > 0),
        branchesWithoutRegion: chart.branchesWithoutRegion.filter(branchMatches),
        peopleWithoutBranch: chart.peopleWithoutBranch.filter((p) => has(p.name)),
    };
}
export function isEmptyOrg(chart) {
    return chart.regions.length === 0 && chart.branchesWithoutRegion.length === 0 && chart.peopleWithoutBranch.length === 0;
}
const HOUSE_HELP = 'A house book holds clients that have no person as their RM, such as HEAD OFFICE or STAFF. It sits in a branch and shows ' +
    'in the reports like an RM, but it cannot be an RD or one of a partner’s RMs. To add a person, use Add RM.';
/** The whole org on one screen. Every change is "what, and from when". */
export function OrgPage() {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const [date, setDate] = useState(todayIso());
    const chart = useLoad(`/api/org?date=${date}`);
    const regions = useLoad('/api/regions');
    const branches = useLoad('/api/branches');
    const employees = useLoad('/api/employees');
    const [search, setSearch] = useState('');
    const [panel, setPanel] = useState(null);
    const close = useCallback(() => setPanel(null), []);
    const reloadAll = () => {
        chart.reload();
        regions.reload();
        branches.reload();
        employees.reload();
    };
    const done = () => {
        close();
        reloadAll();
    };
    const tree = useMemo(() => (chart.data ? filterOrg(chart.data, search) : null), [chart.data, search]);
    const counts = useLoad('/api/clients/count-by-rm');
    const open = (p) => () => setPanel(p);
    // Which regions are opened up. null = as first shown: only the first region; a search opens them all.
    const [opened, setOpened] = useState(null);
    const isOpen = (id, idx) => (search.trim() ? true : opened ? opened.has(id) : idx === 0);
    const toggle = (id, idx) => {
        const next = new Set(opened ?? (tree?.regions[0] ? [tree.regions[0].id] : []));
        if (isOpen(id, idx))
            next.delete(id);
        else
            next.add(id);
        setOpened(next);
    };
    const allOpen = !!tree && tree.regions.every((r, i) => isOpen(r.id, i));
    const rmTable = (list) => list.length === 0 ? (_jsx("p", { className: "muted org-none", children: "No RM yet." })) : (_jsxs("table", { className: "org-rms", children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { className: "org-no", children: "#" }), _jsx("th", { children: "Relationship Manager" }), counts.data && _jsx("th", { className: "num", children: "Clients" })] }) }), _jsx("tbody", { children: list.map((p, i) => (_jsxs("tr", { children: [_jsx("td", { className: "org-no", children: i + 1 }), _jsxs("td", { children: [_jsx("button", { type: "button", className: "cell-link", "aria-label": p.name, onClick: open({ kind: 'rm', id: p.id }), children: p.name }), p.type === 'HOUSE' && _jsx("span", { className: "badge", children: "house" })] }), counts.data && _jsx("td", { className: "num", children: (counts.data[p.id] ?? 0).toLocaleString('en-IN') })] }, p.id))) })] }));
    const branchBlocks = (list) => list.map((b, j) => (_jsxs("div", { className: "org-branch", children: [_jsxs("div", { className: "org-branch-head", children: [_jsxs("span", { className: "org-no", children: [j + 1, "."] }), _jsx("button", { type: "button", className: "cell-link", onClick: open({ kind: 'branch', id: b.id }), children: b.name }), _jsxs("span", { className: "pill", children: [b.people.length, " RM", b.people.length === 1 ? '' : 's'] })] }), rmTable(b.people)] }, b.id)));
    return (_jsxs("section", { children: [_jsxs("div", { className: "page-head", children: [_jsxs("div", { children: [_jsx("h1", { children: "Organisation" }), _jsx("p", { className: "muted", style: { margin: 0 }, children: "Manage regions, branches and relationship managers. Click any name to change it." })] }), canEdit && (_jsxs("div", { className: "actions", children: [_jsx("button", { type: "button", className: "secondary", onClick: open({ kind: 'add-region' }), children: "Add region" }), _jsx("button", { type: "button", className: "secondary", onClick: open({ kind: 'add-branch' }), children: "Add branch" }), _jsx("button", { type: "button", className: "secondary", onClick: open({ kind: 'add-house' }), children: "Add house book" }), _jsx("button", { type: "button", onClick: open({ kind: 'add-rm' }), children: "Add RM" })] }))] }), _jsxs("div", { className: "filters list-filters", children: [_jsxs("label", { children: ["As on", _jsx("input", { type: "date", value: date, onChange: (e) => e.target.value && setDate(e.target.value) })] }), _jsxs("label", { className: "grow", children: ["Search", _jsx("input", { value: search, onChange: (e) => setSearch(e.target.value), placeholder: "Region, branch or RM\u2026" })] }), tree && tree.regions.length > 1 && !search.trim() && (_jsx("button", { type: "button", className: "link", onClick: () => setOpened(allOpen ? new Set() : new Set(tree.regions.map((r) => r.id))), children: allOpen ? 'Collapse all' : 'Expand all' }))] }), chart.error && _jsx("p", { className: "error", children: chart.error }), tree && isEmptyOrg(tree) && _jsx("div", { className: "empty", children: search ? 'Nothing matches.' : 'Nothing set up for this date yet.' }), tree && !isEmptyOrg(tree) && (_jsxs("div", { className: "org-tree", children: [tree.regions.map((r, i) => {
                        const shown = isOpen(r.id, i);
                        return (_jsxs("article", { className: shown ? 'org-region open' : 'org-region', children: [_jsxs("header", { className: "org-region-head", onClick: () => toggle(r.id, i), children: [_jsx("button", { type: "button", className: "org-toggle", "aria-expanded": shown, "aria-label": `${shown ? 'Collapse' : 'Expand'} ${r.name}`, onClick: (e) => {
                                                e.stopPropagation();
                                                toggle(r.id, i);
                                            }, children: "\u203A" }), _jsxs("span", { className: "org-no", children: [i + 1, "."] }), _jsx("button", { type: "button", className: "cell-link org-region-name", onClick: (e) => {
                                                e.stopPropagation();
                                                setPanel({ kind: 'region', id: r.id });
                                            }, children: r.name }), _jsxs("span", { className: r.rd ? 'muted' : 'badge warn', children: ["\u2014 RD: ", r.rd?.name ?? 'none'] }), _jsxs("span", { className: "pill", children: [r.branches.length, " branch", r.branches.length === 1 ? '' : 'es'] })] }), shown && _jsx("div", { className: "org-region-body", children: r.branches.length === 0 ? _jsx("p", { className: "muted org-none", children: "No branch yet." }) : branchBlocks(r.branches) })] }, r.id));
                    }), tree.branchesWithoutRegion.length > 0 && (_jsxs("article", { className: "org-region open", children: [_jsx("header", { className: "org-region-head", children: _jsx("span", { className: "muted", children: "Branches not in a region" }) }), _jsx("div", { className: "org-region-body", children: branchBlocks(tree.branchesWithoutRegion) })] })), tree.peopleWithoutBranch.length > 0 && (_jsxs("article", { className: "org-region open", children: [_jsx("header", { className: "org-region-head", children: _jsx("span", { className: "muted", children: "Not in a branch (e.g. RDs only)" }) }), _jsx("div", { className: "org-region-body", children: _jsx("div", { className: "org-branch", children: rmTable(tree.peopleWithoutBranch) }) })] }))] })), panel?.kind === 'add-region' && (_jsx(SidePanel, { title: "Add region", onClose: close, children: _jsx(NameForm, { label: "Region name", submitLabel: "Add region", onSubmit: (name) => api('/api/regions', { method: 'POST', body: { name } }), onDone: done }) })), panel?.kind === 'add-branch' && (_jsx(SidePanel, { title: "Add branch", onClose: close, children: _jsx(AddBranchForm, { regions: regions.data ?? [], onDone: done }) })), panel?.kind === 'add-rm' && (_jsx(SidePanel, { title: "Add RM", onClose: close, children: _jsx(AddRmForm, { branches: branches.data ?? [], onDone: done }) })), panel?.kind === 'add-house' && (_jsx(SidePanel, { title: "Add house book", onClose: close, children: _jsx(AddRmForm, { branches: branches.data ?? [], house: true, onDone: done }) })), panel?.kind === 'region' && (_jsx(RegionPanel, { region: regions.data?.find((x) => x.id === panel.id), rms: (employees.data ?? []).filter((e) => e.type === 'RM'), canEdit: canEdit, onClose: close, onChanged: reloadAll })), panel?.kind === 'branch' && (_jsx(BranchPanel, { branch: branches.data?.find((x) => x.id === panel.id), regions: regions.data ?? [], canEdit: canEdit, onClose: close, onChanged: reloadAll })), panel?.kind === 'rm' && (_jsx(RmPanel, { rm: employees.data?.find((x) => x.id === panel.id), branches: branches.data ?? [], people: employees.data ?? [], rdOf: (chart.data?.regions ?? []).filter((r) => r.rd?.id === panel.id).map((r) => r.name), clients: counts.data?.[panel.id], canEdit: canEdit, onClose: close, onChanged: () => {
                    reloadAll();
                    counts.reload();
                } }))] }));
}
function Select({ label, value, onChange, options }) {
    return (_jsxs("label", { children: [label, _jsxs("select", { value: value, onChange: (e) => onChange(e.target.value), required: true, children: [_jsx("option", { value: "", children: "Choose\u2026" }), options.map((o) => (_jsx("option", { value: o.id, children: o.name }, o.id)))] })] }));
}
function PastChanges({ path, label }) {
    const history = useLoad(path);
    return (_jsxs("details", { className: "past", children: [_jsx("summary", { children: "Past changes" }), history.error && _jsx("p", { className: "error", children: history.error }), history.data && _jsx(HistoryTable, { label: label, rows: history.data.map((h) => ({ ...h, name: h.targetName })) })] }));
}
function AddBranchForm({ regions, onDone }) {
    const [name, setName] = useState('');
    const [regionId, setRegionId] = useState('');
    const [from, setFrom] = useState(todayIso());
    const { busy, error, run } = useSubmit();
    async function submit(e) {
        e.preventDefault();
        if (await run(() => api('/api/branches', { method: 'POST', body: { name, regionId: Number(regionId), from } })))
            onDone();
    }
    return (_jsxs("form", { onSubmit: submit, children: [_jsxs("label", { children: ["Branch name", _jsx("input", { value: name, onChange: (e) => setName(e.target.value), required: true })] }), _jsx(Select, { label: "Region", value: regionId, onChange: setRegionId, options: regions }), _jsxs("label", { children: ["From", _jsx("input", { type: "date", value: from, onChange: (e) => setFrom(e.target.value), required: true })] }), error && _jsx("p", { className: "error", children: error }), _jsx("button", { type: "submit", disabled: busy, children: "Add branch" })] }));
}
function AddRmForm({ branches, house = false, onDone }) {
    const [name, setName] = useState('');
    const [branchId, setBranchId] = useState('');
    const [from, setFrom] = useState(todayIso());
    const { busy, error, run } = useSubmit();
    async function submit(e) {
        e.preventDefault();
        const body = { name, type: house ? 'HOUSE' : 'RM', branchId: Number(branchId), from };
        if (await run(() => api('/api/employees', { method: 'POST', body })))
            onDone();
    }
    return (_jsxs("form", { onSubmit: submit, children: [house && _jsx("p", { className: "muted", children: HOUSE_HELP }), _jsxs("label", { children: [house ? 'Name (e.g. DIRECT, HO WALK-IN)' : 'RM name (as in the Master MIS)', _jsx("input", { value: name, onChange: (e) => setName(e.target.value), required: true })] }), _jsx(Select, { label: "Branch", value: branchId, onChange: setBranchId, options: branches }), _jsxs("label", { children: ["From", _jsx("input", { type: "date", value: from, onChange: (e) => setFrom(e.target.value), required: true })] }), error && _jsx("p", { className: "error", children: error }), _jsx("button", { type: "submit", disabled: busy, children: house ? 'Add house book' : 'Add RM' })] }));
}
function RegionPanel({ region, rms, canEdit, onClose, onChanged }) {
    const [rdId, setRdId] = useState('');
    if (!region)
        return null;
    return (_jsxs(SidePanel, { title: region.name, onClose: onClose, children: [_jsxs("p", { children: ["RD now: ", region.currentRd?.name ?? _jsx("span", { className: "muted", children: "none" })] }), canEdit && (_jsxs(_Fragment, { children: [_jsx(DatedChangeForm, { title: region.currentRd ? 'Change RD' : 'Set RD', dateLabel: "From", submitLabel: "Save", canSubmit: rdId !== '', onSubmit: (from) => api(`/api/regions/${region.id}/rd`, { method: 'POST', body: { employeeId: Number(rdId), from } }), onDone: () => {
                            setRdId('');
                            onChanged();
                        }, children: _jsx(Select, { label: "RD", value: rdId, onChange: setRdId, options: rms.filter((e) => e.id !== region.currentRd?.id) }) }), _jsx(NameForm, { label: "Rename region", initial: region.name, submitLabel: "Rename", onSubmit: (name) => api(`/api/regions/${region.id}`, { method: 'PATCH', body: { name } }), onDone: onChanged }, region.name), _jsx(RegionLayoutForm, { region: region, onDone: onChanged }, `${region.id}-${region.position}-${region.endBranchRows}`)] })), _jsx(PastChanges, { path: `/api/regions/${region.id}/rd`, label: "RD" })] }));
}
function BranchPanel({ branch, regions, canEdit, onClose, onChanged }) {
    const [regionId, setRegionId] = useState('');
    if (!branch)
        return null;
    return (_jsxs(SidePanel, { title: branch.name, onClose: onClose, children: [_jsxs("p", { children: ["Region now: ", branch.regionName ?? _jsx("span", { className: "muted", children: "none" })] }), canEdit && (_jsxs(_Fragment, { children: [_jsx(DatedChangeForm, { title: "Move to another region", dateLabel: "From", submitLabel: "Move", canSubmit: regionId !== '', onSubmit: (from) => api(`/api/branches/${branch.id}/region`, { method: 'POST', body: { regionId: Number(regionId), from } }), onDone: () => {
                            setRegionId('');
                            onChanged();
                        }, children: _jsx(Select, { label: "Region", value: regionId, onChange: setRegionId, options: regions.filter((r) => r.id !== branch.regionId) }) }), _jsx(NameForm, { label: "Rename branch", initial: branch.name, submitLabel: "Rename", onSubmit: (name) => api(`/api/branches/${branch.id}`, { method: 'PATCH', body: { name } }), onDone: onChanged }, branch.name)] })), _jsx(PastChanges, { path: `/api/branches/${branch.id}/region`, label: "Region" }), _jsx(BranchPartnerBlock, { branchId: branch.id, branchName: branch.name, canEdit: canEdit })] }));
}
/**
 * A partner for every client of the branch (e.g. one sub-broker for all its clients): paid first, at its rate card;
 * the client's own sub-broker and referral partner share what is left.
 */
function BranchPartnerBlock({ branchId, branchName, canEdit }) {
    const history = useLoad(`/api/branches/${branchId}/partner`);
    const partners = useLoad('/api/partners');
    const [partnerId, setPartnerId] = useState('');
    const [lastDay, setLastDay] = useState('');
    const { busy, error, run } = useSubmit();
    const now = (history.data ?? []).find((r) => r.validTo === null);
    const options = (partners.data ?? []).filter((p) => p.status !== 'INACTIVE' && p.type !== 'POSP');
    return (_jsxs("div", { className: "card-block", children: [_jsxs("h3", { children: ["Partner for all clients of ", branchName] }), _jsx("p", { children: now ? `${now.partnerName}, from ${formatDate(now.validFrom)}` : _jsx("span", { className: "muted", children: "None" }) }), _jsx("p", { className: "muted", children: "Paid first, at the partner\u2019s product %. The client\u2019s own partners then share what is left." }), canEdit && (_jsx(DatedChangeForm, { title: now ? 'Change the partner' : 'Set a partner for all clients', dateLabel: "From", submitLabel: "Save", canSubmit: partnerId !== '', onSubmit: (from) => api(`/api/branches/${branchId}/partner`, { method: 'POST', body: { partnerId: Number(partnerId), from } }), onDone: () => {
                    setPartnerId('');
                    history.reload();
                }, children: _jsx(Select, { label: "Partner", value: partnerId, onChange: setPartnerId, options: options }) })), canEdit && now && (_jsxs("form", { className: "inline-form", onSubmit: async (e) => {
                    e.preventDefault();
                    if (await run(() => api(`/api/branches/${branchId}/partner/end`, { method: 'POST', body: { lastDay } })))
                        history.reload();
                }, children: [_jsx("span", { children: "No branch partner after" }), _jsx("input", { type: "date", value: lastDay, onChange: (e) => setLastDay(e.target.value), "aria-label": "Last day with the branch partner", required: true }), _jsx("button", { type: "submit", className: "secondary", disabled: busy || !lastDay, children: "Remove" }), error && _jsx("span", { className: "error", children: error })] })), (history.data ?? []).length > 1 && (_jsxs("details", { children: [_jsx("summary", { children: "Earlier" }), _jsx("ul", { children: (history.data ?? []).filter((r) => r.validTo !== null).map((r) => (_jsxs("li", { children: [r.partnerName, ": ", formatDate(r.validFrom), " \u2013 ", formatDate(r.validTo)] }, r.id))) })] }))] }));
}
function RmPanel({ rm, branches, people, rdOf, clients, canEdit, onClose, onChanged, }) {
    const [branchId, setBranchId] = useState('');
    const [leaving, setLeaving] = useState(false);
    const { error, run } = useSubmit();
    if (!rm)
        return null;
    if (leaving) {
        return (_jsx(SidePanel, { title: `${rm.name} is leaving`, onClose: onClose, children: _jsx(HandoverForm, { rm: rm, people: people, rdOf: rdOf, clients: clients, onCancel: () => setLeaving(false), onDone: onChanged }) }));
    }
    return (_jsxs(SidePanel, { title: rm.name, onClose: onClose, children: [_jsxs("p", { children: ["Branch now: ", rm.branchName ?? _jsx("span", { className: "muted", children: "none" })] }), canEdit && (_jsxs(_Fragment, { children: [_jsx(DatedChangeForm, { title: "Move to another branch", dateLabel: "From", submitLabel: "Move", canSubmit: branchId !== '', onSubmit: (from) => api(`/api/employees/${rm.id}/branch`, { method: 'POST', body: { branchId: Number(branchId), from } }), onDone: () => {
                            setBranchId('');
                            onChanged();
                        }, children: _jsx(Select, { label: "Branch", value: branchId, onChange: setBranchId, options: branches.filter((b) => b.id !== rm.branchId) }) }), _jsx(NameForm, { label: "Rename RM", initial: rm.name, submitLabel: "Rename", onSubmit: (name) => api(`/api/employees/${rm.id}`, { method: 'PATCH', body: { name } }), onDone: onChanged }, rm.name), _jsxs("label", { className: "check separated", children: [_jsx("input", { type: "checkbox", checked: rm.type === 'HOUSE', onChange: async (e) => {
                                    if (await run(() => api(`/api/employees/${rm.id}`, { method: 'PATCH', body: { type: e.target.checked ? 'HOUSE' : 'RM' } })))
                                        onChanged();
                                } }), "House book: not a person (like HEAD OFFICE, STAFF)"] }), _jsx("p", { className: "muted", children: HOUSE_HELP }), error && _jsx("p", { className: "error", children: error }), rm.status === 'ACTIVE' && (_jsx("div", { className: "separated", children: _jsx("button", { type: "button", className: "secondary", onClick: () => setLeaving(true), children: "Leaving the company \u2014 hand over\u2026" }) }))] })), _jsx(PastChanges, { path: `/api/employees/${rm.id}/branch`, label: "Branch" })] }));
}
/** Where the region sits in lists and in the END region section, and whether the END shows its branches as rows. */
function RegionLayoutForm({ region, onDone }) {
    const [position, setPosition] = useState(String(region.position));
    const [branchRows, setBranchRows] = useState(region.endBranchRows);
    const [error, setError] = useState(null);
    const [busy, setBusy] = useState(false);
    const submit = async (e) => {
        e.preventDefault();
        setError(null);
        setBusy(true);
        try {
            await api(`/api/regions/${region.id}`, { method: 'PATCH', body: { position: Number(position), endBranchRows: branchRows } });
            onDone();
        }
        catch (err) {
            setError(errorText(err));
        }
        finally {
            setBusy(false);
        }
    };
    return (_jsxs("form", { onSubmit: submit, children: [_jsx("h3", { children: "Order and END" }), _jsxs("label", { children: ["Position (1 = first)", _jsx("input", { type: "number", min: 1, value: position, onChange: (e) => setPosition(e.target.value), required: true })] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: branchRows, onChange: (e) => setBranchRows(e.target.checked) }), "Show its branches as rows above it in the END region section"] }), error && _jsx("p", { className: "error", children: error }), _jsx("button", { type: "submit", disabled: busy, children: "Save" })] }));
}
function nextDay(iso) {
    const [y, m, d] = iso.split('-').map(Number);
    const n = new Date(y, m - 1, d + 1);
    const pad = (v) => String(v).padStart(2, '0');
    return `${n.getFullYear()}-${pad(n.getMonth() + 1)}-${pad(n.getDate())}`;
}
/**
 * R-61: the RM leaves on their last working day and, from the next day, the person picked (or a new RM made in the same
 * branch) has all their clients and partners, and their RD regions unless unticked. One step, all or nothing.
 */
function HandoverForm({ rm, people, rdOf, clients, onCancel, onDone }) {
    const [lastDay, setLastDay] = useState(todayIso());
    const [mode, setMode] = useState('new');
    const [replacementId, setReplacementId] = useState('');
    const [newName, setNewName] = useState('');
    const [newCode, setNewCode] = useState('');
    const [takeOverRd, setTakeOverRd] = useState(true);
    const [note, setNote] = useState('');
    const [done, setDone] = useState(null);
    const { busy, error, run } = useSubmit();
    const others = people.filter((p) => p.id !== rm.id && p.status === 'ACTIVE').sort((a, b) => a.name.localeCompare(b.name));
    const picked = others.find((p) => String(p.id) === replacementId);
    const takerIsHouse = mode === 'existing' && picked?.type === 'HOUSE';
    const from = lastDay ? nextDay(lastDay) : '';
    const ready = !!lastDay && (mode === 'new' ? newName.trim() !== '' : replacementId !== '');
    const submit = async (e) => {
        e.preventDefault();
        const reason = note.trim() || undefined;
        const body = mode === 'new'
            ? { lastDay, newRmName: newName.trim(), newRmCode: newCode.trim() || undefined, takeOverRd, reason }
            : { lastDay, replacementId: Number(replacementId), takeOverRd: takerIsHouse ? false : takeOverRd, reason };
        const out = {};
        const ok = await run(async () => {
            out.result = await api(`/api/employees/${rm.id}/hand-over`, { method: 'POST', body });
        });
        if (ok && out.result) {
            setDone(out.result);
            onDone();
        }
    };
    if (done) {
        return (_jsxs("p", { role: "status", className: "success", children: ["Done. ", rm.name, "\u2019s last working day is ", formatDate(lastDay), ". From ", formatDate(from), " ", _jsx("strong", { children: done.replacement.name }), " has their clients", done.replacement.type === 'RM' ? ' and partners' : '', done.rdRegionIds.length > 0 ? ` and is RD of ${rdOf.join(', ')}` : '', "."] }));
    }
    return (_jsxs("form", { className: "handover", onSubmit: submit, children: [_jsxs("label", { children: ["Last working day", _jsx("input", { type: "date", value: lastDay, onChange: (e) => setLastDay(e.target.value), required: true })] }), _jsxs("fieldset", { children: [_jsx("legend", { children: "Who takes over" }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "radio", name: "taker", checked: mode === 'new', onChange: () => setMode('new') }), "A new RM", rm.branchName ? ` (joins ${rm.branchName})` : ''] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "radio", name: "taker", checked: mode === 'existing', onChange: () => setMode('existing') }), "Someone already here"] })] }), mode === 'new' ? (_jsxs(_Fragment, { children: [_jsxs("label", { children: ["New RM\u2019s name", _jsx("input", { value: newName, onChange: (e) => setNewName(e.target.value), required: true })] }), _jsxs("label", { children: ["Emp code (optional \u2014 made for you if blank)", _jsx("input", { value: newCode, onChange: (e) => setNewCode(e.target.value) })] })] })) : (_jsxs("label", { children: ["Takes over", _jsxs("select", { value: replacementId, onChange: (e) => setReplacementId(e.target.value), required: true, children: [_jsx("option", { value: "", children: "Choose\u2026" }), others.map((p) => (_jsxs("option", { value: p.id, children: [p.name, p.branchName ? ` — ${p.branchName}` : '', p.type === 'HOUSE' ? ' (house book)' : ''] }, p.id)))] })] })), _jsxs("div", { className: "handover-summary", children: [_jsxs("strong", { children: ["From ", from ? formatDate(from) : '…', " they get:"] }), _jsxs("ul", { children: [_jsxs("li", { children: [clients == null ? 'All' : clients.toLocaleString('en-IN'), " client", clients === 1 ? '' : 's', " of ", rm.name] }), _jsx("li", { children: takerIsHouse ? 'Partners: not moved (a house book has no partners; the links end)' : 'Their partners (sub-brokers, referral partners)' }), rdOf.length > 0 && !takerIsHouse && (_jsx("li", { children: _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: takeOverRd, onChange: (e) => setTakeOverRd(e.target.checked) }), "RD of ", rdOf.join(', ')] }) }))] }), _jsxs("p", { className: "muted", children: ["Earlier months keep ", rm.name, ". Everything stays in the history."] })] }), _jsxs("label", { children: ["Note (optional)", _jsx("input", { value: note, onChange: (e) => setNote(e.target.value), placeholder: "e.g. Resigned" })] }), error && _jsx("p", { className: "error", children: error }), _jsxs("div", { className: "actions", children: [_jsx("button", { type: "submit", disabled: !ready || busy, children: busy ? 'Handing over…' : 'Hand over' }), _jsx("button", { type: "button", className: "secondary", onClick: onCancel, children: "Back" })] })] }));
}
