import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useState } from 'react';
import { useTableView } from '../../components/useTableView';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { Amount } from '../../components/Amount';
import { ListFilters, listPath, useFilterQuery } from '../../components/ListFilters';
import { Modal } from '../../components/Modal';
import { formatDate, formatDateTime, formatMoney, formatMonth } from '../../format';
const EXCHANGE = { N: 'NSE', B: 'BSE' };
/** Equity: the broker's monthly Brokerage Due report (SMC), kept trade by trade and shown per client per month (R-89). */
export function EquityPage() {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const query = useFilterQuery();
    const list = useLoad(listPath('/api/equity', query));
    const uploads = useLoad('/api/equity/uploads');
    const [open, setOpen] = useState(null);
    const rows = list.data ?? [];
    const notFound = rows.filter((r) => r.clientId == null);
    const sum = (k) => rows.reduce((s, r) => s + r[k], 0);
    const reload = () => {
        list.reload();
        uploads.reload();
    };
    const view = useTableView(rows, { month: (r) => r.month, client: (r) => r.clientName, party_code: (r) => r.partyCode, rm_branch: (r) => r.rmName, trades: (r) => r.trades, gross_recd: (r) => r.gross, franchise_share: (r) => r.share, share: (r) => r.sharePercent });
    return (_jsxs("section", { children: [_jsxs("p", { className: "muted", children: ["Equity brokerage comes from the broker\u2019s monthly ", _jsx("strong", { children: "Brokerage Due" }), " report (SMC). Epsilon\u2019s revenue is the", ' ', _jsx("strong", { children: "franchise share" }), "; it goes into the END\u2019s Equity column when the month is processed, shared with the client\u2019s own partner %."] }), canEdit && _jsx(EquityUploadForm, { onDone: reload }), _jsx(ListFilters, { searchHint: "Client name, full PAN or party code", entryFilters: false, partnerFilter: false }), list.error && _jsx("p", { className: "error", children: list.error }), list.data && (_jsxs("div", { className: "cards", children: [_jsxs("div", { className: "stat-card", children: [_jsx("span", { className: "label", children: "Franchise share (Epsilon)" }), _jsxs("span", { className: "value", children: ["\u20B9 ", _jsx(Amount, { value: sum('share') })] }), _jsxs("span", { className: "hint", children: [rows.length, " client-months"] })] }), _jsxs("div", { className: "stat-card", children: [_jsx("span", { className: "label", children: "Gross brokerage" }), _jsxs("span", { className: "value", children: ["\u20B9 ", _jsx(Amount, { value: sum('gross') })] }), _jsxs("span", { className: "hint", children: [rows.reduce((s, r) => s + r.trades, 0), " trades"] })] })] })), notFound.length > 0 && (_jsxs("p", { className: "error", children: [notFound.length, " PAN", notFound.length === 1 ? ' is' : 's are', " not in Clients yet (", notFound.map((r) => r.pan).join(', '), "). Add them on the", ' ', _jsx(Link, { to: "/clients", children: "Clients" }), " screen: the month cannot be processed until then."] })), list.data && rows.length === 0 && _jsx("div", { className: "empty", children: "No equity brokerage yet. Upload the broker\u2019s Brokerage Due report above." }), rows.length > 0 && (_jsxs(_Fragment, { children: [_jsx("div", { className: "table-scroll", children: _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [view.th('month', 'Month'), view.th('client', 'Client'), view.th('party_code', 'Party code'), view.th('rm_branch', 'RM / branch'), view.th('trades', 'Trades', 'num'), _jsx("th", { children: "Exchange" }), view.th('gross_recd', 'Gross recd (₹)', 'num'), view.th('franchise_share', 'Franchise share (₹)', 'num'), view.th('share', 'Share %', 'num')] }) }), _jsxs("tbody", { children: [view.rows.map((r) => (_jsxs("tr", { children: [_jsx("td", { children: formatMonth(r.month) }), _jsxs("td", { children: [r.clientId != null ? (_jsx(Link, { to: `/clients/${r.clientId}`, children: r.clientName })) : (_jsxs(_Fragment, { children: [r.clientName, " ", _jsx("span", { className: "badge warn", children: "not in Clients" })] })), _jsx("div", { className: "muted mono", children: r.pan })] }), _jsx("td", { children: _jsx("button", { type: "button", className: "cell-link mono", onClick: () => setOpen(r), children: r.partyCode }) }), _jsx("td", { children: r.rmName ? `${r.rmName}${r.branchName ? ` — ${r.branchName}` : ''}` : _jsx("span", { className: "muted", children: "\u2014" }) }), _jsx("td", { className: "num", children: r.trades }), _jsx("td", { children: (r.exchanges ?? '').split('+').map((x) => EXCHANGE[x] ?? x).join(' + ') }), _jsx("td", { className: "num", children: formatMoney(r.gross) }), _jsx("td", { className: "num", children: formatMoney(r.share) }), _jsx("td", { className: "num", children: r.sharePercent ?? '' })] }, `${r.month}-${r.partyCode}-${r.pan}`))), _jsxs("tr", { className: "total", children: [_jsx("td", { colSpan: 6, children: "Total" }), _jsx("td", { className: "num", children: _jsx(Amount, { value: sum('gross') }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: sum('share') }) }), _jsx("td", {})] })] })] }) }), view.more] })), _jsx("h2", { style: { marginTop: 24 }, children: "Uploads" }), uploads.data && uploads.data.length === 0 && _jsx("p", { className: "muted", children: "No report uploaded yet." }), uploads.data && uploads.data.length > 0 && (_jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Month" }), _jsx("th", { children: "File" }), _jsx("th", { className: "num", children: "Trades" }), _jsx("th", { className: "num", children: "Clients" }), _jsx("th", { className: "num", children: "Gross (\u20B9)" }), _jsx("th", { className: "num", children: "Franchise share (\u20B9)" }), _jsx("th", { children: "Status" }), _jsx("th", { children: "Uploaded" })] }) }), _jsx("tbody", { children: uploads.data.map((u) => (_jsxs("tr", { className: u.status === 'REPLACED' ? 'muted' : undefined, children: [_jsx("td", { children: formatMonth(u.month) }), _jsxs("td", { children: [u.fileName, u.broker && _jsx("div", { className: "muted", children: u.broker })] }), _jsx("td", { className: "num", children: u.trades }), _jsx("td", { className: "num", children: u.clients }), _jsx("td", { className: "num", children: formatMoney(u.grossTotal) }), _jsx("td", { className: "num", children: formatMoney(u.shareTotal) }), _jsx("td", { children: u.status === 'ACTIVE' ? _jsx("span", { className: "badge ok", children: "In use" }) : _jsx("span", { className: "badge", children: "Replaced" }) }), _jsxs("td", { className: "muted", children: [u.uploadedBy, " \u00B7 ", formatDateTime(u.uploadedAt)] })] }, u.id))) })] })), open && (_jsx(Modal, { title: `${open.clientName ?? open.pan} — ${formatMonth(open.month)}`, onClose: () => setOpen(null), size: "narrow", children: _jsx(Trades, { month: open.month, party: open.partyCode }) }))] }));
}
function Trades({ month, party }) {
    const trades = useLoad(`/api/equity/trades?month=${month}&party=${encodeURIComponent(party)}`);
    if (trades.error)
        return _jsx("p", { className: "error", children: trades.error });
    if (!trades.data)
        return _jsx("p", { className: "muted", children: "Loading\u2026" });
    return (_jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Date" }), _jsx("th", { children: "Exchange" }), _jsx("th", { className: "num", children: "Gross recd" }), _jsx("th", { className: "num", children: "Franchise share" })] }) }), _jsx("tbody", { children: trades.data.map((t, i) => (_jsxs("tr", { children: [_jsx("td", { children: formatDate(t.date) }), _jsx("td", { children: EXCHANGE[t.exchange ?? ''] ?? t.exchange }), _jsx("td", { className: "num", children: formatMoney(t.gross) }), _jsx("td", { className: "num", children: formatMoney(t.share) })] }, i))) })] }));
}
/** Upload the broker's report: the month comes from its trade dates; a later upload of a month replaces the earlier one. */
function EquityUploadForm({ onDone }) {
    const [file, setFile] = useState(null);
    const [result, setResult] = useState(null);
    const { busy, error, run } = useSubmit();
    const upload = async () => {
        if (!file)
            return;
        const form = new FormData();
        form.append('file', file);
        const out = {};
        if (await run(async () => { out.r = await api('/api/equity/upload', { method: 'POST', body: form }); })) {
            setResult(out.r ?? null);
            onDone();
        }
    };
    return (_jsxs("div", { className: "panel-card", style: { marginBottom: 16 }, children: [_jsx("h2", { style: { marginTop: 0 }, children: "Upload the broker\u2019s Brokerage Due report" }), _jsxs("span", { className: "inline-form", children: [_jsxs("label", { children: ["Equity source file (.xlsx)", _jsx("input", { type: "file", accept: ".xlsx,.xls", onChange: (e) => setFile(e.target.files?.[0] ?? null) })] }), _jsx("button", { type: "button", disabled: !file || busy, onClick: upload, children: busy ? 'Uploading…' : 'Upload' })] }), error && _jsx("p", { className: "error", children: error }), result && (_jsxs("p", { role: "status", className: "success", children: [formatMonth(result.month), ": ", result.trades, " trades, ", result.clients, " clients (", result.clientsFound, " found in Clients", result.clientsNotFound > 0 ? `, ${result.clientsNotFound} not found` : '', ") \u00B7 gross ", formatMoney(result.grossTotal), " \u00B7 franchise share", ' ', _jsx("strong", { children: formatMoney(result.shareTotal) }), result.replacedUploadId != null && ' · replaced the month’s earlier upload', "."] }))] }));
}
