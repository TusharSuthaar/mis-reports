import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useState } from 'react';
/** 0–100 across all steps, or null when the job has not said how far it is. */
export function percentOf(p) {
    if (!p || p.steps < 1)
        return null;
    const inStep = p.total > 0 ? Math.min(p.done / p.total, 1) : 0;
    return Math.round(((Math.max(p.step, 1) - 1 + inStep) / p.steps) * 100);
}
function elapsed(since, now) {
    if (!since)
        return '';
    const s = Math.max(0, Math.floor((now - new Date(since).getTime()) / 1000));
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}
/** Progress bar for a background job: step, items done and time taken. Without progress data it shows a moving bar. */
export function JobProgressBar({ job, label = 'Working…' }) {
    const [now, setNow] = useState(() => Date.now());
    useEffect(() => {
        const t = window.setInterval(() => setNow(Date.now()), 1000);
        return () => window.clearInterval(t);
    }, []);
    const p = job?.progress ?? null;
    const percent = percentOf(p);
    const parts = [
        p && p.steps > 1 ? `Step ${p.step} of ${p.steps}` : null,
        p?.label ?? (job?.status === 'QUEUED' ? 'Waiting to start…' : label),
        p && p.total > 0 ? `${p.done.toLocaleString('en-IN')} of ${p.total.toLocaleString('en-IN')}` : null,
        elapsed(job?.startedAt, now) || null,
    ].filter(Boolean);
    return (_jsxs("div", { className: "job-progress", children: [_jsx("div", { className: percent === null ? 'job-progress-track indeterminate' : 'job-progress-track', role: "progressbar", "aria-label": p?.label ?? label, "aria-valuemin": 0, "aria-valuemax": 100, "aria-valuenow": percent ?? undefined, children: _jsx("div", { className: "job-progress-fill", style: percent === null ? undefined : { width: `${percent}%` } }) }), _jsxs("p", { className: "muted", children: [percent !== null && _jsxs("strong", { children: [percent, "% \u00B7 "] }), parts.join(' · ')] })] }));
}
