import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect } from 'react';
/**
 * Centred pop-up for long entry forms. Closes with × or Escape only — not by clicking outside — so a half-filled
 * form is not lost by a stray click.
 */
export function Modal({ title, onClose, children, size = 'wide' }) {
    useEffect(() => {
        const onKey = (e) => e.key === 'Escape' && onClose();
        window.addEventListener('keydown', onKey);
        const overflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        return () => {
            window.removeEventListener('keydown', onKey);
            document.body.style.overflow = overflow;
        };
    }, [onClose]);
    return (_jsx("div", { className: "modal-backdrop", children: _jsxs("div", { className: size === 'narrow' ? 'modal narrow' : 'modal', role: "dialog", "aria-modal": "true", "aria-label": title, children: [_jsxs("header", { children: [_jsx("h2", { children: title }), _jsx("button", { type: "button", className: "link", "aria-label": "Close", onClick: onClose, children: "\u00D7" })] }), _jsx("div", { className: "modal-body", children: children })] }) }));
}
