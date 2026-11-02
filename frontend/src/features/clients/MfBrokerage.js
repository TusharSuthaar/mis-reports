import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useLoad } from '../../api/useLoad';
import { Amount } from '../../components/Amount';
import { formatMoney, formatMonth } from '../../format';
const STATUS = {
    OPEN: { label: 'Saved, not processed', cls: 'badge warn' },
    PROCESSED: { label: 'Processed', cls: 'badge' },
    LOCKED: { label: 'Locked', cls: 'badge ok' },
};
const pct = (f) => (f == null ? '' : ` ${Math.round(f * 10000) / 100}%`);
/** The client's MF brokerage month by month, as saved and split (R-09). */
export function MfBrokerage({ clientId }) {
    const rows = useLoad(`/api/clients/${clientId}/brokerage?product=MF`);
    if (rows.error)
        return _jsx("p", { className: "error", children: rows.error });
    if (!rows.data)
        return _jsx("p", { className: "muted", children: "Loading\u2026" });
    if (rows.data.length === 0)
        return _jsx("div", { className: "empty", children: "No MF brokerage for this client yet (no saved month has them)." });
    const sum = (k) => rows.data.reduce((s, r) => s + r[k], 0);
    const split = rows.data.filter((r) => r.epsilon != null);
    const months = new Set(rows.data.map((r) => r.month)).size;
    return (_jsxs(_Fragment, { children: [_jsxs("div", { className: "cards", children: [_jsxs("div", { className: "stat-card", children: [_jsx("span", { className: "label", children: "MF net brokerage" }), _jsxs("span", { className: "value", children: ["\u20B9 ", _jsx(Amount, { value: sum('net') })] }), _jsxs("span", { className: "hint", children: [months, " month", months === 1 ? '' : 's'] })] }), _jsxs("div", { className: "stat-card", children: [_jsx("span", { className: "label", children: "Partners\u2019 share" }), _jsxs("span", { className: "value", children: ["\u20B9 ", _jsx(Amount, { value: split.reduce((s, r) => s + (r.partnersTotal ?? 0), 0) })] }), _jsx("span", { className: "hint", children: "processed months" })] }), _jsxs("div", { className: "stat-card", children: [_jsx("span", { className: "label", children: "Epsilon keeps" }), _jsxs("span", { className: "value", children: ["\u20B9 ", _jsx(Amount, { value: split.reduce((s, r) => s + (r.epsilon ?? 0), 0) })] }), _jsx("span", { className: "hint", children: "processed months" })] })] }), _jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Month" }), _jsx("th", { className: "num", children: "MF trail (\u20B9)" }), _jsx("th", { className: "num", children: "GST" }), _jsx("th", { className: "num", children: "MF net brokerage" }), _jsx("th", { children: "Partners" }), _jsx("th", { className: "num", children: "Partners\u2019 share" }), _jsx("th", { className: "num", children: "Epsilon keeps" }), _jsx("th", { children: "RM / branch" }), _jsx("th", { children: "Status" })] }) }), _jsxs("tbody", { children: [rows.data.map((r) => (_jsxs("tr", { children: [_jsxs("td", { children: [r.month ? formatMonth(r.month) : '—', r.adjustsMonth && _jsxs("div", { className: "muted", children: ["correction for ", formatMonth(r.adjustsMonth)] })] }), _jsx("td", { className: "num", children: formatMoney(r.gross) }), _jsx("td", { className: "num", children: formatMoney(r.gst) }), _jsx("td", { className: "num", children: formatMoney(r.net) }), _jsx("td", { children: r.epsilon == null ? (_jsx("span", { className: "muted", children: "not split yet" })) : r.partners.length === 0 ? (_jsx("span", { className: "muted", children: "direct (no partner)" })) : (r.partners.map((p) => (_jsxs("div", { children: [p.name, " ", _jsxs("span", { className: "muted", children: ["\u00B7 ", p.role, pct(p.percent)] })] }, `${p.role}-${p.name}`)))) }), _jsx("td", { className: "num", children: r.partnersTotal == null ? '' : formatMoney(r.partnersTotal) }), _jsx("td", { className: "num", children: r.epsilon == null ? '' : formatMoney(r.epsilon) }), _jsx("td", { children: r.rm ? `${r.rm}${r.branch ? ` — ${r.branch}` : ''}` : _jsx("span", { className: "muted", children: "\u2014" }) }), _jsx("td", { children: r.status && _jsx("span", { className: STATUS[r.status].cls, children: STATUS[r.status].label }) })] }, r.lineId))), _jsxs("tr", { className: "total", children: [_jsx("td", { children: "Total" }), _jsx("td", { className: "num", children: _jsx(Amount, { value: sum('gross') }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: sum('gst') }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: sum('net') }) }), _jsx("td", {}), _jsx("td", { className: "num", children: _jsx(Amount, { value: split.reduce((s, r) => s + (r.partnersTotal ?? 0), 0) }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: split.reduce((s, r) => s + (r.epsilon ?? 0), 0) }) }), _jsx("td", { colSpan: 2 })] })] })] })] }));
}
