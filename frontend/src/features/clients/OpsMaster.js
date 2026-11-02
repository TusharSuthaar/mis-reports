import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useLoad } from '../../api/useLoad';
import { formatDateTime } from '../../format';
/** How the Ops columns are grouped on the page; a column not listed here shows under "Other". */
const GROUPS = [
    { title: 'Contact', labels: ['Email', 'Reporting Email', 'Phone Number', 'Call me as', 'Salutation'] },
    { title: 'Family', labels: ['Family Name', 'Family Head Code', 'Family Head Name', 'Family Head', 'Relationship with Family'] },
    {
        title: 'Personal',
        labels: ['DOB', 'AGE', 'Gender', 'Resident Status', 'Individual', 'Marital Status', 'Spouse Name', 'Anniversary Date', 'Employment', 'Occupation',
            'Company Working for', 'Designation', 'Website', 'Guardian Name'],
    },
    {
        title: 'Holding and nominees',
        labels: ['Holding Nature', 'Joint Holder 1 Name', 'Joint Holder 1 PAN', 'Joint Holder 1 DOB', 'Joint Holder 2 Name', 'Joint Holder 2 PAN',
            'Joint Holder 2 DOB', 'Nominee Name', 'Nominee Relation', 'Nominee Contact'],
    },
    {
        title: 'Account with Epsilon',
        labels: ['RM', 'SRM', 'Branch Name', 'Date of joining', 'Client Status', 'Billable Status', 'Client Tag', 'Client Type', 'Client Category',
            'Login Status', 'Referred By'],
    },
    {
        title: 'Exchanges, mandates and KYC',
        labels: ['BSE Login Status', 'BSE Mandate Status', 'NSE Login Status', 'NSE Mandate Status', 'MFU Login Status', 'MFU Mandate Status',
            'DIGIO Mandate', 'KYC Status', 'KYC Fetch Date', 'Latest Agreement Date', 'Latest Agreement Status', 'Risk Profile', 'Risk Score',
            'Latest Risk Assessment', 'Agreed Debt Ratio', 'Agreed Equity Ratio'],
    },
];
const key = (s) => s.trim().toLowerCase();
/** The record's fields in their groups, in the Ops file's order within a group; empty groups are dropped. */
export function groupOpsFields(details) {
    const placed = new Set();
    const groups = GROUPS.map((g) => {
        const wanted = new Set(g.labels.map(key));
        const fields = details.filter((f) => wanted.has(key(f.label)));
        fields.forEach((f) => placed.add(f.label));
        return { title: g.title, fields };
    });
    groups.push({ title: 'Other', fields: details.filter((f) => !placed.has(f.label)) });
    return groups.filter((g) => g.fields.length > 0);
}
/** One address row as a readable block: the address lines, then city, state, PIN, country. */
function addressText(a) {
    const get = (l) => a.find((f) => key(f.label) === key(l))?.value;
    const lineFields = a.filter((f) => /^address line/i.test(f.label));
    const lines = lineFields.length > 0 ? lineFields.map((f) => f.value) : [get('Address') ?? ''].filter(Boolean);
    const place = [get('City'), get('State'), get('PIN Code')].filter(Boolean).join(', ');
    const country = get('Country');
    const used = new Set(['address tag', 'address', 'city', 'state', 'pin code', 'country', ...lineFields.map((f) => key(f.label))]);
    return { tag: get('Address Tag') ?? null, lines: [...lines, place, country ?? ''].filter(Boolean), rest: a.filter((f) => !used.has(key(f.label))) };
}
/** Everything the Ops client master has for this client, one card per client code (R-08). */
export function OpsMaster({ clientId }) {
    const records = useLoad(`/api/clients/${clientId}/ops-master`);
    if (records.error)
        return _jsx("p", { className: "error", children: records.error });
    if (!records.data)
        return null;
    if (records.data.length === 0) {
        return (_jsxs("div", { className: "panel-card", children: [_jsx("h2", { children: "Ops client master" }), _jsx("p", { className: "muted", children: "Not in the Ops client master: no Finesse code has this client\u2019s PAN. Upload the latest ClientMasterReport on Data uploads." })] }));
    }
    return (_jsx(_Fragment, { children: records.data.map((r) => (_jsxs("div", { className: "panel-card ops-master", children: [_jsxs("div", { className: "ops-head", children: [_jsxs("h2", { children: ["Ops client master ", _jsx("span", { className: "badge mono", children: r.code })] }), _jsxs("span", { className: "muted", children: ["From the Ops file loaded ", formatDateTime(r.updatedAt)] })] }), _jsxs("dl", { className: "facts", children: [_jsx("dt", { children: "Name in Ops" }), _jsx("dd", { children: r.name }), _jsx("dt", { children: "PAN in Ops" }), _jsx("dd", { className: "mono", children: r.pan ?? '—' }), r.guardianPan && (_jsxs(_Fragment, { children: [_jsx("dt", { children: "Guardian PAN" }), _jsx("dd", { className: "mono", children: r.guardianPan })] }))] }), _jsxs("div", { className: "ops-groups", children: [groupOpsFields(r.details).map((g) => (_jsxs("section", { children: [_jsx("h3", { children: g.title }), _jsx("dl", { className: "facts", children: g.fields.map((f) => (_jsxs("div", { className: "fact-row", children: [_jsx("dt", { children: f.label }), _jsx("dd", { className: /pan/i.test(f.label) ? 'mono' : undefined, children: f.value })] }, f.label))) })] }, g.title))), r.addresses.map((a, i) => {
                            const t = addressText(a);
                            return (_jsxs("section", { children: [_jsxs("h3", { children: ["Address", t.tag ? ` (${t.tag})` : ''] }), _jsx("address", { className: "ops-address", children: t.lines.map((l, j) => (_jsx("div", { children: l }, j))) }), t.rest.length > 0 && (_jsx("dl", { className: "facts", children: t.rest.map((f) => (_jsxs("div", { className: "fact-row", children: [_jsx("dt", { children: f.label }), _jsx("dd", { children: f.value })] }, f.label))) }))] }, `addr-${i}`));
                        })] })] }, r.code))) }));
}
