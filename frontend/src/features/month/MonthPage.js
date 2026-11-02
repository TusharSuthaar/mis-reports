import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { NameMatches } from '../entry/NameMatches';
import { Fragment, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, errorText } from '../../api/client';
import { PARTNER_TYPE_LABEL, } from '../../api/types';
import { useJob, jobErrorText } from '../../api/useJob';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { formatMoney } from '../../format';
import { Amount } from '../../components/Amount';
import { JobProgressBar } from '../../components/JobProgressBar';
const MONTH_LABEL = new Intl.DateTimeFormat('en-IN', { month: 'long', year: 'numeric' });
const DATE_TIME = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
/** "2026-08" → "August 2026" */
export function monthLabel(ym) {
    const [y, m] = ym.split('-').map(Number);
    return MONTH_LABEL.format(new Date(y, m - 1, 1));
}
/** Last month as yyyy-mm: brokerage files arrive for the month that just ended. */
function lastMonth() {
    const d = new Date();
    d.setDate(1);
    d.setMonth(d.getMonth() - 1);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}
function nextMonth(ym) {
    const [y, m] = ym.split('-').map(Number);
    return m === 12 ? `${y + 1}-01` : `${y}-${String(m + 1).padStart(2, '0')}`;
}
/** The products entered for a month, in END order, with the Product entries tab each lives on. */
const PRODUCTS = [
    { key: 'PREIPO', label: 'Unlisted' }, { key: 'FD', label: 'FD' }, { key: 'BOND', label: 'Bonds' }, { key: 'LOAN', label: 'Loans' },
    { key: 'INS', label: 'Insurance' }, { key: 'PMS', label: 'PMS' }, { key: 'AIF', label: 'AIF' }, { key: 'GIFT', label: 'GIFT City' },
    { key: 'NPS', label: 'NPS' }, { key: 'P2P', label: 'P2P' },
];
const STATE_LABEL = { done: 'Done', todo: 'To do', blocked: 'Needs fixing', waiting: 'Not yet', optional: 'When they change' };
/**
 * Month close as one guided page: pick the month, then go down its steps — masters, the MF file, the month's other
 * products, check & process, END & lock. Each step says whether it is done and has its own action; the step to do next
 * is open.
 */
