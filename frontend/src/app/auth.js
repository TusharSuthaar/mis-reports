import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { api, ApiError, onSessionExpired } from '../api/client';
const AuthContext = createContext(null);
export function AuthProvider({ children }) {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);
    const [waiting, setWaiting] = useState(false);
    const [expired, setExpired] = useState(false);
    useEffect(() => {
        onSessionExpired(() => {
            setUser(null);
            setExpired(true);
        });
    }, []);
    useEffect(() => {
        // Only "not signed in" (401) means the sign-in page. While the server cannot be reached (restarting after an
        // update) the app waits and checks again, so a valid session is never sent to the sign-in page.
        let cancelled = false;
        const check = (attempt) => {
            api('/api/auth/me')
                .then((me) => {
                if (cancelled)
                    return;
                setUser(me);
                setWaiting(false);
                setLoading(false);
            })
                .catch((e) => {
                if (cancelled)
                    return;
                if (e instanceof ApiError && e.status === 401) {
                    setUser(null);
                    setWaiting(false);
                    setLoading(false);
                    return;
                }
                setWaiting(true);
                window.setTimeout(() => check(attempt + 1), Math.min(5000, 1000 * (attempt + 1)));
            });
        };
        check(0);
        return () => {
            cancelled = true;
        };
    }, []);
    const login = useCallback(async (username, password) => {
        setUser(await api('/api/auth/login', { method: 'POST', body: { username, password } }));
        setExpired(false);
    }, []);
    const logout = useCallback(async () => {
        await api('/api/auth/logout', { method: 'POST' });
        setUser(null);
    }, []);
    return _jsx(AuthContext.Provider, { value: { user, loading, waiting, expired, login, logout }, children: children });
}
export function useAuth() {
    const ctx = useContext(AuthContext);
    if (!ctx)
        throw new Error('useAuth must be used inside AuthProvider');
    return ctx;
}
/** Sends signed-out users to /login, and shows "not allowed" when the role is wrong. */
export function RequireAuth({ roles, children }) {
    const { user, loading, waiting } = useAuth();
    const location = useLocation();
    if (loading)
        return _jsx("p", { className: "muted", children: waiting ? 'Waiting for the server… (it may be restarting after an update; this page will continue on its own)' : 'Loading…' });
    if (!user)
        return _jsx(Navigate, { to: "/login", replace: true, state: { from: location.pathname } });
    if (roles && !roles.includes(user.role)) {
        return (_jsxs("section", { children: [_jsx("h1", { children: "Not allowed" }), _jsxs("p", { children: ["Your role (", user.role, ") cannot open this page."] })] }));
    }
    return _jsx(_Fragment, { children: children });
}
