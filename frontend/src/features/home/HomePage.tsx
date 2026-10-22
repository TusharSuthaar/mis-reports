import { ReactNode, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import type { AifEntry, BondEntry, FdEntry, GiftEntry, LoanEntry } from '../../api/types';
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

/** GET /api/months/{id}/readiness: what stands between the month and processing it (R-91). */
interface Readiness {
  status: 'OPEN' | 'PROCESSED' | 'LOCKED';
  mfSaved: boolean;
  mfClients: number;
  equityUploaded: boolean;
  equityClients: number;
  equityNotInClients: number;
  entries: Record<'FD' | 'BOND' | 'AIF' | 'LOAN' | 'GIFT', number>;
  notInMaster: number;
  missingPercent: string[];
  ready: boolean;
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
  const loans = useLoad<LoanEntry[]>(`/api/loans${range}`);
  const gifts = useLoad<GiftEntry[]>(`/api/gift${range}`);
  const equity = useLoad<{ share: number }[]>(`/api/equity${range}`);
  const sum = (l?: { gross: number }[] | null) => (l ?? []).reduce((s, e) => s + e.gross, 0);
  const mf = row?.saved?.net ?? 0;
  const cards = [
    { key: 'MF', title: 'Mutual funds', value: mf, hint: row?.saved ? `${row.saved.lines.toLocaleString('en-IN')} clients` : 'not uploaded', to: '/month' },
    { key: 'FD', title: 'FD', value: sum(fd.data), hint: `${fd.data?.length ?? 0} FDs`, to: `/products?tab=FD&from=${month}&to=${month}` },
    { key: 'BOND', title: 'Bonds', value: sum(bonds.data), hint: `${bonds.data?.length ?? 0} deals`, to: `/products?tab=BOND&from=${month}&to=${month}` },
    { key: 'AIF', title: 'AIF', value: sum(aif.data), hint: `${aif.data?.length ?? 0} commitments`, to: `/products?tab=AIF&from=${month}&to=${month}` },
    { key: 'LOAN', title: 'Loans', value: sum(loans.data), hint: `${loans.data?.length ?? 0} payouts`, to: `/products?tab=LOAN&from=${month}&to=${month}` },
    { key: 'GIFT', title: 'GIFT City', value: sum(gifts.data), hint: `${gifts.data?.length ?? 0} investments`, to: `/products?tab=GIFT&from=${month}&to=${month}` },
    {
      key: 'EQUITY',
      title: 'Equity',
      value: (equity.data ?? []).reduce((s, e) => s + e.share, 0),
      hint: equity.data?.length ? `${equity.data.length} clients` : 'not uploaded',
      to: `/products?tab=EQUITY&from=${month}&to=${month}`,
    },
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
        {row && <PreCheck periodId={row.periodId} />}
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

/**
 * Ready to process? The checks Process stops on, run without changing anything, each with a link to where it is fixed;
 * the END file once the month is processed (R-91).
 */
function PreCheck({ periodId }: { periodId: number }) {
  const check = useLoad<Readiness>(`/api/months/${periodId}/readiness`);
  const r = check.data;
  if (check.error) return <p className="error">{check.error}</p>;
  if (!r) return <p className="muted">Checking the month…</p>;
  const n = (v: number) => v.toLocaleString('en-IN');
  const e = r.entries;
  const items: { ok: boolean; info?: boolean; text: ReactNode; to?: string; link?: string }[] = [
    { ok: r.mfSaved, text: r.mfSaved ? `MF figures saved (${n(r.mfClients)} clients)` : 'MF file not saved yet', to: '/month', link: 'Upload' },
    {
      ok: true,
      info: true,
      text: `Entries: FD ${e.FD} · Bonds ${e.BOND} · AIF ${e.AIF} · Loans ${e.LOAN} · GIFT City ${e.GIFT} · Equity ${r.equityUploaded ? `${n(r.equityClients)} clients` : 'not uploaded'}`,
    },
    {
      ok: r.notInMaster === 0,
      text: r.notInMaster === 0 ? 'Every client has an RM' : `${n(r.notInMaster)} client(s) with business this month have no RM (not in Master MIS)`,
      to: '/clients?show=NO_RM',
      link: 'Fix',
    },
    {
      ok: r.missingPercent.length === 0,
      text:
        r.missingPercent.length === 0 ? (
          'Every partner has its % for the products it has business in'
        ) : (
          <>
            {r.missingPercent.length} partner % missing: <span className="muted">{r.missingPercent.join(', ')}</span>
          </>
        ),
      to: '/setup/partners',
      link: 'Set %',
    },
  ];
  if (r.equityUploaded) {
    items.push({
      ok: r.equityNotInClients === 0,
      text: r.equityNotInClients === 0 ? 'Every equity PAN is a client' : `${n(r.equityNotInClients)} equity PAN(s) not in Clients`,
      to: `/products?tab=EQUITY`,
      link: 'See',
    });
  }
  const processed = r.status !== 'OPEN';
  return (
    <div className="precheck">
      <h3>{processed ? 'Checks' : r.ready ? 'Ready to process' : 'Before you process'}</h3>
      <ul className="todo">
        {items.map((i, k) => (
          <li key={k} className={i.info ? 'info' : i.ok ? 'ok' : 'blocker'}>
            <span className="what">
              <span className="mark" aria-label={i.info ? 'information' : i.ok ? 'done' : 'needs fixing'}>
                {i.info ? 'ℹ' : i.ok ? '✓' : '!'}
              </span>
              <span>{i.text}</span>
            </span>
            {!i.ok && i.to && <Link to={i.to}>{i.link ?? 'Open'}</Link>}
          </li>
        ))}
      </ul>
      <div className="actions" style={{ marginTop: 10 }}>
        {processed ? (
          <a className="button-link" href={`/api/months/${periodId}/end-file`} download>
            Download END file{r.status === 'LOCKED' ? '' : ' (draft)'}
          </a>
        ) : r.ready ? (
          <Link className="button-link" to="/month">
            Go to Month close to process
          </Link>
        ) : null}
        <button type="button" className="link" onClick={check.reload}>
          Check again
        </button>
      </div>
    </div>
  );
}

/** Everything waiting on someone, each with a link to where it is fixed. */
function ToDo({ months }: { months: MonthRow[] }) {
  const fd = useLoad<{ pendingPayments: number }>('/api/fd/alerts');
  const bonds = useLoad<{ pendingPayments: number }>('/api/bonds/alerts');
  const aif = useLoad<{ pendingPayments: number }>('/api/aif/alerts');
  const loans = useLoad<{ pendingPayments: number }>('/api/loans/alerts');
  const att = useLoad<{ sharedPan: number; noRm: number; noPan: number }>('/api/clients/attention');
  const items: { n: number; text: string; to: string }[] = [
    ...months.filter((m) => m.notInMaster > 0).map((m) => ({ n: m.notInMaster, text: `clients in ${label(m.month)} not in Master MIS`, to: '/month' })),
    ...months.filter((m) => m.saved && m.status === 'OPEN').map((m) => ({ n: 1, text: `${label(m.month)} saved but not processed`, to: '/month' })),
    { n: fd.data?.pendingPayments ?? 0, text: 'FD commissions not received', to: '/products?tab=FD&payment=PENDING' },
    { n: bonds.data?.pendingPayments ?? 0, text: 'bond invoices not fully paid', to: '/products?tab=BOND&payment=PENDING' },
    { n: aif.data?.pendingPayments ?? 0, text: 'AIF invoices not fully paid', to: '/products?tab=AIF&payment=PENDING' },
    { n: loans.data?.pendingPayments ?? 0, text: 'loan payouts not received', to: '/products?tab=LOAN&payment=PENDING' },
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
