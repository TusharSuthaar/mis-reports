import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useState } from 'react';
import { api, errorText } from '../../api/client';
import { useJob, jobErrorText } from '../../api/useJob';
import { formatMoney } from '../../format';
import { JobProgressBar } from '../../components/JobProgressBar';
const STATUS_TEXT = {
    MATCHED: 'same name',
    REMEMBERED: 'remembered',
    SEVERAL: 'several RMs have this name: choose',
    UNKNOWN: 'not found: choose',
};
/**
 * Load clients and their starting RM from the Investwell MF file. Step 1 shows what would happen (nothing saved);
 * step 2 loads it in one go.
 */
export function LoadClientsPage() {
    const [file, setFile] = useState(null);
    const [preview, setPreview] = useState(null);
    const [from, setFrom] = useState('2026-08-01');
    const [rmChoices, setRmChoices] = useState({});
    const [partnerNames, setPartnerNames] = useState({});
    const [error, setError] = useState(null);
    const [busy, setBusy] = useState(false);
    const [jobId, setJobId] = useState(null);
    const { job, error: jobError, running } = useJob(jobId);
    async function check() {
        if (!file)
            return;
        setError(null);
        setBusy(true);
        setJobId(null);
        const form = new FormData();
        form.append('file', file);
        try {
            const p = await api('/api/client-load/preview', { method: 'POST', body: form });
            setPreview(p);
            setRmChoices({});
            setPartnerNames({});
        }
        catch (e) {
            setError(errorText(e));
        }
        finally {
            setBusy(false);
        }
    }
    async function load() {
        if (!preview)
            return;
        setError(null);
        try {
            const body = { token: preview.token, fileName: preview.fileName, from, rmChoices, partnerNames };
            const { jobId: id } = await api('/api/client-load', { method: 'POST', body });
            setJobId(id);
        }
        catch (e) {
            setError(errorText(e));
        }
    }
    const unresolved = preview ? preview.rms.filter((r) => r.rmId === null && !rmChoices[r.spelling]) : [];
    const waiting = unresolved.reduce((n, r) => n + r.clients, 0);
    return (_jsxs("section", { children: [_jsx("h1", { children: "Load clients" }), _jsx("p", { className: "muted", children: "Upload the Investwell MF income file. Clients are created with the RM shown in the file, from the date you choose." }), _jsxs("div", { className: "upload", children: [_jsx("input", { type: "file", accept: ".xlsx", "aria-label": "Investwell MF file", onChange: (e) => setFile(e.target.files?.[0] ?? null) }), _jsx("button", { type: "button", className: "secondary", disabled: !file || busy, onClick: check, children: busy ? 'Checking…' : 'Check file' })] }), error && _jsx("p", { className: "error", children: error }), preview && (_jsxs(_Fragment, { children: [_jsx("h2", { children: preview.fileName }), preview.errors.length > 0 ? (_jsxs("div", { className: "result", children: [_jsx("p", { className: "error", role: "status", children: "The file has problems and cannot be loaded." }), _jsx("ul", { children: preview.errors.map((e, i) => (_jsxs("li", { children: [e.row ? `Row ${e.row}: ` : '', e.message] }, i))) })] })) : (_jsxs("p", { className: "ok", role: "status", children: [preview.rows, " client rows add up to the file's Total: gross ", formatMoney(preview.totals.gross), ", GST", ' ', formatMoney(preview.totals.gst), ", net ", formatMoney(preview.totals.net), "."] })), _jsxs("p", { children: [_jsx("strong", { children: preview.newClients }), " new clients, ", preview.existingClients, " already loaded. ", preview.withoutPan, " without PAN.", preview.warningCount > 0 && ` ${preview.warningCount} GST warnings.`] }), _jsx("h3", { children: "RMs in the file" }), _jsxs("table", { className: "compact", children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "In the file" }), _jsx("th", { children: "Clients" }), _jsx("th", { children: "Net" }), _jsx("th", { children: "Goes to" })] }) }), _jsx("tbody", { children: preview.rms.map((r) => (_jsxs("tr", { children: [_jsx("td", { children: r.spelling || _jsx("span", { className: "muted", children: "(blank)" }) }), _jsx("td", { children: r.clients }), _jsx("td", { className: "num", children: formatMoney(r.net) }), _jsx("td", { children: r.rmId !== null ? (_jsxs(_Fragment, { children: [r.rmLabel, " ", _jsxs("span", { className: "muted", children: ["(", STATUS_TEXT[r.status], ")"] })] })) : (_jsxs("select", { "aria-label": `RM for ${r.spelling}`, value: rmChoices[r.spelling] ?? '', onChange: (e) => setRmChoices({ ...rmChoices, [r.spelling]: Number(e.target.value) }), children: [_jsx("option", { value: "", children: "Leave without RM for now" }), (r.candidates.length > 0 ? r.candidates : preview.allRms).map((c) => (_jsx("option", { value: c.id, children: c.label }, c.id)))] })) })] }, r.spelling))) })] }), preview.subBrokers.length > 0 && (_jsxs(_Fragment, { children: [_jsx("h3", { children: "Sub-brokers in the file" }), _jsx("p", { className: "muted", children: "Each becomes a sub-broker partner. To merge a spelling into another, type the other name." }), _jsx("datalist", { id: "sub-broker-names", children: preview.subBrokers.map((s) => (_jsx("option", { value: s.partnerName ?? s.spelling }, s.spelling))) }), _jsxs("table", { className: "compact", children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "In the file" }), _jsx("th", { children: "Clients" }), _jsx("th", { children: "Net" }), _jsx("th", { children: "Partner" })] }) }), _jsx("tbody", { children: preview.subBrokers.map((s) => (_jsxs("tr", { children: [_jsx("td", { children: s.spelling }), _jsx("td", { children: s.clients }), _jsx("td", { className: "num", children: formatMoney(s.net) }), _jsxs("td", { children: [_jsx("input", { list: "sub-broker-names", "aria-label": `Partner for ${s.spelling}`, value: partnerNames[s.spelling] ?? s.partnerName ?? s.spelling, onChange: (e) => setPartnerNames({ ...partnerNames, [s.spelling]: e.target.value }) }), s.partnerId !== null && _jsxs("span", { className: "muted", children: [" (", STATUS_TEXT[s.status], ")"] })] })] }, s.spelling))) })] })] })), preview.errors.length === 0 && (_jsxs("div", { className: "upload", children: [_jsxs("label", { className: "inline", children: ["RM from", _jsx("input", { type: "date", value: from, onChange: (e) => setFrom(e.target.value), required: true })] }), _jsxs("button", { type: "button", disabled: running || !from, onClick: load, children: ["Load ", preview.newClients, " clients"] }), waiting > 0 && _jsxs("span", { className: "muted", children: [waiting, " clients will wait as \"needs RM\"."] })] }))] })), running && _jsx(JobProgressBar, { job: job, label: "Loading clients\u2026" }), jobError && _jsx("p", { className: "error", children: jobError }), job?.status === 'FAILED' && _jsxs("p", { className: "error", children: ["Nothing was loaded: ", jobErrorText(job)] }), job?.status === 'DONE' && job.result && (_jsx("div", { className: "result", children: _jsxs("p", { className: "ok", role: "status", children: ["Loaded ", job.result.clientsCreated, " clients (", job.result.clientsAlreadyThere, " were already there). ", job.result.rmAssigned, " got their RM;", ' ', job.result.needsRm, " need an RM. ", job.result.partnersCreated, " sub-brokers created, ", job.result.spellingsRemembered, " spellings remembered."] }) }))] }));
}
