import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useState } from 'react';
import { indianDigits } from '../format';
const n = (s) => Number(s.replace(/,/g, '')) || 0;
const round2 = (x) => Math.round((x + Number.EPSILON) * 100) / 100;
/** State for the register-figures block, started from a saved entry when it kept its figures. */
export function useGiven(entry) {
    return useState(entry?.figuresAsGiven
        ? { on: true, gross: indianDigits(String(entry.gross)), tds: indianDigits(String(entry.tds)), gst: indianDigits(String(entry.gst)), invoice: indianDigits(String(entry.invoice)) }
        : { on: false, gross: '', tds: '', gst: '', invoice: '' });
}
/** What the request carries for the register figures. */
export function givenBody(g) {
    return g.on
        ? { figuresAsGiven: true, givenGross: n(g.gross), givenTds: g.tds === '' ? 0 : n(g.tds), givenGst: g.gst === '' ? 0 : n(g.gst), givenInvoice: g.invoice === '' ? null : n(g.invoice) }
        : { figuresAsGiven: false };
}
/** The figures to show: the register's when ticked (net = gross − TDS, blank invoice = gross + GST), else the worked-out ones. */
export function shownFigures(worked, g) {
    if (!g.on)
        return worked;
    const gross = n(g.gross), tds = n(g.tds), gst = n(g.gst);
    return { ...worked, gross, tds, gst, net: round2(gross - tds), invoice: g.invoice === '' ? round2(gross + gst) : n(g.invoice) };
}
export function GivenFiguresFields({ value, onChange }) {
    const set = (patch) => onChange({ ...value, ...patch });
    return (_jsxs(_Fragment, { children: [_jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: value.on, onChange: (e) => set({ on: e.target.checked }) }), "Use the register's figures (don't recalculate)"] }), value.on && (_jsxs(_Fragment, { children: [_jsxs("label", { children: ["Gross revenue (excl. GST)", _jsx("input", { value: value.gross, onChange: (e) => set({ gross: indianDigits(e.target.value) }), inputMode: "decimal", required: true })] }), _jsxs("label", { children: ["TDS", _jsx("input", { value: value.tds, onChange: (e) => set({ tds: indianDigits(e.target.value) }), inputMode: "decimal", "aria-label": "Register TDS" })] }), _jsxs("label", { children: ["GST", _jsx("input", { value: value.gst, onChange: (e) => set({ gst: indianDigits(e.target.value) }), inputMode: "decimal", "aria-label": "Register GST" })] }), _jsxs("label", { children: ["Invoice amount", _jsx("input", { value: value.invoice, onChange: (e) => set({ invoice: indianDigits(e.target.value) }), inputMode: "decimal", placeholder: "gross + GST", "aria-label": "Register invoice amount" })] }), _jsx("p", { className: "muted", children: "Stored exactly as entered; the END file uses these. Net = gross \u2212 TDS." })] }))] }));
}
