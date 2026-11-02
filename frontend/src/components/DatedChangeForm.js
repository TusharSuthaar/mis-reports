import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from 'react';
import { useSubmit } from '../api/useSubmit';
import { todayIso } from '../format';
/**
 * A small form for one dated change: "what" (children) and "from when" (the date).
 * On success the form resets and {@code onDone} runs.
 */
export function DatedChangeForm({ title, dateLabel, submitLabel, children, canSubmit = true, onSubmit, onDone, }) {
    const [date, setDate] = useState(todayIso());
    const { busy, error, run } = useSubmit();
    async function submit(e) {
        e.preventDefault();
        if (await run(() => onSubmit(date)))
            onDone();
    }
    return (_jsxs("form", { onSubmit: submit, className: "separated", children: [_jsx("h3", { children: title }), children, _jsxs("label", { children: [dateLabel, _jsx("input", { type: "date", value: date, onChange: (e) => setDate(e.target.value), required: true })] }), error && _jsx("p", { className: "error", children: error }), _jsx("button", { type: "submit", className: "secondary", disabled: busy || !canSubmit, children: submitLabel })] }));
}