export function MonthPage() {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const months = useLoad('/api/months');
    const [params, setParams] = useSearchParams();
    const list = [...(months.data ?? [])].sort((a, b) => a.month.localeCompare(b.month));
    const latest = list.length ? list[list.length - 1].month : null;
    const upcoming = latest ? nextMonth(latest) : lastMonth();
    // The month to work on: the one picked, else the first month not locked yet, else the latest month, else a new one.
    const selected = params.get('m') ?? list.find((m) => m.status !== 'LOCKED')?.month ?? latest ?? upcoming;
    const row = list.find((m) => m.month === selected) ?? null;
    const pick = (ym) => {
        const next = new URLSearchParams(params);
        next.set('m', ym);
        setParams(next, { replace: true });
    };
    return (_jsxs("section", { children: [_jsx("div", { className: "page-head", children: _jsxs("div", { children: [_jsx("h1", { children: "Month close" }), _jsx("p", { className: "muted", style: { margin: 0 }, children: "Pick the month, then go down the steps. The step to do next is open; each one shows whether it is done." })] }) }), months.error && _jsx("p", { className: "error", children: months.error }), _jsxs("div", { className: "month-chips", role: "tablist", "aria-label": "Months", children: [list.map((m) => (_jsxs("button", { type: "button", role: "tab", "aria-selected": m.month === selected, className: m.month === selected ? 'chip active' : 'chip', onClick: () => pick(m.month), children: [monthLabel(m.month), " ", _jsx("span", { className: m.status === 'LOCKED' ? 'badge ok' : m.status === 'PROCESSED' ? 'badge' : 'badge warn', children: STATUS_LABEL[m.status] })] }, m.month))), canEdit && (_jsxs("button", { type: "button", role: "tab", "aria-selected": selected === upcoming && !row, className: selected === upcoming && !row ? 'chip active' : 'chip', onClick: () => pick(upcoming), children: ["+ ", monthLabel(upcoming)] }))] }), months.data && _jsx(MonthWizard, { month: selected, row: row, canEdit: canEdit, onChanged: months.reload }, selected)] }));
}
/** One step of the month: number (✓ when done), title, state and a one-line summary; its body when open. */
function Step({ n, title, state, summary, open: openFirst, children }) {
    const [open, setOpen] = useState(openFirst);
    // A step that becomes the next one (the month's state loaded or changed) opens; the user can still close it.
    useEffect(() => {
        if (openFirst)
            setOpen(true);
    }, [openFirst]);
    return (_jsxs("li", { className: `wstep ${state}${openFirst ? ' current' : ''}`, children: [_jsxs("button", { type: "button", className: "wstep-head", "aria-expanded": open, onClick: () => setOpen(!open), children: [_jsx("span", { className: "wstep-num", "aria-hidden": "true", children: state === 'done' ? '✓' : n }), _jsx("span", { className: "wstep-title", children: title }), _jsx("span", { className: `wstep-state ${state}`, children: STATE_LABEL[state] }), summary && _jsx("span", { className: "wstep-summary muted", children: summary })] }), open && _jsx("div", { className: "wstep-body", children: children })] }));
}
function MonthWizard({ month, row, canEdit, onChanged }) {
    const { user } = useAuth();
    const master = useLoad('/api/finesse/client-master');
    const ready = useLoad(row ? `/api/months/${row.periodId}/readiness` : null);
    const [reviewing, setReviewing] = useState(null);
    const [replacing, setReplacing] = useState(false);
    const r = ready.data;
    const saved = (row?.saved?.lines ?? 0) > 0;
    const pending = row?.pendingUpload ?? null;
    const status = row?.status ?? 'OPEN';
    const reviewId = reviewing?.id ?? pending?.id ?? null;
    const changed = () => {
        onChanged();
        ready.reload();
    };
    const masterState = master.data && master.data.codes === 0 ? 'todo' : 'optional';
    const mfState = reviewId ? 'todo' : saved ? (row.notInMaster > 0 ? 'blocked' : 'done') : 'todo';
    const nameMatchesWaiting = (r?.equityNameMatches?.length ?? 0) + (r?.entryNameMatches?.length ?? 0);
    const productsState = !row ? 'waiting' : r && (r.equityNotInClients > 0 || nameMatchesWaiting > 0) ? 'blocked' : 'optional';
    const checkState = !row || !saved ? 'waiting' : status !== 'OPEN' ? 'done' : r && !r.ready ? 'blocked' : 'todo';
    const endState = status === 'LOCKED' ? 'done' : status === 'PROCESSED' ? 'todo' : 'waiting';
    const current = masterState === 'todo' ? 1 : mfState !== 'done' ? 2 : status === 'OPEN' ? 4 : 5;
    const entryCount = r ? PRODUCTS.reduce((s, p) => s + (r.entries[p.key] ?? 0), 0) : 0;
    return (_jsxs("ol", { className: "wizard", children: [_jsxs(Step, { n: 1, title: "Masters", state: masterState, open: current === 1, summary: master.data && master.data.codes > 0 ? `Client master ${master.data.codes.toLocaleString('en-IN')} codes${master.data.updatedAt ? `, updated ${DATE_TIME.format(new Date(master.data.updatedAt))}` : ''}` : undefined, children: [_jsx("p", { className: "muted", children: "Only when they change: the Finesse client master (code \u2192 PAN) and Master MIS (PAN \u2192 RM, partners and %)." }), _jsxs("div", { className: "source-cards", children: [_jsx(ClientMasterCard, { canEdit: canEdit }), _jsx(MasterMisCard, { canEdit: canEdit })] })] }), _jsx(Step, { n: 2, title: `MF brokerage for ${monthLabel(month)}`, state: mfState, open: current === 2, summary: saved ? `${row.savedUpload?.fileName ?? 'Saved'} · ${row.saved.lines.toLocaleString('en-IN')} clients · net ${formatMoney(row.saved.net)}` : undefined, children: reviewId ? (_jsx(Review, { uploadId: reviewId, notes: reviewing?.notes ?? [], canEdit: canEdit, onDone: () => { setReviewing(null); setReplacing(false); changed(); } }, reviewId)) : saved && !replacing ? (_jsxs(_Fragment, { children: [row.notInMaster > 0 ? (_jsxs("p", { className: "error", children: [row.notInMaster, " clients are not in Master MIS: the month cannot be processed until each has an RM.", ' ', row.savedUpload && (_jsxs("button", { type: "button", className: "link", onClick: () => setReviewing({ id: row.savedUpload.id, notes: [] }), children: ["Fix ", row.notInMaster, " clients, then process"] }))] })) : (_jsxs("p", { className: "ok", children: ["Saved: ", row.saved.lines.toLocaleString('en-IN'), " clients, net ", formatMoney(row.saved.net), ". Every client is in Master MIS."] })), canEdit && status !== 'LOCKED' && (_jsx("button", { type: "button", className: "link", onClick: () => setReplacing(true), children: "Upload a corrected file instead" }))] })) : canEdit ? (_jsx(BrokerageCard, { month: month, onStaged: (id, notes) => { setReviewing({ id, notes }); onChanged(); } })) : (_jsx("p", { className: "muted", children: "Not uploaded yet." })) }), _jsx(Step, { n: 3, title: "Other products this month", state: productsState, open: (!!row && saved && status === 'OPEN') || productsState === 'blocked', summary: r ? `${entryCount} entries · equity ${r.equityUploaded ? `${r.equityClients} clients` : 'not uploaded'}` : undefined, children: !row ? (_jsxs("p", { className: "muted", children: ["Unlisted, FD, bonds, loans, insurance, PMS, AIF, GIFT City, NPS and P2P can be entered for ", monthLabel(month), " any time on", ' ', _jsx(Link, { to: `/products?from=${month}&to=${month}`, children: "Product entries" }), "; once the MF file is saved, their counts show here."] })) : !r ? (_jsx("p", { className: "muted", children: "Checking the month\u2026" })) : (_jsxs(_Fragment, { children: [_jsxs("p", { className: "muted", children: ["Everything entered for ", monthLabel(month), " is included when the month is processed. Add or correct entries before you process."] }), _jsxs("div", { className: "product-tally", children: [PRODUCTS.map((p) => (_jsxs(Link, { className: (r.entries[p.key] ?? 0) > 0 ? 'tally has' : 'tally', to: `/products?tab=${p.key}&from=${month}&to=${month}`, children: [_jsx("span", { children: p.label }), _jsx("b", { children: r.entries[p.key] ?? 0 })] }, p.key))), _jsxs(Link, { className: r.equityUploaded ? 'tally has' : 'tally', to: "/products?tab=EQUITY", children: [_jsx("span", { children: "Equity file" }), _jsx("b", { children: r.equityUploaded ? `${r.equityClients} clients` : 'not uploaded' })] })] }), r.equityNotInClients > 0 && (_jsx(MissingEquity, { month: month, list: r.equityMissing ?? [], count: r.equityNotInClients, canEdit: canEdit, onAdded: changed })), _jsx(NameMatches, { title: "Equity clients", refLabel: "Party code", amountLabel: "Share", canEdit: canEdit, rows: (r.equityNameMatches ?? []).map((m) => ({ key: m.partyCode, ref: m.partyCode, nameInFile: m.nameInFile, clientId: m.clientId, clientName: m.clientName, pan: m.pan, rmName: m.rmName, amount: m.share })), onConfirm: async (row, clientId) => {
                                await api('/api/equity/name-matches', { method: 'POST', body: { month, partyCode: row.ref, clientId } });
                                changed();
                            } }), _jsx(NameMatches, { title: "Insurance policies", refLabel: "Policy no.", amountLabel: "Expected commission", canEdit: canEdit, rows: (r.entryNameMatches ?? []).filter((e) => e.product === 'INS').map(({ match: m }) => ({ key: String(m.entryId), ref: m.ref, nameInFile: m.nameInFile, clientId: m.clientId, clientName: m.clientName, pan: m.pan, rmName: m.rmName, amount: m.amount })), onConfirm: async (row, clientId) => {
                                await api(`/api/insurance/${row.key}/confirm-client`, { method: 'POST', body: { clientId } });
                                changed();
                            } }), _jsx("p", { className: "muted", children: "Monthly files: the equity brokerage report on the Equity tab, the insurance policy sheet on the Insurance tab." })] })) }), _jsx(Step, { n: 4, title: "Check & process", state: checkState, open: current === 4, summary: status !== 'OPEN' && row?.processedBy ? `Processed by ${row.processedBy}${row.processedAt ? `, ${DATE_TIME.format(new Date(row.processedAt))}` : ''}` : undefined, children: !row || !saved ? (_jsx("p", { className: "muted", children: "Save the MF file first (step 2)." })) : (_jsx(ProcessStep, { row: row, r: r, canEdit: canEdit, onChanged: changed, recheck: ready.reload })) }), _jsx(Step, { n: 5, title: "END file & lock", state: endState, open: current === 5, summary: status === 'LOCKED' ? `Locked${row?.lockedBy ? ` by ${row.lockedBy}` : ''}` : undefined, children: !row || status === 'OPEN' ? (_jsx("p", { className: "muted", children: "Process the month first (step 4); then download the END file and Finance locks it." })) : (_jsx(EndStep, { row: row, role: user?.role, onChanged: changed })) })] }));
}
/**
 * The month's equity clients that are not in Clients, as the broker's file has them (party code, name, PAN if any, share),
 * each with "Add client": the name and PAN from the file, an RM, from the month's first day. The trade finds it at once.
 */
