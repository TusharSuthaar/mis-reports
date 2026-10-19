import { Fragment } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import type { Role } from '../api/types';
import { AmountSwitch } from '../components/Amount';
import { Icon } from '../components/Icon';
import { MASTER_READ } from './permissions';
import { useAuth } from './auth';

interface NavItem {
  to: string;
  label: string;
  icon: string;
  roles?: Role[];
  group?: string;
  /** Other addresses that belong to this item (old links, sub-pages), so it shows as the current one. */
  also?: string[];
}

/** The menu: four workspaces, each with its own panels. */
export const NAV: NavItem[] = [
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
  if (!user) return null;

  const items = NAV.filter((i) => !i.roles || i.roles.includes(user.role));
  const isCurrent = (i: NavItem) => (i.to === '/' ? pathname === '/' : pathname.startsWith(i.to) || (i.also ?? []).some((a) => pathname.startsWith(a)));

  async function onLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">E</span>
          <span>
            Epsilon <strong>MIS</strong>
          </span>
        </div>
        <nav>
          {items.map((i, idx) => (
            <Fragment key={i.to}>
              {i.group && i.group !== items[idx - 1]?.group && <div className="nav-group">{i.group}</div>}
              <NavLink to={i.to} end={i.to === '/'} className={() => (isCurrent(i) ? 'active' : undefined)}>
                <Icon name={i.icon} />
                <span>{i.label}</span>
              </NavLink>
            </Fragment>
          ))}
        </nav>
        <AmountSwitch />
        <div className="sidebar-foot">
          <div className="user-chip">
            <span className="avatar">{user.displayName.slice(0, 1).toUpperCase()}</span>
            <span>
              {user.displayName}
              <small>{user.role}</small>
            </span>
          </div>
          <button type="button" className="link" onClick={onLogout} aria-label="Sign out" title="Sign out">
            <Icon name="logout" />
          </button>
        </div>
      </aside>
      <div className="main">
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
