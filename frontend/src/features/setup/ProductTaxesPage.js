import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from 'react';
import { api } from '../../api/client';
import { useLoad } from '../../api/useLoad';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { DatedChangeForm } from '../../components/DatedChangeForm';
import { SidePanel } from '../../components/SidePanel';
import { formatDate } from '../../format';
/** Setup → Products & taxes: GST and TDS % per product, changed "from a date" (R-71). */
export function ProductTaxesPage() {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const lines = useLoad('/api/product-taxes');
    const [open, setOpen] = useState(null);
    const line = lines.data?.find((l) => l.product === open);
    return (_jsxs("section", { children: [_jsx("h1", { children: "Products & taxes" }), _jsx("p", { className: "muted", children: "GST and TDS for each product. A change applies from the date you give; earlier months keep the old rate. An FD entry takes the GST % of its month and starts with TDS ticked when \u201CTDS by default\u201D is on." }), lines.error && _jsx("p", { className: "error", children: lines.error }), lines.data && (_jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Product" }), _jsx("th", { className: "num", children: "GST %" }), _jsx("th", { className: "num", children: "TDS %" }), _jsx("th", { children: "TDS by default" }), _jsx("th", { children: "Since" })] }) }), _jsx("tbody", { children: lines.data.map((l) => (_jsxs("tr", { children: [_jsx("td", { children: _jsx("button", { type: "button", className: "cell-link", onClick: () => setOpen(l.product), children: l.name }) }), _jsx("td", { className: "num", children: l.now ? l.now.gstPercent : _jsx("span", { className: "muted", children: "not set" }) }), _jsx("td", { className: "num", children: l.now ? l.now.tdsPercent : '' }), _jsx("td", { children: l.now ? (l.now.tdsByDefault ? 'Yes' : 'No') : '' }), _jsx("td", { children: l.now ? formatDate(l.now.validFrom) : '' })] }, l.product))) })] })), line && _jsx(TaxPanel, { line: line, canEdit: canEdit, onClose: () => setOpen(null), onChanged: lines.reload })] }));
}
function TaxPanel({ line, canEdit, onClose, onChanged }) {
    const history = useLoad(`/api/product-taxes/${line.product}`);
    const [gst, setGst] = useState(String(line.now?.gstPercent ?? 18));
    const [tds, setTds] = useState(String(line.now?.tdsPercent ?? 0));
    const [byDefault, setByDefault] = useState(line.now?.tdsByDefault ?? false);
    const [reason, setReason] = useState('');
    return (_jsxs(SidePanel, { title: line.name, onClose: onClose, children: [canEdit && (_jsxs(DatedChangeForm, { title: "Change rates", dateLabel: "From", submitLabel: "Save", canSubmit: gst !== '' && tds !== '', onSubmit: (from) => api(`/api/product-taxes/${line.product}`, {
                    method: 'POST',
                    body: { gstPercent: Number(gst), tdsPercent: Number(tds), tdsByDefault: byDefault, from, reason },
                }), onDone: () => {
                    setReason('');
                    history.reload();
                    onChanged();
                }, children: [_jsxs("label", { children: ["GST %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: gst, onChange: (e) => setGst(e.target.value), required: true })] }), _jsxs("label", { children: ["TDS %", _jsx("input", { type: "number", min: 0, max: 100, step: "0.01", value: tds, onChange: (e) => setTds(e.target.value), required: true })] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: byDefault, onChange: (e) => setByDefault(e.target.checked) }), "TDS by default (a new entry starts with TDS ticked)"] }), _jsxs("label", { children: ["Reason", _jsx("input", { value: reason, onChange: (e) => setReason(e.target.value) })] })] })), _jsx("h3", { children: "History" }), history.data && history.data.length === 0 && _jsx("p", { className: "muted", children: "Never set." }), history.data && history.data.length > 0 && (_jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "From" }), _jsx("th", { children: "To" }), _jsx("th", { className: "num", children: "GST %" }), _jsx("th", { className: "num", children: "TDS %" }), _jsx("th", { children: "Default" }), _jsx("th", { children: "By" })] }) }), _jsx("tbody", { children: history.data.map((h) => (_jsxs("tr", { children: [_jsx("td", { children: formatDate(h.validFrom) }), _jsx("td", { children: h.validTo ? formatDate(h.validTo) : 'now' }), _jsx("td", { className: "num", children: h.gstPercent }), _jsx("td", { className: "num", children: h.tdsPercent }), _jsx("td", { children: h.tdsByDefault ? 'Yes' : 'No' }), _jsx("td", { children: h.changedBy })] }, h.id))) })] }))] }));
}
