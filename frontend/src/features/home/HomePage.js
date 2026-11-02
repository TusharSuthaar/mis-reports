import { jsxs as _jsxs, jsx as _jsx, Fragment as _Fragment } from "react/jsx-runtime";
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useLoad } from '../../api/useLoad';
import { useAuth } from '../../app/auth';
import { MASTER_READ } from '../../app/permissions';
import { Icon } from '../../components/Icon';
import { formatMoney } from '../../format';
import { Amount } from '../../components/Amount';
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const label = (ym) => `${MONTHS[Number(ym.slice(5, 7)) - 1]} ${ym.slice(0, 4)}`;
/** Dashboard: the month at a glance — revenue by product, where the month close stands, and everything waiting on someone. */
export function HomePage() {
    const { user } = useAuth();
    const canSee = !!user && MASTER_READ.includes(user.role);
    const months = useLoad(canSee ? '/api/months' : null);
    const [month, setMonth] = useState('');
    useEffect(() => {
        if (!month && months.data && months.data.length > 0) {
            const withData = months.data.filter((m) => m.saved);
            setMonth((withData[withData.length - 1] ?? months.data[months.data.length - 1]).month);
        }
    }, [months.data, month]);
    if (!canSee) {
        return (_jsxs("section", { children: [_jsxs("h1", { children: ["Welcome, ", user?.displayName] }), _jsxs("p", { className: "muted", children: ["Signed in as ", user?.username, " with role ", user?.role, "."] }), _jsxs("p", { className: "warn-note", children: ["The ", user?.role, " role has no screens yet: the view of each RM's and RD's own clients and brokerage is still to come. Until then, ask an administrator if you need to see something."] })] }));
    }
    return (_jsxs("section", { children: [_jsxs("div", { className: "page-head", children: [_jsxs("div", { children: [_jsx("h1", { children: "Dashboard" }), _jsxs("p", { className: "muted", style: { margin: 0 }, children: ["Welcome, ", user?.displayName] })] }), months.data && months.data.length > 0 && (_jsxs("label", { className: "inline", children: ["Month", _jsx("select", { value: month, onChange: (e) => setMonth(e.target.value), "aria-label": "Dashboard month", children: [...months.data].reverse().map((m) => (_jsx("option", { value: m.month, children: label(m.month) }, m.month))) })] }))] }), months.data && months.data.length === 0 && _jsxs("div", { className: "empty", children: ["No month uploaded yet. Start on ", _jsx(Link, { to: "/month", children: "Month close" }), "."] }), month && _jsx(MonthGlance, { month: month, row: months.data?.find((m) => m.month === month) }), _jsx(ToDo, { months: months.data ?? [] })] }));
}
function MonthGlance({ month, row }) {
    const range = `?from=${month}&to=${month}`;
    const fd = useLoad(`/api/fd${range}`);
    const bonds = useLoad(`/api/bonds${range}`);
    const aif = useLoad(`/api/aif${range}`);
    const loans = useLoad(`/api/loans${range}`);
    const gifts = useLoad(`/api/gift${range}`);
    const equity = useLoad(`/api/equity${range}`);
    const sum = (l) => (l ?? []).reduce((s, e) => s + e.gross, 0);
    const mf = row?.saved?.net ?? 0;
    const cards = [
        { key: 'MF', title: 'Mutual funds', value: mf, hint: row?.saved ? `${row.saved.lines.toLocaleString('en-IN')} clients` : 'not uploaded', to: '/month' },
        { key: 'FD', title: 'FD', value: sum(fd.data), hint: `${fd.data?.length ?? 0} FDs`, to: `/products?tab=FD&from=${month}&to=${month}` },
        { key: 'BOND', title: 'Bonds', value: sum(bonds.data), hint: `${bonds.data?.length ?? 0} deals`, to: `/products?tab=BOND&from=${month}&to=${month}` },
        { key: 'AIF', title: 'AIF', value: sum(aif.data), hint: `${aif.data?.length ?? 0} commitments`, to: `/products?tab=AIF&from=${month}&to=${month}` },
        { key: 'LOAN', title: 'Loans', value: sum(loans.data), hint: `${loans.data?.length ?? 0} payouts`, to: `/products?tab=LOAN&from=${month}&to=${month}` },
        { key: 'GIFT', title: 'GIFT City', value: sum(gifts.data), hint: `${gifts.data?.length ?? 0} investments`, to: `/products?tab=GIFT&from=${month}&to=${month}` },
        {
            key: 'EQUITY',
            title: 'Equity',
            value: (equity.data ?? []).reduce((s, e) => s + e.share, 0),
            hint: equity.data?.length ? `${equity.data.length} clients` : 'not uploaded',
            to: `/products?tab=EQUITY&from=${month}&to=${month}`,
        },
    ];
    const total = cards.reduce((s, c) => s + c.value, 0);
    const status = row?.status;
    const steps = [
        { label: 'Uploaded', done: !!row?.saved },
        { label: 'Processed', done: status === 'PROCESSED' || status === 'LOCKED' },
        { label: 'Locked', done: status === 'LOCKED' },
    ];
    const next = steps.find((s) => !s.done);
    return (_jsxs(_Fragment, { children: [_jsxs("div", { className: "cards", children: [_jsxs("div", { className: "stat-card", style: { borderColor: 'var(--primary)' }, children: [_jsxs("span", { className: "label", children: ["Gross revenue \u00B7 ", label(month)] }), _jsxs("span", { className: "value", children: ["\u20B9 ", _jsx(Amount, { value: total })] }), _jsxs("span", { className: "hint", children: ["excl. GST \u00B7 \u20B9 ", formatMoney(total)] })] }), cards.map((c) => (_jsxs(Link, { to: c.to, className: "stat-card", style: { textDecoration: 'none', color: 'inherit' }, children: [_jsx("span", { className: "label", children: c.title }), _jsxs("span", { className: "value", children: ["\u20B9 ", _jsx(Amount, { value: c.value })] }), _jsx("span", { className: "hint", children: c.hint })] }, c.key)))] }), _jsxs("div", { className: "panel-card", style: { marginBottom: 16 }, children: [_jsxs("h2", { children: ["Month close \u00B7 ", label(month)] }), _jsx("div", { className: "stepper", children: steps.map((s) => (_jsxs("span", { className: s.done ? 'step done' : s === next ? 'step now' : 'step', children: [_jsx("span", { className: "dot", children: s.done ? '✓' : '' }), s.label] }, s.label))) }), row && _jsx(PreCheck, { periodId: row.periodId }), _jsx("p", { className: "muted", style: { margin: '8px 0 0' }, children: next ? (_jsxs(_Fragment, { children: ["Next: ", _jsx(Link, { to: "/month", children: next.label === 'Uploaded' ? 'upload the MF file' : next.label === 'Processed' ? 'process the month' : 'Finance locks the month' })] })) : ('This month is closed.') })] })] }));
}
/**
 * Ready to process? The checks Process stops on, run without changing anything, each with a link to where it is fixed;
 * the END file once the month is processed (R-91).
 */
