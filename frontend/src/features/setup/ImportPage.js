import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from 'react';
import { api, errorText } from '../../api/client';
import { useJob, jobErrorText } from '../../api/useJob';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { JobProgressBar } from '../../components/JobProgressBar';
/** Upload the masters workbook: "Check file" is a dry run, "Import" applies it (all or nothing). */
export function ImportPage() {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const [file, setFile] = useState(null);
    const [jobId, setJobId] = useState(null);
    const [error, setError] = useState(null);
    const { job, error: jobError, running } = useJob(jobId);
    async function start(dryRun) {
        if (!file)
            return;
        setError(null);
        setJobId(null);
        const form = new FormData();
        form.append('file', file);
        form.append('dryRun', String(dryRun));
        try {
            const { jobId: id } = await api('/api/imports/masters', { method: 'POST', body: form });
            setJobId(id);
        }
        catch (e) {
            setError(errorText(e));
        }
    }
    const result = job?.result ?? null;
    return (_jsxs("section", { children: [_jsx("h1", { children: "Import masters" }), _jsxs("ol", { className: "steps", children: [_jsxs("li", { children: [_jsx("a", { href: "/api/imports/masters/template", download: true, children: "Download the template" }), ' ', "and fill in the sheets you need (Regions, Branches, Employees, RD, RM-Branch, Partners, Partner-RM). The first sheet explains each column."] }), _jsxs("li", { children: ["Choose the file and press ", _jsx("strong", { children: "Check file" }), ": nothing is saved, you see what would change and any problems."] }), _jsxs("li", { children: ["When the check is clean, press ", _jsx("strong", { children: "Import" }), ". The whole file is applied, or nothing is."] })] }), canEdit ? (_jsxs("div", { className: "upload", children: [_jsx("input", { type: "file", accept: ".xlsx", "aria-label": "Masters workbook", onChange: (e) => setFile(e.target.files?.[0] ?? null) }), _jsx("button", { type: "button", className: "secondary", disabled: !file || running, onClick: () => start(true), children: "Check file" }), _jsx("button", { type: "button", disabled: !file || running, onClick: () => start(false), children: "Import" })] })) : (_jsx("p", { className: "muted", children: "Only ADMIN and HO can import." })), error && _jsx("p", { className: "error", children: error }), jobError && _jsx("p", { className: "error", children: jobError }), running && _jsx(JobProgressBar, { job: job, label: "Importing\u2026" }), job?.status === 'FAILED' && _jsxs("p", { className: "error", children: ["The import failed: ", jobErrorText(job)] }), result && _jsx(ImportResultView, { result: result })] }));
}
function ImportResultView({ result }) {
    const ok = result.errors.length === 0;
    let headline;
    if (!ok)
        headline = `${result.errors.length} problem(s) found. Nothing was saved.`;
    else if (result.dryRun)
        headline = 'The file is clean. Nothing was saved yet: press Import to apply it.';
    else
        headline = 'Imported.';
    return (_jsxs("div", { className: "result", children: [_jsxs("p", { className: ok ? 'ok' : 'error', role: "status", children: [_jsxs("strong", { children: [result.fileName, ":"] }), " ", headline] }), result.sheets.length > 0 && (_jsxs("table", { className: "compact", children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Sheet" }), _jsx("th", { children: result.applied ? 'Created' : 'Would create' }), _jsx("th", { children: result.applied ? 'Updated' : 'Would update' }), _jsx("th", { children: "Unchanged" })] }) }), _jsx("tbody", { children: result.sheets.map((s) => (_jsxs("tr", { children: [_jsx("td", { children: s.sheet }), _jsx("td", { children: s.created }), _jsx("td", { children: s.updated }), _jsx("td", { children: s.unchanged })] }, s.sheet))) })] })), !ok && (_jsxs("table", { className: "compact", children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Sheet" }), _jsx("th", { children: "Row" }), _jsx("th", { children: "Problem" })] }) }), _jsx("tbody", { children: result.errors.map((e, i) => (_jsxs("tr", { children: [_jsx("td", { children: e.sheet }), _jsx("td", { children: e.row || '' }), _jsx("td", { children: e.message })] }, i))) })] }))] }));
}
