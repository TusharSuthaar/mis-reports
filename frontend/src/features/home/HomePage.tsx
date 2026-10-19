import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import type { AifEntry, BondEntry, FdEntry } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useAuth } from '../../app/auth';
import { MASTER_READ } from '../../app/permissions';
import { Icon } from '../../components/Icon';
import { formatMoney } from '../../format';
import { Amount } from '../../components/Amount';

interface MonthRow {
  periodId: number;
  month: string;
  status: 'OPEN' | 'PROCESSED' | 'LOCKED';
  saved: { lines: number; net: number } | null;
  notInMaster: number;
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const label = (ym: string) => `${MONTHS[Number(ym.slice(5, 7)) - 1]} ${ym.slice(0, 4)}`;

/** Dashboard: the month at a glance — revenue by product, where the month close stands, and everything waiting on someone. */
export function HomePage() {
  const { user } = useAuth();
  const canSee = !!user && MASTER_READ.includes(user.role);
  const months = useLoad<MonthRow[]>(canSee ? '/api/months' : null);
  const [month, setMonth] = useState('');
  useEffect(() => {
    if (!month && months.data && months.data.length > 0) {
      const withData = months.data.filter((m) => m.saved);
      setMonth((withData[withData.length - 1] ?? months.data[months.data.length - 1]).month);
    }
  }, [months.data, month]);

  if (!canSee) {
    return (
      <section>
        <h1>Welcome, {user?.displayName}</h1>
        <p className="muted">Signed in as {user?.username} with role {user?.role}.</p>
      </section>
    );
  }
  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Dashboard</h1>
          <p className="muted" style={{ margin: 0 }}>
            Welcome, {user?.displayName}
          </p>
        </div>
        {months.data && months.data.length > 0 && (
          <label className="inline">
            Month
            <select value={month} onChange={(e) => setMonth(e.target.value)} aria-label="Dashboard month">
              {[...months.data].reverse().map((m) => (
                <option key={m.month} value={m.month}>
                  {label(m.month)}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>
      {months.data && months.data.length === 0 && <div className="empty">No month uploaded yet. Start on <Link to="/month">Month close</Link>.</div>}
      {month && <MonthGlance month={month} row={months.data?.find((m) => m.month === month)} />}
      <ToDo months={months.data ?? []} />
    </section>
  );
}

function MonthGlance({ month, row }: { month: string; row?: MonthRow }) {
  const range = `?from=${month}&to=${month}`;
  const fd = useLoad<FdEntry[]>(`/api/fd${range}`);
  const bonds = useLoad<BondEntry[]>(`/api/bonds${range}`);
  const aif = useLoad<AifEntry[]>(`/api/aif${range}`);
  const sum = (l?: { gross: number }[] | null) => (l ?? []).reduce((s, e) => s + e.gross, 0);
  const mf = row?.saved?.net ?? 0;
  const cards = [
    { key: 'MF', title: 'Mutual funds', value: mf, hint: row?.saved ? `${row.saved.lines.toLocaleString('en-IN')} clients` : 'not uploaded', to: '/month' },
    { key: 'FD', title: 'FD', value: sum(fd.data), hint: `${fd.data?.length ?? 0} FDs`, to: `/products?tab=FD&from=${month}&to=${month}` },
    { key: 'BOND', title: 'Bonds', value: sum(bonds.data), hint: `${bonds.data?.length ?? 0} deals`, to: `/products?tab=BOND&from=${month}&to=${month}` },
    { key: 'AIF', title: 'AIF', value: sum(aif.data), hint: `${aif.data?.length ?? 0} commitments`, to: `/products?tab=AIF&from=${month}&to=${month}` },
  ];
  const total = cards.reduce((s, c) => s + c.value, 0);
  const status = row?.status;
  const steps = [
    { label: 'Uploaded', done: !!row?.saved },
    { label: 'Processed', done: status === 'PROCESSED' || status === 'LOCKED' },
    { label: 'Locked', done: status === 'LOCKED' },
  ];
  const next = steps.find((s) => !s.done);
  return (
    <>
      <div className="cards">
        <div className="stat-card" style={{ borderColor: 'var(--primary)' }}>
          <span className="label">Gross revenue · {label(month)}</span>
          <span className="value">₹ <Amount value={total} /></span>
          <span className="hint">excl. GST · ₹ {formatMoney(total)}</span>
        </div>
        {cards.map((c) => (
          <Link key={c.key} to={c.to} className="stat-card" style={{ textDecoration: 'none', color: 'inherit' }}>
            <span className="label">{c.title}</span>
            <span className="value">₹ <Amount value={c.value} /></span>
            <span className="hint">{c.hint}</span>
          </Link>
        ))}
      </div>
      <div className="panel-card" style={{ marginBottom: 16 }}>
        <h2>Month close · {label(month)}</h2>
        <div className="stepper">
          {steps.map((s) => (
            <span key={s.label} className={s.done ? 'step done' : s === next ? 'step now' : 'step'}>
              <span className="dot">{s.done ? '✓' : ''}</span>
              {s.label}
            </span>
          ))}
        </div>
        <p className="muted" style={{ margin: '8px 0 0' }}>
          {next ? (
            <>
              Next: <Link to="/month">{next.label === 'Uploaded' ? 'upload the MF file' : next.label === 'Processed' ? 'process the month' : 'Finance locks the month'}</Link>
            </>
          ) : (
            'This month is closed.'
          )}
        </p>
      </div>
    </>
  );
}

/** Everything waiting on someone, each with a link to where it is fixed. */
function ToDo({ months }: { months: MonthRow[] }) {
  const fd = useLoad<{ pendingPayments: number }>('/api/fd/alerts');
  const bonds = useLoad<{ pendingPayments: number }>('/api/bonds/alerts');
  const aif = useLoad<{ pendingPayments: number }>('/api/aif/alerts');
  const att = useLoad<{ sharedPan: number; noRm: number; noPan: number }>('/api/clients/attention');
  const items: { n: number; text: string; to: string }[] = [
    ...months.filter((m) => m.notInMaster > 0).map((m) => ({ n: m.notInMaster, text: `clients in ${label(m.month)} not in Master MIS`, to: '/month' })),
    ...months.filter((m) => m.saved && m.status === 'OPEN').map((m) => ({ n: 1, text: `${label(m.month)} saved but not processed`, to: '/month' })),
    { n: fd.data?.pendingPayments ?? 0, text: 'FD commissions not received', to: '/products?tab=FD&payment=PENDING' },
    { n: bonds.data?.pendingPayments ?? 0, text: 'bond invoices not fully paid', to: '/products?tab=BOND&payment=PENDING' },
    { n: aif.data?.pendingPayments ?? 0, text: 'AIF invoices not fully paid', to: '/products?tab=AIF&payment=PENDING' },
    { n: att.data?.noRm ?? 0, text: 'clients without an RM', to: '/clients?show=NO_RM' },
    { n: att.data?.sharedPan ?? 0, text: 'clients sharing a PAN', to: '/clients?show=SHARED_PAN' },
    { n: att.data?.noPan ?? 0, text: 'clients without a PAN', to: '/clients?show=NO_PAN' },
  ].filter((i) => i.n > 0);
  return (
    <div className="panel-card">
      <h2>To do</h2>
      {items.length === 0 ? (
        <p className="muted">Nothing waiting. 🎉</p>
      ) : (
        <ul className="todo">
          {items.map((i) => (
            <li key={i.text}>
              <span className="what">
                <Icon name="alert" />
                <span>
                  <strong>{i.n.toLocaleString('en-IN')}</strong> {i.text}
                </span>
              </span>
              <Link to={i.to}>Open</Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