function MissingEquity({ month, list, count, canEdit, onAdded }) {
    const [adding, setAdding] = useState(null);
    const [name, setName] = useState('');
    const [pan, setPan] = useState('');
    const [rmId, setRmId] = useState('');
    const employees = useLoad(adding ? '/api/employees' : null);
    const { busy, error, run } = useSubmit();
    const open = (m) => {
        setAdding(m.partyCode);
        setName(m.name ?? '');
        setPan(m.pan && !m.pan.includes('*') ? m.pan : '');
        setRmId('');
    };
    return (_jsxs("div", { className: "card-block", style: { marginTop: 8 }, children: [_jsxs("p", { className: "error", style: { marginTop: 0 }, children: [count, " equity client(s) of ", monthLabel(month), " are not in Clients. Add each one (or correct its name on the Clients screen); the month can be processed once all are found."] }), _jsxs("table", { className: "compact highlight-rows", children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Party code" }), _jsx("th", { children: "Name in the broker's file" }), _jsx("th", { children: "PAN" }), _jsx("th", { className: "num", children: "Share" }), _jsx("th", {})] }) }), _jsx("tbody", { children: list.map((m) => (_jsxs(Fragment, { children: [_jsxs("tr", { children: [_jsx("td", { className: "mono", children: m.partyCode }), _jsx("td", { children: m.name ?? '—' }), _jsx("td", { className: "mono", children: m.pan ?? _jsx("span", { className: "muted", children: "not in the file" }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: m.share }) }), _jsxs("td", { children: [canEdit && adding !== m.partyCode && (_jsx("button", { type: "button", className: "secondary", onClick: () => open(m), children: "Add client" })), m.name && (_jsx(Link, { to: `/clients?q=${encodeURIComponent(m.name.split(' ')[0])}`, style: { marginLeft: 8 }, children: "Look in Clients" }))] })] }), adding === m.partyCode && (_jsx("tr", { children: _jsxs("td", { colSpan: 5, children: [_jsxs("form", { className: "inline-form", onSubmit: async (e) => {
                                                    e.preventDefault();
                                                    const ok = await run(() => api('/api/clients', {
                                                        method: 'POST',
                                                        body: { name, pan: pan || null, rmId: Number(rmId), from: `${month}-01`, reason: `Equity client (party ${m.partyCode}) of ${monthLabel(month)}` },
                                                    }));
                                                    if (ok) {
                                                        setAdding(null);
                                                        onAdded();
                                                    }
                                                }, children: [_jsxs("label", { children: ["Client name", _jsx("input", { value: name, onChange: (e) => setName(e.target.value), required: true })] }), _jsxs("label", { children: ["PAN ", m.pan ? '' : '(optional)', _jsx("input", { value: pan, onChange: (e) => setPan(e.target.value.toUpperCase().replace(/\s/g, '')), maxLength: 10, placeholder: "ABCDE1234F" })] }), _jsxs("label", { children: ["RM", _jsxs("select", { value: rmId, onChange: (e) => setRmId(e.target.value), required: true, children: [_jsx("option", { value: "", children: "Choose\u2026" }), (employees.data ?? []).filter((x) => x.status === 'ACTIVE').map((x) => (_jsx("option", { value: x.id, children: x.branchName ? `${x.name} — ${x.branchName}` : x.name }, x.id)))] })] }), _jsx("button", { type: "submit", disabled: busy, children: "Add to Clients" }), _jsx("button", { type: "button", className: "link", onClick: () => setAdding(null), children: "Cancel" })] }), !m.pan && _jsx("p", { className: "muted", children: "The file has no PAN for this client: it is matched by this exact name. Add the PAN when you have it." }), error && _jsx("p", { className: "error", children: error })] }) }))] }, m.partyCode + (m.pan ?? '')))) })] })] }));
}
/** Step 4: the pre-check (what blocks Process, with where to fix it), Process, and the split once done. */
function ProcessStep({ row, r, canEdit, onChanged, recheck }) {
    const [jobId, setJobId] = useState(null);
    const { busy, error, run } = useSubmit();
    const { job, running } = useJob(jobId);
    useEffect(() => {
        if (job?.status === 'DONE' || job?.status === 'FAILED')
            onChanged();
        // eslint-disable-next-line react-hooks/exhaustive-deps -- once per finished run, not when the parent re-renders
    }, [job?.status]);
    const checks = r
        ? [
            { ok: r.notInMaster === 0, text: r.notInMaster === 0 ? 'Every client has an RM' : `${r.notInMaster} client(s) with business this month have no RM`, to: '/clients?show=NO_RM', link: 'Give them an RM' },
            {
                ok: r.missingPercent.length === 0,
                text: r.missingPercent.length === 0 ? 'Every partner has its % for the products it has business in' : _jsxs(_Fragment, { children: ["Partner % missing: ", _jsx("span", { className: "muted", children: r.missingPercent.join(', ') })] }),
                to: '/setup/partners',
                link: 'Set %',
            },
            { ok: r.equityNotInClients === 0, text: r.equityNotInClients === 0 ? 'Every equity PAN is a client' : `${r.equityNotInClients} equity PAN(s) not in Clients`, to: '/products?tab=EQUITY', link: 'See' },
            (() => {
                const waiting = (r.equityNameMatches?.length ?? 0) + (r.entryNameMatches?.length ?? 0);
                return { ok: waiting === 0, text: waiting === 0 ? 'Every client found by name is confirmed' : `${waiting} equity / insurance client(s) found by name only: confirm them in step 3 above`, to: '/products?tab=EQUITY', link: 'Equity tab' };
            })(),
        ]
        : [];
    const processed = row.status !== 'OPEN';
    const canRun = canEdit && row.status !== 'LOCKED' && (r?.ready || processed) && row.notInMaster === 0;
    return (_jsxs(_Fragment, { children: [!r ? (_jsx("p", { className: "muted", children: "Checking the month\u2026" })) : (_jsx("ul", { className: "todo", children: checks.map((c, i) => (_jsxs("li", { className: c.ok ? 'ok' : 'blocker', children: [_jsxs("span", { className: "what", children: [_jsx("span", { className: "mark", "aria-label": c.ok ? 'done' : 'needs fixing', children: c.ok ? '✓' : '!' }), _jsx("span", { children: c.text })] }), !c.ok && _jsx(Link, { to: c.to, children: c.link })] }, i))) })), _jsxs("div", { className: "actions", style: { marginTop: 10 }, children: [canRun && (_jsx("button", { type: "button", className: processed ? 'secondary' : undefined, disabled: running || busy, onClick: async () => {
                            await run(async () => setJobId((await api(`/api/months/${row.periodId}/process`, { method: 'POST' })).jobId));
                        }, children: running ? 'Processing…' : processed ? 'Process again' : `Process ${monthLabel(row.month)}` })), !processed && r && !r.ready && _jsx("span", { className: "muted", children: "Fix the items above, then process." }), processed && canEdit && row.status !== 'LOCKED' && _jsx("span", { className: "muted", children: "Process again after changing any entry of the month." }), _jsx("button", { type: "button", className: "link", onClick: recheck, children: "Check again" })] }), running && _jsx(JobProgressBar, { job: job, label: "Processing\u2026" }), job?.status === 'FAILED' && _jsx("p", { className: "error", children: jobErrorText(job) }), error && _jsx("p", { className: "error", children: error }), processed && (_jsxs(_Fragment, { children: [_jsx("h3", { children: "How the month was split" }), _jsx(Split, { periodId: row.periodId })] }))] }));
}
/** Step 5: the END file (a draft until locked), Finance's lock, and an admin's unlock with a reason. */
function EndStep({ row, role, onChanged }) {
    const [unlocking, setUnlocking] = useState(false);
    const [reason, setReason] = useState('');
    const { busy, error, run } = useSubmit();
    const locked = row.status === 'LOCKED';
    const canDownload = ['ADMIN', 'HO', 'FINANCE'].includes(role ?? '');
    return (_jsxs(_Fragment, { children: [_jsx("p", { className: "muted", children: locked ? `${monthLabel(row.month)} is locked: the END file is final. Corrections go into the next open month as adjustments.`
                    : 'Download the END file to check it. Finance locks the month when it is right; until then it is a draft and can be processed again.' }), _jsxs("div", { className: "actions", children: [canDownload && (_jsx("a", { className: "button-link", href: `/api/months/${row.periodId}/end-file`, download: true, children: locked ? 'END file' : 'END file (draft)' })), (role === 'FINANCE' || role === 'ADMIN') && !locked && (_jsx("button", { type: "button", disabled: busy, onClick: async () => { if (await run(() => api(`/api/months/${row.periodId}/lock`, { method: 'POST' })))
                            onChanged(); }, children: "Lock" })), role !== 'FINANCE' && role !== 'ADMIN' && !locked && _jsx("span", { className: "muted", children: "Finance locks the month." }), role === 'ADMIN' && locked && !unlocking && (_jsx("button", { type: "button", className: "secondary", onClick: () => setUnlocking(true), children: "Unlock\u2026" })), role === 'ADMIN' && locked && unlocking && (_jsxs("span", { className: "inline-form", children: [_jsx("input", { value: reason, onChange: (e) => setReason(e.target.value), placeholder: "Reason (required)", "aria-label": "Reason to unlock" }), _jsx("button", { type: "button", className: "secondary", disabled: busy || !reason.trim(), onClick: async () => { if (await run(() => api(`/api/months/${row.periodId}/unlock`, { method: 'POST', body: { reason } }))) {
                                    setUnlocking(false);
                                    onChanged();
                                } }, children: "Unlock" })] }))] }), error && _jsx("p", { className: "error", children: error })] }));
}
/** Finesse client master: which PAN (or guardian PAN) each Finesse client code belongs to. Upload when it changes. */
function ClientMasterCard({ canEdit }) {
    const status = useLoad('/api/finesse/client-master');
    const [file, setFile] = useState(null);
    const [result, setResult] = useState(null);
    const { busy, error, run } = useSubmit();
    const s = status.data;
    async function upload() {
        if (!file)
            return;
        const form = new FormData();
        form.append('file', file);
        const ok = await run(async () => setResult(await api('/api/finesse/client-master', { method: 'POST', body: form })));
        if (ok)
            status.reload();
    }
    return (_jsxs("div", { className: "source-card", children: [_jsx("h2", { children: "Finesse client master" }), _jsx("p", { className: "muted", children: "Client code \u2192 PAN and guardian PAN. Upload when it changes." }), status.error && _jsx("p", { className: "error", children: status.error }), s && s.codes === 0 && _jsx("p", { className: "warn-note", children: "Not loaded yet. Upload it before the brokerage file." }), s && s.codes > 0 && (_jsxs("p", { children: [s.codes.toLocaleString('en-IN'), " codes \u00B7 ", s.withPan.toLocaleString('en-IN'), " with PAN \u00B7 ", s.withGuardianPan, " with guardian PAN", s.updatedAt && _jsxs("span", { className: "muted", children: [" \u00B7 updated ", DATE_TIME.format(new Date(s.updatedAt))] })] })), canEdit && (_jsxs("div", { className: "upload", children: [_jsx("input", { type: "file", accept: ".xlsx", "aria-label": "Finesse client master file", onChange: (e) => setFile(e.target.files?.[0] ?? null) }), _jsx("button", { type: "button", className: "secondary", disabled: !file || busy, onClick: upload, children: busy ? 'Reading…' : 'Upload client master' })] })), error && _jsx("p", { className: "error", children: error }), result && (_jsxs("p", { className: "ok", role: "status", children: [result.rows.toLocaleString('en-IN'), " codes read: ", result.added, " new, ", result.updated, " changed, ", result.unchanged, " unchanged.", result.withoutAnyPan > 0 && ` ${result.withoutAnyPan} have no PAN or guardian PAN.`, result.invalidPans > 0 && ` ${result.invalidPans} PANs were not valid and were left out.`] }))] }));
}
/** Master MIS: PAN → RM, sub broker, referral partner and %. */
function MasterMisCard({ canEdit }) {
    return (_jsxs("div", { className: "source-card", children: [_jsx("h2", { children: "Master MIS" }), _jsx("p", { className: "muted", children: "PAN \u2192 RM, sub broker, referral partner and %. Upload when it changes, or edit a client on the Clients screen." }), _jsxs("div", { className: "upload", children: [canEdit && (_jsx(Link, { className: "button-link", to: "/setup/load-shares", children: "Upload Master MIS" })), _jsx(Link, { className: "button-link", to: "/clients", children: "Clients" })] })] }));
}
/** The month's Finesse MF brokerage file. */
function BrokerageCard({ month, onStaged }) {
    const [file, setFile] = useState(null);
    const [jobId, setJobId] = useState(null);
    const [error, setError] = useState(null);
    const { job, error: jobError, running } = useJob(jobId);
    useEffect(() => {
        if (job?.status === 'DONE' && job.result?.uploadId)
            onStaged(job.result.uploadId, job.result.notes ?? []);
        // eslint-disable-next-line react-hooks/exhaustive-deps -- run once per finished upload, not when the parent re-renders
    }, [job?.status, job?.result?.uploadId]);
    async function upload() {
        if (!file)
            return;
        setError(null);
        setJobId(null);
        const form = new FormData();
        form.append('file', file);
        form.append('month', month);
        try {
            const { jobId: id } = await api('/api/uploads', { method: 'POST', body: form });
            setJobId(id);
        }
        catch (e) {
            setError(errorText(e));
        }
    }
    const fileErrors = job?.status === 'DONE' && job.result && !job.result.uploadId ? job.result.errors : [];
    return (_jsxs("div", { className: "source-card accent", children: [_jsxs("h2", { children: ["Finesse MF brokerage for ", monthLabel(month)] }), _jsx("p", { className: "muted", children: "The month's file: client code and brokerage (net of GST). RM and partners come from Master MIS. Next: review and save." }), _jsxs("div", { className: "upload", children: [_jsx("input", { type: "file", accept: ".xlsx", "aria-label": "Finesse brokerage file", onChange: (e) => setFile(e.target.files?.[0] ?? null) }), _jsx("button", { type: "button", disabled: !file || !month || running, onClick: upload, children: running ? 'Reading…' : 'Upload brokerage' })] }), running && _jsx(JobProgressBar, { job: job, label: "Reading the file\u2026" }), error && _jsx("p", { className: "error", children: error }), jobError && _jsx("p", { className: "error", children: jobError }), job?.status === 'FAILED' && _jsxs("p", { className: "error", children: ["The file could not be read: ", jobErrorText(job)] }), fileErrors.length > 0 && (_jsxs("div", { className: "result", children: [_jsx("p", { className: "error", role: "status", children: "The file was not accepted:" }), _jsx("ul", { children: fileErrors.slice(0, 50).map((e, i) => (_jsxs("li", { children: [e.row ? `Row ${e.row}: ` : '', e.message] }, i))) })] }))] }));
}
function Review({ uploadId, notes, canEdit, onDone }) {
    const review = useLoad(`/api/uploads/${uploadId}/review`);
    const [saved, setSaved] = useState(null);
    const { busy, error, run } = useSubmit();
    const r = review.data;
    if (review.error)
        return _jsx("p", { className: "error", children: review.error });
    if (!r)
        return _jsx("p", { className: "muted", children: "Loading review\u2026" });
    const u = r.upload;
    const missingNet = r.notInMaster.reduce((sum, n) => sum + n.net, 0);
    return (_jsxs("div", { className: "card-block", children: [_jsxs("h2", { children: ["Review: ", u.fileName, " for ", monthLabel(u.month)] }), _jsxs("p", { children: [r.codes.toLocaleString('en-IN'), " client codes \u2192 ", r.clients.toLocaleString('en-IN'), " clients \u00B7 net ", _jsx("strong", { children: formatMoney(u.fileTotals.net) }), " \u00B7 GST 18%", ' ', formatMoney(u.fileTotals.gst), " \u00B7 MF trail ", formatMoney(u.fileTotals.gross)] }), _jsxs("div", { className: "stat-row", children: [_jsxs("div", { className: "stat", children: [_jsx("span", { className: "muted", children: "Matched by PAN" }), _jsx("strong", { children: r.byPan.toLocaleString('en-IN') })] }), _jsxs("div", { className: "stat", children: [_jsx("span", { className: "muted", children: "Matched by guardian PAN" }), _jsx("strong", { children: r.byGuardianPan.toLocaleString('en-IN') })] }), _jsxs("div", { className: r.toConfirm > 0 ? 'stat danger' : 'stat', children: [_jsx("span", { className: r.toConfirm > 0 ? undefined : 'muted', children: "Matched by name (no PAN)" }), _jsx("strong", { children: r.byName.toLocaleString('en-IN') })] }), _jsxs("div", { className: r.notInMaster.length > 0 ? 'stat danger' : 'stat', children: [_jsx("span", { children: "Not in Master MIS" }), _jsx("strong", { children: r.notInMaster.length })] })] }), notes.length > 0 && (_jsx("ul", { className: "notes", children: notes.map((n, i) => (_jsx("li", { children: n }, i))) })), r.notInMaster.length > 0 ? (_jsxs(_Fragment, { children: [_jsxs("h3", { className: "error", children: [r.notInMaster.length, " clients are not in Master MIS (net ", formatMoney(missingNet), "). The month can be saved, but not processed until they are added."] }), _jsxs("table", { className: "compact highlight-rows", children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Finesse code" }), _jsx("th", { children: "Client" }), _jsx("th", { children: "PAN" }), _jsx("th", { children: "Why" }), _jsx("th", { className: "num", children: "Net" }), _jsx("th", {})] }) }), _jsx("tbody", { children: r.notInMaster.map((n) => (_jsxs("tr", { children: [_jsx("td", { className: "mono", children: n.codes }), _jsx("td", { children: n.name }), _jsx("td", { className: "mono", children: n.pan ?? '—' }), _jsx("td", { children: n.reason }), _jsx("td", { className: "num", children: _jsx(Amount, { value: n.net }) }), _jsx("td", { children: canEdit && (_jsx(Link, { className: "button-link", to: `/clients?q=${encodeURIComponent(n.name)}`, children: "Give an RM" })) })] }, n.clientId))) })] }), _jsxs("p", { className: "upload", children: [_jsx("a", { className: "button-link", href: `/api/uploads/${uploadId}/not-in-master.xlsx`, download: true, children: "Download this list (Excel)" }), _jsx("span", { className: "muted", children: "Add them to Master MIS and upload it again, or give each an RM on the Clients screen." }), _jsx("button", { type: "button", className: "link", onClick: review.reload, children: "Check again" })] })] })) : (_jsx("p", { className: "ok", children: "Every client is in Master MIS." })), _jsx(NameMatches, { title: "MF client codes", refLabel: "Finesse code", amountLabel: "Net", canEdit: canEdit && u.status === 'STAGED', rows: r.nameMatches.map((m) => ({ key: m.code, ref: m.code, nameInFile: m.nameInFile, clientId: m.clientId, clientName: m.clientName, pan: m.pan, rmName: m.rmName, amount: m.net, confirmed: m.confirmed })), onConfirm: async (row, clientId) => {
                    await api(`/api/uploads/${uploadId}/name-matches`, { method: 'POST', body: { code: row.ref, clientId } });
                    review.reload();
                } }), _jsx("h3", { children: "Save the month" }), r.toConfirm > 0 && _jsxs("p", { className: "warn-note", children: ["Confirm the ", r.toConfirm, " client code(s) found by name above; then the month can be saved."] }), r.correction && (_jsxs("p", { className: "warn-note", children: [monthLabel(u.month), " is locked. Saving does not change it: the difference for each client is posted into the next open month as an adjustment."] })), u.status !== 'STAGED' ? (_jsxs("p", { className: "muted", children: ["This upload is ", u.status.toLowerCase(), "."] })) : (canEdit && (_jsxs("div", { className: "upload", children: [_jsx("button", { type: "button", disabled: busy || r.toConfirm > 0, onClick: async () => {
                            const ok = await run(async () => {
                                const res = await api(`/api/uploads/${uploadId}/commit`, { method: 'POST' });
                                setSaved(`Saved ${res.lines.lines} clients, net ${formatMoney(res.lines.net)}.`);
                            });
                            if (ok)
                                onDone();
                        }, children: r.correction ? 'Post corrections' : `Save ${monthLabel(u.month)}` }), _jsx("button", { type: "button", className: "secondary", disabled: busy, onClick: async () => {
                            if (await run(() => api(`/api/uploads/${uploadId}/discard`, { method: 'POST' })))
                                onDone();
                        }, children: "Discard this upload" })] }))), error && _jsx("p", { className: "error", children: error }), saved && _jsx("p", { className: "ok", role: "status", children: saved })] }));
}
const STATUS_LABEL = { OPEN: 'Open', PROCESSED: 'Processed', LOCKED: 'Locked' };
/** How the month's net was split: totals, then by partner and by branch. */
function Split({ periodId }) {
    const s = useLoad(`/api/months/${periodId}/summary`);
    if (s.error)
        return _jsx("p", { className: "error", children: s.error });
    if (!s.data)
        return _jsx("p", { className: "muted", children: "Loading\u2026" });
    const d = s.data;
    // Partners whose share came to 0 this month are not listed (as in the END, R-83).
    const paid = d.byPartner.filter((p) => Math.abs(p.amount) >= 0.005);
    return (_jsxs("div", { className: "split", children: [_jsx("table", { className: "compact", "aria-label": "Split totals", children: _jsxs("tbody", { children: [_jsxs("tr", { children: [_jsx("td", { children: "Net brokerage" }), _jsx("td", { className: "num", children: _jsx(Amount, { value: d.net }) })] }), _jsxs("tr", { children: [_jsx("td", { children: "Sub-brokers" }), _jsx("td", { className: "num", children: _jsx(Amount, { value: d.subBrokers }) })] }), _jsxs("tr", { children: [_jsx("td", { children: "Referral partners" }), _jsx("td", { className: "num", children: _jsx(Amount, { value: d.referrals }) })] }), _jsxs("tr", { children: [_jsx("td", { children: _jsx("strong", { children: "Epsilon keeps" }) }), _jsx("td", { className: "num", children: _jsx("strong", { children: _jsx(Amount, { value: d.epsilon }) }) })] }), d.adjustments > 0 && _jsxs("tr", { children: [_jsx("td", { children: "of which corrections to locked months" }), _jsxs("td", { className: "num", children: [d.adjustments, " clients"] })] })] }) }), _jsxs("details", { children: [_jsxs("summary", { children: ["Paid to ", paid.length, " partners"] }), _jsxs("table", { className: "compact", children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Partner" }), _jsx("th", { children: "Type" }), _jsx("th", { className: "num", children: "Clients" }), _jsx("th", { className: "num", children: "Amount" })] }) }), _jsx("tbody", { children: paid.map((p) => (_jsxs("tr", { children: [_jsx("td", { children: p.partnerName }), _jsx("td", { children: PARTNER_TYPE_LABEL[p.role] }), _jsx("td", { className: "num", children: p.clients }), _jsx("td", { className: "num", children: _jsx(Amount, { value: p.amount }) })] }, p.partnerId + p.role))) })] })] }), _jsxs("details", { children: [_jsx("summary", { children: "By branch" }), _jsxs("table", { className: "compact", children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Region" }), _jsx("th", { children: "Branch" }), _jsx("th", { className: "num", children: "Net" }), _jsx("th", { className: "num", children: "Partners" }), _jsx("th", { className: "num", children: "Epsilon" })] }) }), _jsx("tbody", { children: d.byBranch.map((b) => (_jsxs("tr", { children: [_jsx("td", { children: b.region ?? '—' }), _jsx("td", { children: b.branch ?? '—' }), _jsx("td", { className: "num", children: _jsx(Amount, { value: b.net }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: b.partners }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: b.epsilon }) })] }, `${b.region}|${b.branch}`))) })] })] })] }));
}