function PreCheck({ periodId }) {
    const check = useLoad(`/api/months/${periodId}/readiness`);
    const r = check.data;
    if (check.error)
        return _jsx("p", { className: "error", children: check.error });
    if (!r)
        return _jsx("p", { className: "muted", children: "Checking the month\u2026" });
    const n = (v) => v.toLocaleString('en-IN');
    const e = r.entries;
    const items = [
        { ok: r.mfSaved, text: r.mfSaved ? `MF figures saved (${n(r.mfClients)} clients)` : 'MF file not saved yet', to: '/month', link: 'Upload' },
        {
            ok: true,
            info: true,
            text: `Entries: FD ${e.FD} · Bonds ${e.BOND} · AIF ${e.AIF} · Loans ${e.LOAN} · GIFT City ${e.GIFT} · Equity ${r.equityUploaded ? `${n(r.equityClients)} clients` : 'not uploaded'}`,
        },
        {
            ok: r.notInMaster === 0,
            text: r.notInMaster === 0 ? 'Every client has an RM' : `${n(r.notInMaster)} client(s) with business this month have no RM (not in Master MIS)`,
            to: '/clients?show=NO_RM',
            link: 'Fix',
        },
        {
            ok: r.missingPercent.length === 0,
            text: r.missingPercent.length === 0 ? ('Every partner has its % for the products it has business in') : (_jsxs(_Fragment, { children: [r.missingPercent.length, " partner % missing: ", _jsx("span", { className: "muted", children: r.missingPercent.join(', ') })] })),
            to: '/setup/partners',
            link: 'Set %',
        },
    ];
    if (r.equityUploaded) {
        items.push({
            ok: r.equityNotInClients === 0,
            text: r.equityNotInClients === 0 ? 'Every equity PAN is a client' : `${n(r.equityNotInClients)} equity PAN(s) not in Clients`,
            to: `/products?tab=EQUITY`,
            link: 'See',
        });
    }
    const processed = r.status !== 'OPEN';
    return (_jsxs("div", { className: "precheck", children: [_jsx("h3", { children: processed ? 'Checks' : r.ready ? 'Ready to process' : 'Before you process' }), _jsx("ul", { className: "todo", children: items.map((i, k) => (_jsxs("li", { className: i.info ? 'info' : i.ok ? 'ok' : 'blocker', children: [_jsxs("span", { className: "what", children: [_jsx("span", { className: "mark", "aria-label": i.info ? 'information' : i.ok ? 'done' : 'needs fixing', children: i.info ? 'ℹ' : i.ok ? '✓' : '!' }), _jsx("span", { children: i.text })] }), !i.ok && i.to && _jsx(Link, { to: i.to, children: i.link ?? 'Open' })] }, k))) }), _jsxs("div", { className: "actions", style: { marginTop: 10 }, children: [processed ? (_jsxs("a", { className: "button-link", href: `/api/months/${periodId}/end-file`, download: true, children: ["Download END file", r.status === 'LOCKED' ? '' : ' (draft)'] })) : r.ready ? (_jsx(Link, { className: "button-link", to: "/month", children: "Go to Month close to process" })) : null, _jsx("button", { type: "button", className: "link", onClick: check.reload, children: "Check again" })] })] }));
}
/** Everything waiting on someone, each with a link to where it is fixed. */
function ToDo({ months }) {
    const fd = useLoad('/api/fd/alerts');
    const bonds = useLoad('/api/bonds/alerts');
    const aif = useLoad('/api/aif/alerts');
    const loans = useLoad('/api/loans/alerts');
    const att = useLoad('/api/clients/attention');
    const items = [
        ...months.filter((m) => m.notInMaster > 0).map((m) => ({ n: m.notInMaster, text: `clients in ${label(m.month)} not in Master MIS`, to: '/month' })),
        ...months.filter((m) => m.saved && m.status === 'OPEN').map((m) => ({ n: 1, text: `${label(m.month)} saved but not processed`, to: '/month' })),
        { n: fd.data?.pendingPayments ?? 0, text: 'FD commissions not received', to: '/products?tab=FD&payment=PENDING' },
        { n: bonds.data?.pendingPayments ?? 0, text: 'bond invoices not fully paid', to: '/products?tab=BOND&payment=PENDING' },
        { n: aif.data?.pendingPayments ?? 0, text: 'AIF invoices not fully paid', to: '/products?tab=AIF&payment=PENDING' },
        { n: loans.data?.pendingPayments ?? 0, text: 'loan payouts not received', to: '/products?tab=LOAN&payment=PENDING' },
        { n: att.data?.noRm ?? 0, text: 'clients without an RM', to: '/clients?show=NO_RM' },
        { n: att.data?.sharedPan ?? 0, text: 'clients sharing a PAN', to: '/clients?show=SHARED_PAN' },
        { n: att.data?.noPan ?? 0, text: 'clients without a PAN', to: '/clients?show=NO_PAN' },
    ].filter((i) => i.n > 0);
    return (_jsxs("div", { className: "panel-card", children: [_jsx("h2", { children: "To do" }), items.length === 0 ? (_jsx("p", { className: "muted", children: "Nothing waiting. \uD83C\uDF89" })) : (_jsx("ul", { className: "todo", children: items.map((i) => (_jsxs("li", { children: [_jsxs("span", { className: "what", children: [_jsx(Icon, { name: "alert" }), _jsxs("span", { children: [_jsx("strong", { children: i.n.toLocaleString('en-IN') }), " ", i.text] })] }), _jsx(Link, { to: i.to, children: "Open" })] }, i.text))) }))] }));
}
