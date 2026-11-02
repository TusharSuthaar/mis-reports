import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from 'react';
import { useSubmit } from '../api/useSubmit';
/** One-field form used for create and rename. */
export function NameForm({ label, initial = '', submitLabel, onSubmit, onDone, }) {
    const [name, setName] = useState(initial);
    const { busy, error, run } = useSubmit();
    async function submit(e) {
        e.preventDefault();
        if (await run(() => onSubmit(name)))
            onDone();
    }
    return (_jsxs("form", { onSubmit: submit, children: [_jsxs("label", { children: [label, _jsx("input", { value: name, onChange: (e) => setName(e.target.value), required: true })] }), error && _jsx("p", { className: "error", children: error }), _jsx("button", { type: "submit", disabled: busy || name.trim() === '' || name === initial, children: submitLabel })] }));
}
