import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useLoad } from '../../api/useLoad';
import { api, errorText } from '../../api/client';
import { PARTNER_TYPE_LABEL } from '../../api/types';
import { useJob, jobErrorText } from '../../api/useJob';
import { JobProgressBar } from '../../components/JobProgressBar';
/**
 * Master MIS upload (Master_MIS.xlsx): adds the clients it has and the app does not (by PAN; by name for a row without
 * a PAN), sets each client's RM, and the partner shares. Check shows what would change from the chosen date (nothing
 * saved); Load applies it. Re-loading an updated master applies only the differences.
 */
export function LoadSharesPage() {
    const [file, setFile] = useState(null);
    const [preview, setPreview] = useState(null);
    const [from, setFrom] = useState('2026-08-01');
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
        form.append('from', from);
        try {
            setPreview(await api('/api/share-load/preview', { method: 'POST', body: form }));
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
            const { jobId: id } = await api('/api/share-load', {
                method: 'POST',
                body: { token: preview.token, fileName: preview.fileName, from: preview.from },
            });
            setJobId(id);
        }
        catch (e) {
            setError(errorText(e));
        }
    }
    const p = preview;
    const done = job?.status === 'DONE' && job.result ? job.result : null;
    const reset = () => {
        setPreview(null);
        setJobId(null);
        setFile(null);
        setError(null);
    };
    // Once loaded, the page says so and offers the next step, instead of leaving the check and its Apply button on screen.
    if (done) {
        const leftOut = (p?.unknownRms ?? []).reduce((s, u) => s + u.clients, 0);
        return (_jsxs("section", { children: [_jsx("h1", { children: "Upload Master MIS" }), _jsxs("div", { className: "card-block", role: "status", children: [_jsxs("h2", { className: "ok", style: { marginTop: 0 }, children: ["\u2713 Master MIS loaded", p ? `: ${p.fileName}` : ''] }), _jsxs("p", { children: ["Changes count from ", p?.from ?? from, "."] }), _jsx("div", { className: "figures", children: [
                                ['Clients added', done.clientsCreated],
                                ['RMs changed', done.rmChanged],
                                ['Shares set', done.sharesSet],
                                ['Shares ended', done.sharesEnded],
                                ['Partners created', done.partnersCreated],
                                ['Partner product %s set', done.productPercentsSet],
                                ['Clients with their own %', done.clientExceptions],
                            ].map(([label, n]) => (_jsxs("div", { className: "figure", children: [_jsx("span", { children: label }), _jsx("b", { children: n.toLocaleString('en-IN') })] }, label))) }), leftOut > 0 && (_jsxs("p", { className: "warn-note", children: [leftOut, " client(s) were left unchanged because their RM name in Master MIS is not known (", (p?.unknownRms ?? []).map((u) => u.spelling).join(', '), "). Map those names (or add the RM on the Organisation screen), then upload the file again: only the differences are applied."] })), _jsxs("div", { className: "actions", style: { marginTop: 12 }, children: [_jsx(Link, { className: "button-link", to: "/month", children: "Back to Month close" }), _jsx("button", { type: "button", className: "secondary", onClick: reset, children: "Upload another file" })] })] })] }));
    }
    return (_jsxs("section", { children: [_jsx("h1", { children: "Upload Master MIS" }), _jsx("p", { className: "muted", children: "Master MIS decides each client's RM, sub broker and referral partner. Clients are keyed by PAN (by name for a row without a PAN); clients the app does not have yet are added. Only clients with a partner in the master share brokerage; a partner without a % gets 0%. A second partner goes in the columns BROKER 2 | TYPE 2 | % 2." }), _jsxs("div", { className: "upload", children: [_jsxs("label", { className: "inline", children: ["Changes count from", _jsx("input", { type: "date", value: from, onChange: (e) => { setFrom(e.target.value); setPreview(null); }, required: true })] }), _jsx("input", { type: "file", accept: ".xlsx", "aria-label": "Client master", onChange: (e) => setFile(e.target.files?.[0] ?? null) }), _jsx("button", { type: "button", className: "secondary", disabled: !file || !from || busy, onClick: check, children: busy ? 'Checking…' : 'Check file' })] }), error && _jsx("p", { className: "error", children: error }), p && p.error && _jsx("p", { className: "error", role: "status", children: p.error }), p && !p.error && (_jsxs(_Fragment, { children: [_jsx("h2", { children: p.fileName }), _jsx("table", { className: "compact summary", "aria-label": "Summary", children: _jsxs("tbody", { children: [_jsxs("tr", { children: [_jsx("td", { children: "Rows in the master" }), _jsx("td", { className: "num", children: p.rows })] }), _jsxs("tr", { children: [_jsx("td", { children: _jsx("strong", { children: "New clients to add" }) }), _jsx("td", { className: "num", children: _jsx("strong", { children: p.newClients }) })] }), _jsxs("tr", { children: [_jsx("td", { children: "\u2026 of which without a PAN (kept by name)" }), _jsx("td", { className: "num", children: p.newClientsWithoutPan })] }), _jsxs("tr", { children: [_jsx("td", { children: _jsxs("strong", { children: ["RM changes from ", p.from] }) }), _jsx("td", { className: "num", children: _jsx("strong", { children: p.rmChangeCount }) })] }), _jsxs("tr", { children: [_jsx("td", { children: "RM already as in the master" }), _jsx("td", { className: "num", children: p.rmUnchanged })] }), _jsxs("tr", { children: [_jsx("td", { children: "Clients without an RM in the master" }), _jsx("td", { className: "num", children: p.clientsWithoutRm })] }), _jsxs("tr", { children: [_jsx("td", { children: "Rows with a partner" }), _jsx("td", { className: "num", children: p.rowsWithPartner })] }), _jsxs("tr", { children: [_jsx("td", { children: "Clients matched in the system" }), _jsx("td", { className: "num", children: p.matchedClients })] }), _jsxs("tr", { children: [_jsx("td", { children: _jsx("strong", { children: "Shares to set or change" }) }), _jsx("td", { className: "num", children: _jsx("strong", { children: p.toSet }) })] }), _jsxs("tr", { children: [_jsx("td", { children: "Shares to end (no partner in the master any more)" }), _jsx("td", { className: "num", children: p.toEnd })] }), _jsxs("tr", { children: [_jsx("td", { children: "Already as in the master" }), _jsx("td", { className: "num", children: p.unchanged })] }), _jsxs("tr", { children: [_jsx("td", { children: "Client % of 0 (this client gets nothing)" }), _jsx("td", { className: "num", children: p.zeroPct })] }), p.partnerRates && (_jsxs(_Fragment, { children: [_jsxs("tr", { children: [_jsx("td", { children: "Partners in the partner sheets" }), _jsx("td", { className: "num", children: p.partnerRates.sheetPartners })] }), _jsxs("tr", { children: [_jsx("td", { children: _jsx("strong", { children: "Partner product %s to set or change" }) }), _jsx("td", { className: "num", children: _jsx("strong", { children: p.partnerRates.percentChanges }) })] }), _jsxs("tr", { children: [_jsx("td", { children: "Client links that use the partner\u2019s %" }), _jsx("td", { className: "num", children: p.partnerRates.followsPartner })] }), _jsxs("tr", { children: [_jsx("td", { children: "Client links with their own % (differs from the partner\u2019s)" }), _jsx("td", { className: "num", children: p.partnerRates.clientExceptions })] })] })), _jsxs("tr", { children: [_jsx("td", { children: "Clients with both a sub-broker and a referral partner" }), _jsx("td", { className: "num", children: p.bothRoles })] }), _jsxs("tr", { children: [_jsx("td", { children: "Matched by PAN, name spelt differently" }), _jsx("td", { className: "num", children: p.matchedByPanOnly })] }), _jsxs("tr", { children: [_jsx("td", { children: "Partner rows not matched to a client" }), _jsx("td", { className: "num", children: p.unmatchedCount })] })] }) }), p.unknownRms.length > 0 && (_jsxs("details", { open: true, children: [_jsxs("summary", { className: "error", children: [p.unknownRms.length, " RM names are not known (those clients are left unchanged)"] }), _jsx("p", { className: "muted", children: "Map a name to the RM it means (remembered for every later upload), or add the RM on the Org screen. Then check the file again." }), _jsxs("table", { className: "compact", children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "RM in Master MIS" }), _jsx("th", { children: "Why" }), _jsx("th", { className: "num", children: "Clients" }), _jsx("th", { children: "Means" })] }) }), _jsx("tbody", { children: p.unknownRms.map((u) => (_jsx(UnknownRmRow, { spelling: u.spelling, note: u.note, clients: u.clients, onMapped: check }, u.spelling))) })] })] })), p.rmChanges.length > 0 && (_jsxs("details", { children: [_jsxs("summary", { children: [p.rmChangeCount, " RM changes from ", p.from, p.rmChanges.length < p.rmChangeCount ? ` (first ${p.rmChanges.length} shown)` : ''] }), _jsxs("table", { className: "compact", children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Client" }), _jsx("th", { children: "RM now" }), _jsxs("th", { children: ["RM from ", p.from] })] }) }), _jsx("tbody", { children: p.rmChanges.map((c) => (_jsxs("tr", { children: [_jsx("td", { children: c.client }), _jsx("td", { children: c.fromRm ?? _jsx("span", { className: "muted", children: "none" }) }), _jsx("td", { children: c.toRm })] }, c.clientId))) })] })] })), p.newPartners.length > 0 && (_jsxs("details", { open: true, children: [_jsxs("summary", { children: [p.newPartners.length, " partners will be created"] }), _jsxs("table", { className: "compact", children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Partner" }), _jsx("th", { children: "Type" }), _jsx("th", { children: "Clients" }), _jsx("th", { children: "Or is it\u2026" })] }) }), _jsx("tbody", { children: p.newPartners.map((np) => (_jsx(NewPartnerRow, { name: np.name, type: np.type, clients: np.clients, onMapped: check }, np.type + np.name))) })] })] })), p.partnerRates && (p.partnerRates.pending.length > 0 || p.partnerRates.approved.length > 0) && (_jsxs("div", { className: "warn-note", children: [p.partnerRates.pending.length > 0 && (_jsxs("p", { children: ["Under process, so paid nothing until approved: ", _jsx("strong", { children: p.partnerRates.pending.join(', ') })] })), p.partnerRates.approved.length > 0 && (_jsxs("p", { children: ["No longer under process, so paid again: ", _jsx("strong", { children: p.partnerRates.approved.join(', ') })] }))] })), p.problems.length > 0 && (_jsxs("details", { children: [_jsxs("summary", { children: [p.problems.length, " rows need a look (left unchanged)"] }), _jsx("ul", { children: p.problems.map((m, i) => (_jsx("li", { children: m }, i))) })] })), p.unmatchedCount > 0 && (_jsxs("details", { children: [_jsxs("summary", { children: [p.unmatchedCount, " partner rows not matched to a client", p.unmatched.length < p.unmatchedCount ? ` (first ${p.unmatched.length} shown)` : ''] }), _jsx("p", { className: "muted", children: "Usually clients not in any loaded MF file yet. Load the master again after they appear." }), _jsxs("table", { className: "compact", children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Row" }), _jsx("th", { children: "Client" }), _jsx("th", { children: "PAN" }), _jsx("th", { children: "Why" })] }) }), _jsx("tbody", { children: p.unmatched.map((u) => (_jsxs("tr", { children: [_jsx("td", { children: u.row }), _jsx("td", { children: u.clientName }), _jsx("td", { className: "mono", children: u.maskedPan }), _jsx("td", { children: u.reason })] }, u.row))) })] })] })), _jsx("div", { className: "upload", children: _jsxs("button", { type: "button", disabled: running || p.newClients + p.rmChangeCount + p.toSet + p.toEnd + (p.partnerRates?.percentChanges ?? 0) === 0, onClick: load, children: ["Apply from ", p.from, ": ", p.newClients, " new clients, ", p.rmChangeCount, " RM changes, ", p.toSet + p.toEnd, " share changes", p.partnerRates ? `, ${p.partnerRates.percentChanges} partner %s` : ''] }) }), _jsx("p", { className: "muted", children: "Partners of the new clients are set right after they are added." })] })), running && _jsx(JobProgressBar, { job: job, label: "Loading the Master MIS\u2026" }), jobError && _jsx("p", { className: "error", children: jobError }), job?.status === 'FAILED' && _jsxs("p", { className: "error", children: ["Nothing was loaded: ", jobErrorText(job)] }), job?.status === 'DONE' && job.result && (_jsxs("p", { className: "ok", role: "status", children: ["Done: ", job.result.clientsCreated, " clients added, ", job.result.rmChanged, " RMs changed, ", job.result.sharesSet, " shares set,", ' ', job.result.sharesEnded, " ended, ", job.result.partnersCreated, " partners created, ", job.result.productPercentsSet, " partner product %s set, ", job.result.clientExceptions, " clients with their own %."] }))] }));
}
/** One RM spelling Master MIS uses that the app does not know: choose the RM it means. */
/** A partner the master names that the app does not have: create it, or say it is an existing one spelt differently. */
function NewPartnerRow({ name, type, clients, onMapped }) {
    const partners = useLoad('/api/partners');
    const [partnerId, setPartnerId] = useState('');
    const [error, setError] = useState(null);
    const same = (partners.data ?? []).filter((x) => x.type === type && x.status !== 'INACTIVE');
    return (_jsxs("tr", { children: [_jsx("td", { children: name }), _jsx("td", { children: PARTNER_TYPE_LABEL[type] }), _jsx("td", { className: "num", children: clients }), _jsx("td", { children: _jsxs("span", { className: "inline-form", children: [_jsxs("select", { "aria-label": `Existing partner meant by ${name}`, value: partnerId, onChange: (e) => setPartnerId(e.target.value), children: [_jsx("option", { value: "", children: "New partner" }), same.map((x) => (_jsx("option", { value: x.id, children: x.name }, x.id)))] }), _jsx("button", { type: "button", className: "secondary", disabled: !partnerId, onClick: async () => {
                                setError(null);
                                try {
                                    await api('/api/share-load/partner-spelling', { method: 'POST', body: { spelling: name, partnerId: Number(partnerId) } });
                                    onMapped();
                                }
                                catch (e) {
                                    setError(errorText(e));
                                }
                            }, children: "Same partner, check again" }), error && _jsx("span", { className: "error", children: error })] }) })] }));
}
function UnknownRmRow({ spelling, note, clients, onMapped }) {
    const employees = useLoad('/api/employees');
    const [rmId, setRmId] = useState('');
    const [error, setError] = useState(null);
    const active = (employees.data ?? []).filter((e) => e.status === 'ACTIVE');
    return (_jsxs("tr", { children: [_jsx("td", { children: spelling }), _jsx("td", { className: "muted", children: note }), _jsx("td", { className: "num", children: clients }), _jsx("td", { children: _jsxs("span", { className: "inline-form", children: [_jsxs("select", { "aria-label": `RM meant by ${spelling}`, value: rmId, onChange: (e) => setRmId(e.target.value), children: [_jsx("option", { value: "", children: "Choose\u2026" }), active.map((e) => (_jsx("option", { value: e.id, children: e.branchName ? `${e.name} — ${e.branchName}` : e.name }, e.id)))] }), _jsx("button", { type: "button", className: "secondary", disabled: !rmId, onClick: async () => {
                                setError(null);
                                try {
                                    await api('/api/share-load/rm-spelling', { method: 'POST', body: { spelling, rmId: Number(rmId) } });
                                    onMapped();
                                }
                                catch (e) {
                                    setError(errorText(e));
                                }
                            }, children: "Map and check again" }), error && _jsx("span", { className: "error", children: error })] }) })] }));
}
