import { Fragment as _Fragment, jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useSyncExternalStore } from 'react';
import { formatAmount, formatMoney } from '../format';
/*
 * The "Amounts" switch (₹ / K / Lakhs). It only changes how lists, totals and the Dashboard show figures — entry forms,
 * Excel downloads and the END file always keep exact rupees. Remembered in this browser.
 */
const KEY = 'amountUnit';
const listeners = new Set();
function read() {
    try {
        const v = localStorage.getItem(KEY);
        return v === 'full' || v === 'K' || v === 'L' ? v : 'L';
    }
    catch {
        return 'L';
    }
}
let current = read();
export function setAmountUnit(unit) {
    current = unit;
    try {
        localStorage.setItem(KEY, unit);
    }
    catch {
        /* private window: still works for this visit */
    }
    listeners.forEach((l) => l());
}
export function useAmountUnit() {
    return useSyncExternalStore((l) => {
        listeners.add(l);
        return () => listeners.delete(l);
    }, () => current);
}
/** A figure in the chosen unit; hover shows the exact rupees. */
export function Amount({ value }) {
    const unit = useAmountUnit();
    if (value == null)
        return null;
    return unit === 'full' ? _jsx(_Fragment, { children: formatMoney(value) }) : _jsx("span", { title: `₹ ${formatMoney(value)}`, children: formatAmount(value, unit) });
}
/** For text built from strings (e.g. "net 12.35 L"). */
export function useMoney() {
    const unit = useAmountUnit();
    return (n) => formatAmount(n, unit);
}
const OPTIONS = [
    { unit: 'full', label: '₹', hint: 'Exact rupees' },
    { unit: 'K', label: 'K', hint: 'Thousands' },
    { unit: 'L', label: 'Lakhs', hint: 'Lakhs' },
];
export function AmountSwitch() {
    const unit = useAmountUnit();
    return (_jsxs("div", { className: "amount-switch", role: "group", "aria-label": "Show amounts in", children: [_jsx("span", { children: "Amounts" }), OPTIONS.map((o) => (_jsx("button", { type: "button", title: o.hint, "aria-pressed": unit === o.unit, className: unit === o.unit ? 'on' : undefined, onClick: () => setAmountUnit(o.unit), children: o.label }, o.unit)))] }));
}
