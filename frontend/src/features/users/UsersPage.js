import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useCallback, useEffect, useState } from 'react';
import { api, errorText } from '../../api/client';
import { useSubmit } from '../../api/useSubmit';
import { ROLES } from '../../api/types';
import { useAuth } from '../../app/auth';
import { SidePanel } from '../../components/SidePanel';
import { formatDateTime } from '../../format';
export function UsersPage() {
    const [users, setUsers] = useState(null);
    const [loadError, setLoadError] = useState(null);
    const [panel, setPanel] = useState(null);
    const load = useCallback(() => {
        api('/api/users').then(setUsers).catch((e) => setLoadError(errorText(e)));
    }, []);
    useEffect(load, [load]);
    const close = useCallback(() => setPanel(null), []);
    const saved = useCallback(() => {
        setPanel(null);
        load();
    }, [load]);
    return (_jsxs("section", { children: [_jsxs("div", { className: "page-head", children: [_jsx("h1", { children: "Users" }), _jsx("button", { type: "button", onClick: () => setPanel({ mode: 'new' }), children: "New user" })] }), loadError && _jsx("p", { className: "error", children: loadError }), users && (_jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Username" }), _jsx("th", { children: "Name" }), _jsx("th", { children: "Role" }), _jsx("th", { children: "Status" }), _jsx("th", { children: "Last changed" })] }) }), _jsx("tbody", { children: users.map((u) => (_jsxs("tr", { className: "clickable", onClick: () => setPanel({ mode: 'edit', user: u }), children: [_jsx("td", { children: u.username }), _jsx("td", { children: u.displayName }), _jsx("td", { children: u.role }), _jsxs("td", { children: [u.active ? 'Active' : _jsx("span", { className: "muted", children: "Inactive" }), u.locked && _jsx("span", { className: "badge warn", children: "locked: wrong passwords" }), !u.locked && u.waitUntil && _jsx("span", { className: "badge", children: "waiting after wrong passwords" })] }), _jsx("td", { children: formatDateTime(u.updatedAt) })] }, u.id))) })] })), panel?.mode === 'new' && (_jsx(SidePanel, { title: "New user", onClose: close, children: _jsx(NewUserForm, { onSaved: saved }) })), panel?.mode === 'edit' && (_jsxs(SidePanel, { title: panel.user.username, onClose: close, children: [(panel.user.locked || panel.user.waitUntil) && _jsx(UnlockUser, { user: panel.user, onSaved: saved }), _jsx(EditUserForm, { user: panel.user, onSaved: saved }), _jsx(ResetPasswordForm, { user: panel.user })] }))] }));
}
function NewUserForm({ onSaved }) {
    const [username, setUsername] = useState('');
    const [displayName, setDisplayName] = useState('');
    const [role, setRole] = useState('HO');
    const [password, setPassword] = useState('');
    const [error, setError] = useState(null);
    async function onSubmit(e) {
        e.preventDefault();
        setError(null);
        try {
            await api('/api/users', { method: 'POST', body: { username, displayName, role, password } });
            onSaved();
        }
        catch (err) {
            setError(errorText(err));
        }
    }
    return (_jsxs("form", { onSubmit: onSubmit, children: [_jsxs("label", { children: ["Username", _jsx("input", { value: username, onChange: (e) => setUsername(e.target.value), required: true })] }), _jsxs("label", { children: ["Name", _jsx("input", { value: displayName, onChange: (e) => setDisplayName(e.target.value), required: true })] }), _jsxs("label", { children: ["Role", _jsx("select", { value: role, onChange: (e) => setRole(e.target.value), children: ROLES.map((r) => (_jsx("option", { value: r, children: NO_SCREENS_YET.includes(r) ? `${r} (no screens yet)` : r }, r))) })] }), _jsxs("label", { children: ["Initial password", _jsx("input", { type: "password", value: password, onChange: (e) => setPassword(e.target.value), autoComplete: "new-password", required: true }), _jsx("small", { className: "muted", children: "At least 10 characters, must not contain the username." })] }), error && _jsx("p", { className: "error", children: error }), _jsx("button", { type: "submit", children: "Create user" })] }));
}
function EditUserForm({ user, onSaved }) {
    const { user: me } = useAuth();
    const isSelf = me?.id === user.id;
    const [displayName, setDisplayName] = useState(user.displayName);
    const [role, setRole] = useState(user.role);
    const [active, setActive] = useState(user.active);
    const [error, setError] = useState(null);
    async function onSubmit(e) {
        e.preventDefault();
        setError(null);
        try {
            await api(`/api/users/${user.id}`, { method: 'PATCH', body: { displayName, role, active } });
            onSaved();
        }
        catch (err) {
            setError(errorText(err));
        }
    }
    return (_jsxs("form", { onSubmit: onSubmit, children: [_jsxs("label", { children: ["Name", _jsx("input", { value: displayName, onChange: (e) => setDisplayName(e.target.value), required: true })] }), _jsxs("label", { children: ["Role", _jsx("select", { value: role, onChange: (e) => setRole(e.target.value), disabled: isSelf, children: ROLES.map((r) => (_jsx("option", { value: r, children: NO_SCREENS_YET.includes(r) ? `${r} (no screens yet)` : r }, r))) })] }), _jsxs("label", { className: "check", children: [_jsx("input", { type: "checkbox", checked: active, onChange: (e) => setActive(e.target.checked), disabled: isSelf }), "Active (can sign in)"] }), isSelf && _jsx("small", { className: "muted", children: "You cannot change your own role or deactivate yourself." }), error && _jsx("p", { className: "error", children: error }), _jsx("button", { type: "submit", children: "Save" })] }));
}
function ResetPasswordForm({ user }) {
    const [password, setPassword] = useState('');
    const [message, setMessage] = useState(null);
    async function onSubmit(e) {
        e.preventDefault();
        try {
            await api(`/api/users/${user.id}/password`, { method: 'POST', body: { password } });
            setPassword('');
            setMessage({ ok: true, text: 'Password changed.' });
        }
        catch (err) {
            setMessage({ ok: false, text: errorText(err) });
        }
    }
    return (_jsxs("form", { onSubmit: onSubmit, className: "separated", children: [_jsx("h3", { children: "Reset password" }), _jsxs("label", { children: ["New password", _jsx("input", { type: "password", value: password, onChange: (e) => setPassword(e.target.value), autoComplete: "new-password", required: true })] }), message && _jsx("p", { className: message.ok ? 'ok' : 'error', children: message.text }), _jsx("button", { type: "submit", className: "secondary", children: "Set password" })] }));
}
/** Roles that can sign in but have no screens until the RM / RD view exists (audit L-03). */
const NO_SCREENS_YET = ['RM', 'RD'];
/** A user stopped by wrong passwords (R-101): unlocking lets them sign in again at once. */
function UnlockUser({ user, onSaved }) {
    const { busy, error, run } = useSubmit();
    return (_jsxs("div", { className: "card-block", children: [_jsxs("p", { className: "warn-note", style: { marginTop: 0 }, children: [user.locked
                        ? `${user.username} is locked after 10 wrong passwords.`
                        : `${user.username} must wait after wrong passwords (until ${formatDateTime(user.waitUntil)}).`, ' ', "Check with them that it was them before unlocking."] }), _jsx("button", { type: "button", disabled: busy, onClick: async () => { if (await run(() => api(`/api/users/${user.id}/unlock`, { method: 'POST' })))
                    onSaved(); }, children: "Unlock" }), error && _jsx("p", { className: "error", children: error })] }));
}
