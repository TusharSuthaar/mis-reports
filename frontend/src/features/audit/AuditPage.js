import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { Fragment, useEffect, useState } from 'react';
import { api, ApiError } from '../../api/client';
import { formatDateTime } from '../../format';
const PAGE_SIZE = 50;
const EMPTY = { entity: '', entityId: '', action: '', by: '', from: '', to: '' };
/** Builds the query string; local dates become instants, and "to" is inclusive (sent as the next midnight). */
export function auditQuery(f, page) {
    const q = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
    for (const key of ['entity', 'entityId', 'action', 'by']) {
        if (f[key].trim())
            q.set(key, f[key].trim());
    }
    if (f.from)
        q.set('from', new Date(`${f.from}T00:00:00`).toISOString());
    if (f.to) {
        const end = new Date(`${f.to}T00:00:00`);
        end.setDate(end.getDate() + 1);
        q.set('to', end.toISOString());
    }
    return q.toString();
}
export function AuditPage() {
    const [draft, setDraft] = useState(EMPTY);
    const [filters, setFilters] = useState(EMPTY);
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [error, setError] = useState(null);
    const [open, setOpen] = useState(null);
    useEffect(() => {
        // Only the answer for the current filters/page is shown (a slow earlier one arriving last is dropped).
        let current = true;
        setError(null);
        api(`/api/audit?${auditQuery(filters, page)}`)
            .then((d) => current && setData(d))
            .catch((e) => current && setError(e instanceof ApiError ? e.message : 'Could not load the audit log'));
        return () => {
            current = false;
        };
    }, [filters, page]);
    function onSearch(e) {
        e.preventDefault();
        setPage(0);
        setFilters(draft);
    }
    const field = (key, label, type = 'text') => (_jsxs("label", { children: [label, _jsx("input", { type: type, value: draft[key], onChange: (e) => setDraft({ ...draft, [key]: e.target.value }) })] }));
    const lastPage = data ? Math.max(0, Math.ceil(data.total / PAGE_SIZE) - 1) : 0;
    return (_jsxs("section", { children: [_jsx("h1", { children: "Audit log" }), _jsxs("form", { className: "filters", onSubmit: onSearch, children: [field('entity', 'Entity'), field('entityId', 'Entity id'), field('action', 'Action'), field('by', 'By user'), field('from', 'From', 'date'), field('to', 'To', 'date'), _jsx("button", { type: "submit", children: "Search" })] }), error && _jsx("p", { className: "error", children: error }), data && data.items.length === 0 && _jsx("p", { className: "muted", children: "No audit entries match." }), data && data.items.length > 0 && (_jsxs(_Fragment, { children: [_jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "When" }), _jsx("th", { children: "By" }), _jsx("th", { children: "Entity" }), _jsx("th", { children: "Id" }), _jsx("th", { children: "Action" })] }) }), _jsx("tbody", { children: data.items.map((a) => (_jsxs(Fragment, { children: [_jsxs("tr", { className: "clickable", onClick: () => setOpen(open === a.id ? null : a.id), children: [_jsx("td", { children: formatDateTime(a.at) }), _jsx("td", { children: a.by }), _jsx("td", { children: a.entity }), _jsx("td", { children: a.entityId }), _jsx("td", { children: a.action })] }), open === a.id && (_jsx("tr", { className: "detail", children: _jsx("td", { colSpan: 5, children: _jsxs("div", { className: "diff", children: [_jsxs("div", { children: [_jsx("strong", { children: "Before" }), _jsx("pre", { children: a.before == null ? '—' : JSON.stringify(a.before, null, 2) })] }), _jsxs("div", { children: [_jsx("strong", { children: "After" }), _jsx("pre", { children: a.after == null ? '—' : JSON.stringify(a.after, null, 2) })] })] }) }) }))] }, a.id))) })] }), _jsxs("div", { className: "pager", children: [_jsx("button", { type: "button", className: "secondary", disabled: page === 0, onClick: () => setPage(page - 1), children: "Previous" }), _jsxs("span", { children: ["Page ", page + 1, " of ", lastPage + 1, " \u00B7 ", data.total, " entries"] }), _jsx("button", { type: "button", className: "secondary", disabled: page >= lastPage, onClick: () => setPage(page + 1), children: "Next" })] })] }))] }));
}
