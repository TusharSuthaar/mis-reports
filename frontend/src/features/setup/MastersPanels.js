import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { ExchangeRatesPage } from './ExchangeRatesPage';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../app/auth';
import { MASTER_WRITE } from '../../app/permissions';
import { Icon } from '../../components/Icon';
import { Tabs, useTab } from '../../components/Tabs';
import { ActivityPage } from '../activity/ActivityPage';
import { AuditPage } from '../audit/AuditPage';
import { UsersPage } from '../users/UsersPage';
import { PlatformsPage } from './PlatformsPage';
import { ProductTaxesPage } from './ProductTaxesPage';
/** An old address goes to its new panel (and tab), keeping its filters. */
export function Moved({ to, tab }) {
    const { search } = useLocation();
    const p = new URLSearchParams(search);
    if (tab)
        p.set('tab', tab);
    const q = p.toString();
    return _jsx(Navigate, { to: q ? `${to}?${q}` : to, replace: true });
}
/** Masters → Platforms & taxes: two tabs. */
export function PlatformsAndTaxesPage() {
    const tabs = [
        { key: 'platforms', label: 'Platforms' },
        { key: 'taxes', label: 'Products & taxes' },
        { key: 'fx', label: 'Exchange rates' },
    ];
    const [tab, setTab] = useTab(tabs);
    return (_jsxs("section", { children: [_jsx(Tabs, { tabs: tabs, value: tab, onChange: setTab }), tab === 'platforms' && _jsx(PlatformsPage, {}), tab === 'taxes' && _jsx(ProductTaxesPage, {}), tab === 'fx' && _jsx(ExchangeRatesPage, {})] }));
}
/** Admin → Users, Audit log (ADMIN), Activity. */
export function AdminPage() {
    const { user } = useAuth();
    const admin = user?.role === 'ADMIN';
    const tabs = [...(admin ? [{ key: 'users', label: 'Users' }, { key: 'audit', label: 'Audit log' }] : []), { key: 'activity', label: 'Activity' }];
    const [tab, setTab] = useTab(tabs);
    return (_jsxs("section", { children: [_jsx(Tabs, { tabs: tabs, value: tab, onChange: setTab }), tab === 'users' && _jsx(UsersPage, {}), tab === 'audit' && _jsx(AuditPage, {}), tab === 'activity' && _jsx(ActivityPage, {})] }));
}
const UPLOADS = [
    {
        to: '/setup/load-shares',
        title: 'Master MIS',
        text: 'Client → RM, sub-broker, referral partner and %, plus the partner rate cards. Upload when it changes.',
        write: true,
    },
    {
        to: '/month',
        title: 'Finesse client master & MF brokerage',
        text: 'Client codes and PANs, and each month’s MF brokerage file — uploaded on Month close.',
        write: false,
    },
    {
        to: '/products?tab=EQUITY',
        title: 'Equity brokerage (SMC)',
        text: 'The broker’s monthly Brokerage Due report (“Equity Source”). Each client’s franchise share is the month’s Equity revenue.',
        write: true,
    },
    {
        to: '/setup/import',
        title: 'Org & partner masters (Excel)',
        text: 'Regions, branches, RMs and partners from the import workbook. Dry run first.',
        write: false,
    },
    {
        to: '/setup/load-clients',
        title: 'Investwell MF file (old)',
        text: 'Only for the old Investwell income file. Finesse replaced it.',
        write: true,
    },
];
/** Masters → Data uploads: every file that feeds the system, in one place. */
export function UploadsPage() {
    const { user } = useAuth();
    const canWrite = !!user && MASTER_WRITE.includes(user.role);
    return (_jsxs("section", { children: [_jsx("div", { className: "page-head", children: _jsxs("div", { children: [_jsx("h1", { children: "Data uploads" }), _jsx("p", { className: "muted", style: { margin: 0 }, children: "The files that feed the system. Each upload shows a preview before anything is saved." })] }) }), _jsx("div", { className: "grid-2", children: UPLOADS.filter((u) => canWrite || !u.write).map((u) => (_jsxs(Link, { to: u.to, className: "panel-card", style: { textDecoration: 'none', color: 'inherit' }, children: [_jsxs("h2", { style: { display: 'flex', alignItems: 'center', gap: 8 }, children: [_jsx(Icon, { name: "uploads" }), " ", u.title] }), _jsx("p", { className: "muted", style: { margin: 0 }, children: u.text })] }, u.to))) })] }));
}
