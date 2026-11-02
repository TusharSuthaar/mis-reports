import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { Fragment } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { AmountSwitch } from '../components/Amount';
import { Icon } from '../components/Icon';
import { MASTER_READ } from './permissions';
import { useAuth } from './auth';
/** The menu: four workspaces, each with its own panels. */
export const NAV = [
    { to: '/', label: 'Dashboard', icon: 'dashboard' },
    { to: '/month', label: 'Month close', icon: 'month', roles: MASTER_READ, group: 'Monthly MIS' },
    { to: '/products', label: 'Product entries', icon: 'products', roles: MASTER_READ, group: 'Business', also: ['/fd', '/bonds', '/aif'] },
    { to: '/clients', label: 'Clients', icon: 'clients', roles: MASTER_READ, group: 'Business' },
    { to: '/setup/org', label: 'Organisation', icon: 'org', roles: MASTER_READ, group: 'Masters' },
    { to: '/setup/partners', label: 'Partners', icon: 'partners', roles: MASTER_READ, group: 'Masters' },
    { to: '/setup/platforms', label: 'Platforms & taxes', icon: 'platforms', roles: MASTER_READ, group: 'Masters', also: ['/setup/product-taxes'] },
    { to: '/setup/uploads', label: 'Data uploads', icon: 'uploads', roles: MASTER_READ, group: 'Masters', also: ['/setup/load-shares', '/setup/load-clients', '/setup/import'] },
    { to: '/admin', label: 'Users & audit', icon: 'admin', roles: ['ADMIN'], group: 'Admin', also: ['/users', '/audit', '/activity'] },
    { to: '/admin', label: 'Activity', icon: 'admin', roles: ['HO', 'FINANCE'], group: 'Admin', also: ['/activity'] },
];
export function Shell() {
    const { user, logout } = useAuth();
    const navigate = useNavigate();
    const { pathname } = useLocation();
    if (!user)
        return null;
    const items = NAV.filter((i) => !i.roles || i.roles.includes(user.role));
    const isCurrent = (i) => (i.to === '/' ? pathname === '/' : pathname.startsWith(i.to) || (i.also ?? []).some((a) => pathname.startsWith(a)));
    async function onLogout() {
        await logout();
        navigate('/login', { replace: true });
    }
    return (_jsxs("div", { className: "shell", children: [_jsxs("aside", { className: "sidebar", children: [_jsxs("div", { className: "brand", children: [_jsx("span", { className: "brand-mark", children: "E" }), _jsxs("span", { children: ["Epsilon ", _jsx("strong", { children: "MIS" })] })] }), _jsx("nav", { children: items.map((i, idx) => (_jsxs(Fragment, { children: [i.group && i.group !== items[idx - 1]?.group && _jsx("div", { className: "nav-group", children: i.group }), _jsxs(NavLink, { to: i.to, end: i.to === '/', className: () => (isCurrent(i) ? 'active' : undefined), children: [_jsx(Icon, { name: i.icon }), _jsx("span", { children: i.label })] })] }, i.to))) }), _jsx(AmountSwitch, {}), _jsxs("div", { className: "sidebar-foot", children: [_jsxs("div", { className: "user-chip", children: [_jsx("span", { className: "avatar", children: user.displayName.slice(0, 1).toUpperCase() }), _jsxs("span", { children: [user.displayName, _jsx("small", { children: user.role })] })] }), _jsx("button", { type: "button", className: "link", onClick: onLogout, "aria-label": "Sign out", title: "Sign out", children: _jsx(Icon, { name: "logout" }) })] })] }), _jsx("div", { className: "main", children: _jsx("main", { className: "content", children: _jsx(Outlet, {}) }) })] }));
}
