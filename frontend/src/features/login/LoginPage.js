import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { useAuth } from '../../app/auth';
export function LoginPage() {
    const { user, loading, expired, login } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState(null);
    const [busy, setBusy] = useState(false);
    const from = safeReturnPath(location.state?.from);
    if (!loading && user)
        return _jsx(Navigate, { to: from, replace: true });
    async function onSubmit(e) {
        e.preventDefault();
        setBusy(true);
        setError(null);
        try {
            await login(username, password);
            navigate(from, { replace: true });
        }
        catch (err) {
            // 429: too many wrong passwords (wait, or locked until an administrator unlocks): the server says which (R-101).
            setError(err instanceof ApiError && err.status === 401 ? 'Invalid username or password'
                : err instanceof ApiError && err.status === 429 ? err.message : 'Sign-in failed. Try again.');
        }
        finally {
            setBusy(false);
        }
    }
    return (_jsx("div", { className: "login", children: _jsxs("form", { className: "card", onSubmit: onSubmit, children: [_jsx("h1", { children: "Epsilon MIS" }), expired && (_jsx("p", { className: "muted", role: "status", children: "You were signed out. Please sign in again." })), _jsxs("label", { children: ["Username", _jsx("input", { value: username, onChange: (e) => setUsername(e.target.value), autoComplete: "username", autoFocus: true, required: true })] }), _jsxs("label", { children: ["Password", _jsx("input", { type: "password", value: password, onChange: (e) => setPassword(e.target.value), autoComplete: "current-password", required: true })] }), error && (_jsx("p", { className: "error", role: "alert", children: error })), _jsx("button", { type: "submit", disabled: busy, children: busy ? 'Signing in…' : 'Sign in' })] }) }));
}
/**
 * Where to go after signing in: only a path of this app. "//host", "/\host" and full URLs would leave the site
 * (an open redirect), so they go to the dashboard instead.
 */
export function safeReturnPath(from) {
    if (!from || !from.startsWith('/') || from.startsWith('//') || from.includes('\\') || /[\u0000-\u001f]/.test(from))
        return '/';
    return from;
}
