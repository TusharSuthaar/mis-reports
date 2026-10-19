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
export function Moved({ to, tab }: { to: string; tab?: string }) {
  const { search } = useLocation();
  const p = new URLSearchParams(search);
  if (tab) p.set('tab', tab);
  const q = p.toString();
  return <Navigate to={q ? `${to}?${q}` : to} replace />;
}

/** Masters → Platforms & taxes: two tabs. */
export function PlatformsAndTaxesPage() {
  const tabs = [
    { key: 'platforms', label: 'Platforms' },
    { key: 'taxes', label: 'Products & taxes' },
  ];
  const [tab, setTab] = useTab(tabs);
  return (
    <section>
      <Tabs tabs={tabs} value={tab} onChange={setTab} />
      {tab === 'platforms' ? <PlatformsPage /> : <ProductTaxesPage />}
    </section>
  );
}

/** Admin → Users, Audit log (ADMIN), Activity. */
export function AdminPage() {
  const { user } = useAuth();
  const admin = user?.role === 'ADMIN';
  const tabs = [...(admin ? [{ key: 'users', label: 'Users' }, { key: 'audit', label: 'Audit log' }] : []), { key: 'activity', label: 'Activity' }];
  const [tab, setTab] = useTab(tabs);
  return (
    <section>
      <Tabs tabs={tabs} value={tab} onChange={setTab} />
      {tab === 'users' && <UsersPage />}
      {tab === 'audit' && <AuditPage />}
      {tab === 'activity' && <ActivityPage />}
    </section>
  );
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
  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Data uploads</h1>
          <p className="muted" style={{ margin: 0 }}>
            The files that feed the system. Each upload shows a preview before anything is saved.
          </p>
        </div>
      </div>
      <div className="grid-2">
        {UPLOADS.filter((u) => canWrite || !u.write).map((u) => (
          <Link key={u.to} to={u.to} className="panel-card" style={{ textDecoration: 'none', color: 'inherit' }}>
            <h2 style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Icon name="uploads" /> {u.title}
            </h2>
            <p className="muted" style={{ margin: 0 }}>
              {u.text}
            </p>
          </Link>
        ))}
      </div>
    </section>
  );
}
