import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useMemo, useState } from 'react';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { Amount } from '../../components/Amount';
import { matchClients } from './ClientAndRm';
/**
 * Clients found by name only, each confirmed once (R-100): "Confirm" keeps the client suggested; "Different client" finds
 * the right one by PAN or client code. A confirmed match is remembered, so the same code or name is not asked again.
 */
export function NameMatches({ title, refLabel, amountLabel, rows, canEdit, onConfirm }) {
    const [picking, setPicking] = useState(null);
    const { busy, error, run } = useSubmit();
    const open = rows.filter((r) => !r.confirmed);
    if (rows.length === 0)
        return null;
    const confirm = (row, clientId) => run(async () => {
        await onConfirm(row, clientId);
        setPicking(null);
    });
    return (_jsxs("div", { className: "card-block", style: { marginTop: 8 }, children: [_jsx("p", { className: open.length > 0 ? 'error' : 'ok', style: { marginTop: 0 }, children: open.length > 0
                    ? `${title}: ${open.length} found by name only (no PAN). Check each one is the right person, then confirm it or pick the right client. Each is asked once.`
                    : `${title}: every client found by name is confirmed.` }), _jsxs("table", { className: "compact highlight-rows", children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: refLabel }), _jsx("th", { children: "Name in the file" }), _jsx("th", { children: "Client suggested" }), _jsx("th", { children: "RM" }), _jsx("th", { className: "num", children: amountLabel }), _jsx("th", {})] }) }), _jsx("tbody", { children: rows.map((r) => (_jsxs("tr", { className: r.confirmed ? 'muted' : undefined, children: [_jsx("td", { className: "mono", children: r.ref }), _jsx("td", { children: r.nameInFile ?? '—' }), _jsxs("td", { children: [r.clientName, " ", _jsx("span", { className: "mono", children: r.pan ?? 'no PAN' })] }), _jsx("td", { children: r.rmName ?? _jsx("span", { className: "muted", children: "no RM" }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: r.amount }) }), _jsxs("td", { children: [r.confirmed ? (_jsx("span", { className: "badge ok", children: "confirmed" })) : canEdit && picking !== r.key ? (_jsxs("span", { className: "inline-form", children: [_jsx("button", { type: "button", disabled: busy, onClick: () => confirm(r, r.clientId), children: "Confirm" }), _jsx("button", { type: "button", className: "secondary", disabled: busy, onClick: () => setPicking(r.key), children: "Different client\u2026" })] })) : null, picking === r.key && _jsx(PickClient, { busy: busy, onPick: (id) => confirm(r, id), onCancel: () => setPicking(null) })] })] }, r.key))) })] }), error && _jsx("p", { className: "error", children: error })] }));
}
/** Finds a client by PAN or client code (the name alone is what was not enough). */
function PickClient({ busy, onPick, onCancel }) {
    const [lookup, setLookup] = useState('');
    const [looked, setLooked] = useState('');
    const found = useLoad(looked ? `/api/clients?q=${encodeURIComponent(looked)}&size=20` : null);
    const matches = useMemo(() => (looked && found.data ? matchClients(found.data.items, looked) : null), [found.data, looked]);
    return (_jsxs("div", { className: "inline-form", style: { marginTop: 6, flexWrap: 'wrap' }, children: [_jsxs("label", { children: ["PAN or client code", _jsx("input", { value: lookup, onChange: (e) => setLookup(e.target.value.toUpperCase().replace(/\s/g, '')), onKeyDown: (e) => {
                            if (e.key === 'Enter') {
                                e.preventDefault();
                                setLooked(lookup.trim());
                            }
                        }, placeholder: "ABCDE1234F or FC01234" })] }), _jsx("button", { type: "button", className: "secondary", disabled: lookup.trim().length < 3, onClick: () => setLooked(lookup.trim()), children: "Find" }), _jsx("button", { type: "button", className: "link", onClick: onCancel, children: "Cancel" }), matches && matches.length === 0 && _jsx("p", { className: "muted", children: "No client has this PAN or code." }), (matches ?? []).map((c) => (_jsxs("button", { type: "button", className: "link", disabled: busy, onClick: () => onPick(c.id), children: ["Use ", c.name, " \u00B7 ", _jsx("span", { className: "mono", children: c.pan ?? 'no PAN' }), " \u00B7 ", c.rmName ?? 'no RM'] }, c.id)))] }));
}
