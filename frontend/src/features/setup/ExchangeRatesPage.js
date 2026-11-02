import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from 'react';
import { api } from '../../api/client';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { formatDate, formatDateTime, todayIso } from '../../format';
/** USD → ₹ rates by day (R-84): fetched from Frankfurter (ECB) when a form needs a day, or set by hand here. */
export function ExchangeRatesPage() {
    const { user } = useAuth();
    const canEdit = canEditMasters(user?.role);
    const list = useLoad('/api/exchange-rates/USD');
    const [day, setDay] = useState(todayIso());
    const [rate, setRate] = useState('');
    const [reason, setReason] = useState('');
    const [checked, setChecked] = useState(null);
    const { busy, error, run } = useSubmit();
    const save = async (e) => {
        e.preventDefault();
        if (await run(() => api(`/api/exchange-rates/USD/${day}`, { method: 'PUT', body: { rate: Number(rate), reason: reason || undefined } }))) {
            setRate('');
            setReason('');
            list.reload();
        }
    };
    const fetchDay = async () => {
        const out = {};
        if (await run(async () => { out.r = await api(`/api/exchange-rates/USD/lookup?date=${day}`, { method: 'POST' }); })) {
            setChecked(out.r ?? null);
            list.reload();
        }
    };
    return (_jsxs("section", { children: [_jsx("p", { className: "muted", children: "US dollar to rupee, one rate per day. The GIFT City form fetches a day\u2019s rate from Frankfurter (free European Central Bank rates) the first time it is needed and keeps it here; only the day and \u201CUSD to INR\u201D are sent. On a weekend or holiday it is the last working day\u2019s rate. A rate set here by hand is used for that day from then on; saved entries keep the rate they were saved with." }), canEdit && (_jsxs("form", { className: "panel-card inline-form", onSubmit: save, style: { flexWrap: 'wrap', alignItems: 'flex-end', gap: 12 }, children: [_jsxs("label", { children: ["Day", _jsx("input", { type: "date", value: day, onChange: (e) => setDay(e.target.value), required: true })] }), _jsx("button", { type: "button", className: "secondary", disabled: busy || !day, onClick: fetchDay, children: "Fetch ECB rate" }), _jsxs("label", { children: ["Rate (\u20B9 per USD)", _jsx("input", { type: "number", min: 0, step: "0.0001", value: rate, onChange: (e) => setRate(e.target.value), placeholder: "e.g. 95.97" })] }), _jsxs("label", { className: "grow", children: ["Reason", _jsx("input", { value: reason, onChange: (e) => setReason(e.target.value), placeholder: "e.g. rate on the fund statement" })] }), _jsx("button", { type: "submit", disabled: busy || !rate, children: "Set by hand" }), checked && _jsx("p", { className: "muted", style: { width: '100%', margin: 0 }, children: checked.rate != null ? `${formatDate(checked.day)}: ₹${checked.rate} (${checked.note})` : checked.note }), error && _jsx("p", { className: "error", style: { width: '100%', margin: 0 }, children: error })] })), list.error && _jsx("p", { className: "error", children: list.error }), list.data && list.data.length === 0 && _jsx("p", { className: "muted", children: "No rates yet: they are added the first time a GIFT City entry needs one." }), list.data && list.data.length > 0 && (_jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Day" }), _jsx("th", { className: "num", children: "\u20B9 per USD" }), _jsx("th", { children: "Source" }), _jsx("th", { children: "Reason" }), _jsx("th", { children: "By" })] }) }), _jsx("tbody", { children: list.data.map((r) => (_jsxs("tr", { children: [_jsx("td", { children: formatDate(r.day) }), _jsx("td", { className: "num", children: r.rate }), _jsxs("td", { children: [_jsx("span", { className: r.source === 'MANUAL' ? 'badge warn' : 'badge', children: r.source === 'MANUAL' ? 'By hand' : 'ECB' }), " ", _jsx("span", { className: "muted", children: r.note })] }), _jsx("td", { children: r.reason ?? '' }), _jsxs("td", { className: "muted", children: [r.changedBy, " ", r.changedAt && `· ${formatDateTime(r.changedAt)}`] })] }, r.day))) })] }))] }));
}
