import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { formatDate, formatDateTime } from '../format';
/** A dated history, newest first. An empty "To" means the entry is current. */
export function HistoryTable({ rows, label, actions }) {
    if (rows.length === 0)
        return _jsx("p", { className: "muted", children: "No history yet." });
    return (_jsxs("table", { className: "compact", children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: label }), _jsx("th", { children: "From" }), _jsx("th", { children: "To" }), actions && _jsx("th", {})] }) }), _jsx("tbody", { children: rows.map((r) => (_jsxs("tr", { title: `Last changed by ${r.changedBy}, ${formatDateTime(r.changedAt)}`, children: [_jsx("td", { children: r.name }), _jsx("td", { children: formatDate(r.validFrom) }), _jsx("td", { children: r.validTo ? formatDate(r.validTo) : _jsx("span", { className: "badge", children: "current" }) }), actions && _jsx("td", { children: actions(r) })] }, r.id))) })] }));
}
