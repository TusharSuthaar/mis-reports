import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useEffect, useState } from 'react';
import { api, errorText } from '../../api/client';
import { formatDateTime } from '../../format';
const PAGE_SIZE = 50;
const EMPTY = { kind: '', problemsOnly: false, by: '', reference: '', from: '', to: '' };
const KINDS = [
    { value: '', label: 'Everything' },
    { value: 'JOB', label: 'Uploads, imports and processing' },
    { value: 'EVENT', label: 'Saves, locks and downloads' },
    { value: 'ERROR', label: 'Unexpected errors' },
];
const RESULT_TEXT = {
    WAITING: 'Waiting',
    RUNNING: 'Running',
    DONE: 'Done',
    FAILED: 'Failed',
};
/** Query string; local dates become instants and "to" is inclusive (sent as the next midnight). */
export function activityQuery(f, page) {
    const q = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
    if (f.kind)
        q.set('kind', f.kind);
    if (f.problemsOnly)
        q.set('problemsOnly', 'true');
    if (f.by.trim())
        q.set('by', f.by.trim());
    if (f.reference.trim())
        q.set('reference', f.reference.trim());
    if (f.from)
        q.set('from', new Date(`${f.from}T00:00:00`).toISOString());
    if (f.to) {
        const end = new Date(`${f.to}T00:00:00`);
        end.setDate(end.getDate() + 1);
        q.set('to', end.toISOString());
    }
    return q.toString();
}
/** How long a job took, in words ("12 s", "3 min"). */
export function took(a) {
    if (!a.finishedAt)
        return '';
    const s = Math.max(0, Math.round((new Date(a.finishedAt).getTime() - new Date(a.at).getTime()) / 1000));
    return s < 90 ? `${s} s` : `${Math.round(s / 60)} min`;
}
/** What happened, who did it, did it work — uploads, processing, locks, downloads and errors in one list. */
export function ActivityPage() {
    const [draft, setDraft] = useState(EMPTY);
    const [filters, setFilters] = useState(EMPTY);
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [summary, setSummary] = useState(null);
    const [error, setError] = useState(null);
    const [refreshed, setRefreshed] = useState(0);
    useEffect(() => {
        // Only the answer for the current filters/page is shown (a slow earlier one arriving last is dropped).
        let current = true;
        setError(null);
        api(`/api/activity?${activityQuery(filters, page)}`)
            .then((d) => current && setData(d))
            .catch((e) => current && setError(errorText(e)));
        api('/api/activity/summary')
            .then((d) => current && setSummary(d))
            .catch(() => current && setSummary(null));
        return () => {
            current = false;
        };
    }, [filters, page, refreshed]);
    function onSearch(e) {
        e.preventDefault();
        setPage(0);
        setFilters(draft);
    }
    function showProblems() {
        const next = { ...EMPTY, problemsOnly: true };
        setDraft(next);
        setPage(0);
        setFilters(next);
    }
    const lastPage = data ? Math.max(0, Math.ceil(data.total / PAGE_SIZE) - 1) : 0;
    return (_jsxs("section", { children: [_jsxs("div", { className: "page-head", children: [_jsx("h1", { children: "Activity" }), _jsx("button", { type: "button", className: "secondary", onClick: () => setRefreshed((n) => n + 1), children: "Refresh" })] }), _jsxs("p", { className: "muted", children: ["Everything that was uploaded, processed, saved, locked or downloaded, and anything that went wrong. When something fails, the ", _jsx("strong", { children: "reference" }), " is what the support team needs: the same reference is on every log line."] }), summary && (_jsxs("div", { className: "stat-row", children: [_jsxs("div", { className: "stat", children: [_jsx("span", { children: "Running now" }), _jsx("strong", { children: summary.runningJobs })] }), _jsxs("button", { type: "button", className: `stat stat-button${summary.failedJobs > 0 ? ' danger' : ''}`, onClick: showProblems, title: "Show only what failed", children: [_jsx("span", { children: "Failed jobs, last 7 days" }), _jsx("strong", { children: summary.failedJobs })] }), _jsxs("button", { type: "button", className: `stat stat-button${summary.errors > 0 ? ' danger' : ''}`, onClick: showProblems, title: "Show only what failed", children: [_jsx("span", { children: "Unexpected errors, last 7 days" }), _jsx("strong", { children: summary.errors })] })] })), _jsxs("form", { className: "filters", onSubmit: onSearch, children: [_jsxs("label", { children: ["Show", _jsx("select", { value: draft.kind, onChange: (e) => setDraft({ ...draft, kind: e.target.value }), children: KINDS.map((k) => (_jsx("option", { value: k.value, children: k.label }, k.value))) })] }), _jsxs("label", { children: ["Who", _jsx("input", { value: draft.by, onChange: (e) => setDraft({ ...draft, by: e.target.value }), placeholder: "Username" })] }), _jsxs("label", { children: ["Reference", _jsx("input", { value: draft.reference, onChange: (e) => setDraft({ ...draft, reference: e.target.value }), placeholder: "e.g. 7K2MQX4P" })] }), _jsxs("label", { children: ["From", _jsx("input", { type: "date", value: draft.from, onChange: (e) => setDraft({ ...draft, from: e.target.value }) })] }), _jsxs("label", { children: ["To", _jsx("input", { type: "date", value: draft.to, onChange: (e) => setDraft({ ...draft, to: e.target.value }) })] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: draft.problemsOnly, onChange: (e) => setDraft({ ...draft, problemsOnly: e.target.checked }) }), "Only problems"] }), _jsx("button", { type: "submit", children: "Search" })] }), error && _jsx("p", { className: "error", children: error }), data && data.items.length === 0 && _jsx("p", { className: "muted", children: "Nothing matches." }), data && data.items.length > 0 && (_jsxs(_Fragment, { children: [_jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "When" }), _jsx("th", { children: "What" }), _jsx("th", { children: "Who" }), _jsx("th", { children: "Result" }), _jsx("th", { children: "Reference" })] }) }), _jsx("tbody", { children: data.items.map((a) => (_jsxs("tr", { children: [_jsx("td", { children: formatDateTime(a.at) }), _jsxs("td", { children: [a.title, a.detail && (_jsx("div", { className: a.result === 'FAILED' ? 'error' : 'muted', children: a.detail }))] }), _jsx("td", { children: a.by }), _jsxs("td", { children: [_jsx("span", { className: `badge${a.result === 'FAILED' ? ' fail-badge' : a.result === 'DONE' ? '' : ' warn-badge'}`, children: RESULT_TEXT[a.result] }), took(a) && _jsx("div", { className: "muted", children: took(a) })] }), _jsx("td", { className: "mono", children: a.reference ?? '—' })] }, `${a.kind}-${a.id}`))) })] }), _jsxs("div", { className: "pager", children: [_jsx("button", { type: "button", className: "secondary", disabled: page === 0, onClick: () => setPage(page - 1), children: "Previous" }), _jsxs("span", { children: ["Page ", page + 1, " of ", lastPage + 1, " \u00B7 ", data.total, " entries"] }), _jsx("button", { type: "button", className: "secondary", disabled: page >= lastPage, onClick: () => setPage(page + 1), children: "Next" })] })] }))] }));
}
