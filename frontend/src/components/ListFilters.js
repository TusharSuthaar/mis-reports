import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PAYMENT_LABEL } from '../api/types';
import { useLoad } from '../api/useLoad';
/** The filter keys a product list understands (R-77); they live in the page address, so a refresh or a shared link keeps them. */
export const FILTER_KEYS = ['from', 'to', 'q', 'rmId', 'partnerId', 'platform', 'platformId', 'lender', 'fund', 'amc', 'pop', 'payment', 'includeCancelled'];
/** Page-address marker for "every month" (a screen choice only: never sent to the server). */
const ALL_MONTHS = 'months';
/**
 * The first month of the default window: the newest month in Month close and the two before it, so the lists never open
 * empty just because the next month is not loaded yet; this month and the two before when there is none (R-77, audit M-04).
 */
export function defaultFromMonth(latest, today = new Date()) {
    const [y, m] = latest ? latest.split('-').map(Number) : [today.getFullYear(), today.getMonth() + 1];
    const d = new Date(y, m - 3, 1);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}
/** The newest month in Month close ("2026-08"); undefined while it loads, null when there is none or it cannot be read. */
function useLatestMonth() {
    const months = useLoad('/api/months');
    if (months.error)
        return null;
    if (!months.data)
        return undefined;
    return months.data.map((r) => r.month.substring(0, 7)).sort().pop() ?? null;
}
/** No month chosen and "All months" not asked for: the list shows the last 3 months. */
function windowed(params) {
    return !params.get('from') && !params.get('to') && params.get(ALL_MONTHS) !== 'all';
}
/**
 * The current filters as a query string for the list / Excel endpoints; without chosen months, the last 3 months (R-77).
 * null while the newest month is still loading, so a list is asked for once, with the right window.
 */
export function useFilterQuery() {
    const [params] = useSearchParams();
    const latest = useLatestMonth();
    const q = new URLSearchParams();
    if (windowed(params)) {
        if (latest === undefined)
            return null;
        q.set('from', defaultFromMonth(latest));
    }
    for (const k of FILTER_KEYS) {
        const v = params.get(k);
        if (v)
            q.set(k, v);
    }
    const s = q.toString();
    return s ? `?${s}` : '';
}
/** A list path with the filter query, or null (nothing asked yet) while the query is not ready. */
export function listPath(path, query) {
    return query === null ? null : `${path}${query}`;
}
/**
 * The last 3 months by default (All months on request); narrow by month range, text, RM, referral / sub-broker, platform, payment status, cancelled.
 * {@code platformKey}: "platform" (FD: a name), "platformId" (Bonds: a platform of Setup → Platforms) or "lender" (Loans).
 */
export function ListFilters({ searchHint, platformKey, platforms, platformLabel = 'Platform', payments = PAYMENT_LABEL, entryFilters = true, partnerFilter = true, }) {
    const [params, setParams] = useSearchParams();
    const employees = useLoad('/api/employees');
    const partners = useLoad('/api/partners');
    const [text, setText] = useState(params.get('q') ?? '');
    const set = (key, value) => {
        const next = new URLSearchParams(params);
        if (value)
            next.set(key, value);
        else
            next.delete(key);
        setParams(next, { replace: true });
    };
    // Search as you type, once typing pauses.
    useEffect(() => {
        const t = window.setTimeout(() => {
            if ((params.get('q') ?? '') !== text.trim())
                set('q', text.trim());
        }, 350);
        return () => window.clearTimeout(t);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [text]);
    const active = FILTER_KEYS.some((k) => params.get(k)) || params.get(ALL_MONTHS) === 'all';
    const lastThree = windowed(params);
    const latest = useLatestMonth();
    const rms = (employees.data ?? []).filter((e) => e.status === 'ACTIVE');
    const sharePartners = (partners.data ?? []).filter((p) => p.type === 'SUB_BROKER' || p.type === 'REFERRAL');
    return (_jsxs("div", { className: "filters list-filters", children: [_jsxs("label", { children: ["From month", _jsx("input", { type: "month", value: params.get('from') ?? '', onChange: (e) => set('from', e.target.value) })] }), _jsxs("label", { children: ["To month", _jsx("input", { type: "month", value: params.get('to') ?? '', onChange: (e) => set('to', e.target.value) })] }), _jsxs("label", { className: "grow", children: ["Search", _jsx("input", { value: text, onChange: (e) => setText(e.target.value), placeholder: searchHint })] }), _jsxs("label", { children: ["RM", _jsxs("select", { value: params.get('rmId') ?? '', onChange: (e) => set('rmId', e.target.value), children: [_jsx("option", { value: "", children: "All" }), rms.map((e) => (_jsx("option", { value: e.id, children: e.branchName ? `${e.name} — ${e.branchName}` : e.name }, e.id)))] })] }), partnerFilter && (_jsxs("label", { children: ["Referral / sub-broker", _jsxs("select", { value: params.get('partnerId') ?? '', onChange: (e) => set('partnerId', e.target.value), children: [_jsx("option", { value: "", children: "All" }), sharePartners.map((p) => (_jsx("option", { value: p.id, children: p.name }, p.id)))] })] })), platformKey && (_jsxs("label", { children: [platformLabel, _jsxs("select", { value: params.get(platformKey) ?? '', onChange: (e) => set(platformKey, e.target.value), children: [_jsx("option", { value: "", children: "All" }), (platforms ?? []).map((p) => (_jsx("option", { value: p.value, children: p.label }, p.value)))] })] })), entryFilters && (_jsxs("label", { children: ["Payment", _jsxs("select", { value: params.get('payment') ?? '', onChange: (e) => set('payment', e.target.value), children: [_jsx("option", { value: "", children: "All" }), Object.entries(payments).map(([k, v]) => (_jsx("option", { value: k, children: v }, k)))] })] })), entryFilters && (_jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: params.get('includeCancelled') === 'true', onChange: (e) => set('includeCancelled', e.target.checked ? 'true' : '') }), "Show cancelled"] })), active && (_jsx("button", { type: "button", className: "link", onClick: () => {
                    setText('');
                    // Only the filters go: the tab and any open pop-up stay.
                    const next = new URLSearchParams(params);
                    FILTER_KEYS.forEach((k) => next.delete(k));
                    next.delete(ALL_MONTHS);
                    setParams(next, { replace: true });
                }, children: "Clear filters" })), _jsx("p", { className: "muted window-note", children: lastThree ? (_jsxs(_Fragment, { children: ["Showing the last 3 months (from ", defaultFromMonth(latest), ").", ' ', _jsx("button", { type: "button", className: "link", onClick: () => set(ALL_MONTHS, 'all'), children: "All months" })] })) : params.get(ALL_MONTHS) === 'all' && !params.get('from') && !params.get('to') ? (_jsxs(_Fragment, { children: ["Showing all months.", ' ', _jsx("button", { type: "button", className: "link", onClick: () => set(ALL_MONTHS, ''), children: "Last 3 months" })] })) : null })] }));
}
