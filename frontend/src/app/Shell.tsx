import { Fragment } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import type { Role } from '../api/types';
import { MASTER_READ, MASTER_WRITE } from './permissions';
import { useAuth } from './auth';

interface NavItem {
  to: string;
  label: string;
  roles?: Role[];
  group?: string;
}

/** Only screens that exist are listed; later phases add theirs here. */
export const NAV: NavItem[] = [
  { to: '/', label: 'Home' },
  { to: '/month', label: 'This month', roles: MASTER_READ },
  { to: '/clients', label: 'Clients', roles: MASTER_READ },
  { to: '/setup/org', label: 'Org', roles: MASTER_READ, group: 'Setup' },
  { to: '/setup/partners', label: 'Partners', roles: MASTER_READ, group: 'Setup' },
  { to: '/setup/load-clients', label: 'Load clients', roles: MASTER_WRITE, group: 'Setup' },
  { to: '/setup/load-shares', label: 'Upload Master MIS', roles: MASTER_WRITE, group: 'Setup' },
  { to: '/setup/import', label: 'Import', roles: MASTER_READ, group: 'Setup' },
  { to: '/users', label: 'Users', roles: ['ADMIN'], group: 'Admin' },
  { to: '/audit', label: 'Audit log', roles: ['ADMIN'], group: 'Admin' },
];

export function Shell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  if (!user) return null;

  const items = NAV.filter((i) => !i.roles || i.roles.includes(user.role));

  async function onLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">Epsilon MIS</div>
        <nav>
          {items.map((i, idx) => (
            <Fragment key={i.to}>
              {i.group && i.group !== items[idx - 1]?.group && <div className="nav-group">{i.group}</div>}
              <NavLink to={i.to} end={i.to === '/'}>
                {i.label}
              </NavLink>
            </Fragment>
          ))}
        </nav>
      </aside>
      <div className="main">
        <header className="topbar">
          <span>
            {user.displayName} <span className="badge">{user.role}</span>
          </span>
          <button type="button" className="link" onClick={onLogout}>
            Sign out
          </button>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
