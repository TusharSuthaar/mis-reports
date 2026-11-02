import { jsx as _jsx } from "react/jsx-runtime";
import { Modal } from './Modal';
/**
 * Create/edit forms for masters (org, partners, platforms…). Since the 2026-10 redesign every add/edit opens as the same
 * centred pop-up (narrower than the product forms); × or Escape closes it.
 */
export function SidePanel({ title, onClose, children }) {
    return (_jsx(Modal, { title: title, onClose: onClose, size: "narrow", children: children }));
}
