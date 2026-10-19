import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PAYMENT_LABEL, type AifEntry, type BondEntry, type FdEntry, type FdPaymentStatus } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { Icon } from '../../components/Icon';
import { ListFilters, useFilterQuery } from '../../components/ListFilters';
import { Tabs, useTab } from '../../components/Tabs';
import { Amount } from '../../components/Amount';
import { AifPage } from '../aif/AifPage';
import { BondsPage } from '../bonds/BondsPage';
import { FdPage, formatMonth } from '../fd/FdPage';

const TABS = [
  { key: 'ALL', label: 'All products' },
  { key: 'FD', label: 'FD' },
  { key: 'BOND', label: 'Bonds' },
  { key: 'AIF', label: 'AIF' },
];
const PRODUCT_NAME: Record<string, string> = { FD: 'FD', BOND: 'Bond', AIF: 'AIF' };

/** Product entries: every product booked by hand on one page — all together, or one product with its own columns. */
export function ProductsPage() {
  const [tab] = useTab(TABS);
  const [params, setParams] = useSearchParams();
  // Switching tab keeps the common filters but drops the product-only ones and any open pop-up.
  const switchTab = (key: string) => {
    const next = new URLSearchParams(params);
    ['platform', 'platformId', 'new', 'open'].forEach((k) => next.delete(k));
    next.set('tab', key);
    setParams(next, { replace: true });
  };
  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Product entries</h1>
          <p className="muted" style={{ margin: 0 }}>
            FD, bonds and AIF booked by hand. Filters apply across months.
          </p>
        </div>
        <NewEntryMenu onPick={(p) => {
          const next = new URLSearchParams(params);
          ['platform', 'platformId', 'open'].forEach((k) => next.delete(k));
          next.set('tab', p);
          next.set('new', '1');
          setParams(next, { replace: true });
        }} />
      </div>
      <Tabs tabs={TABS} value={tab} onChange={switchTab} />
      {tab === 'ALL' && <AllProducts onOpen={(product, id) => {
        const next = new URLSearchParams(params);
        next.set('tab', product);
        next.set('open', String(id));
        setParams(next, { replace: true });
      }} />}
      {tab === 'FD' && <FdPage embedded />}
      {tab === 'BOND' && <BondsPage embedded />}
      {tab === 'AIF' && <AifPage embedded />}
    </section>
  );
}

function NewEntryMenu({ onPick }: { onPick: (product: string) => void }) {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const close = (e: MouseEvent) => ref.current && !ref.current.contains(e.target as Node) && setOpen(false);
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);
  if (!canEditMasters(user?.role)) return null;
  return (
    <div className="menu-wrap" ref={ref}>
      <button type="button" onClick={() => setOpen(!open)} aria-haspopup="menu" aria-expanded={open}>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <Icon name="plus" size={16} /> New entry ▾
        </span>
      </button>
      {open && (
        <div className="menu" role="menu">
          {(['FD', 'BOND', 'AIF'] as const).map((p) => (
            <button key={p} type="button" role="menuitem" onClick={() => { setOpen(false); onPick(p); }}>
              New {PRODUCT_NAME[p]}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

interface Row {
  product: 'FD' | 'BOND' | 'AIF';
  id: number;
  month: string;
  client: string;
  item: string;
  rm: string;
  amount: number;
  gross: number;
  invoice: number;
  payment: FdPaymentStatus;
  cancelled: boolean;
}

/** All products in one list (newest first), with the same filters; a row opens the entry in its product tab. */
function AllProducts({ onOpen }: { onOpen: (product: string, id: number) => void }) {
  const query = useFilterQuery();
  const fd = useLoad<FdEntry[]>(`/api/fd${query}`);
  const bonds = useLoad<BondEntry[]>(`/api/bonds${query}`);
  const aif = useLoad<AifEntry[]>(`/api/aif${query}`);
  const rows: Row[] = [
    ...(fd.data ?? []).map((e): Row => ({ product: 'FD', id: e.id, month: e.month, client: e.clientName, item: `${e.fdName}${e.fdNo ? ` #${e.fdNo}` : ''}`, rm: e.rmName, amount: e.amount, gross: e.gross, invoice: e.invoice, payment: e.paymentStatus, cancelled: e.status === 'CANCELLED' })),
    ...(bonds.data ?? []).map((e): Row => ({ product: 'BOND', id: e.id, month: e.month, client: e.clientName, item: e.bondName, rm: e.rmName, amount: e.consideration, gross: e.gross, invoice: e.invoice, payment: e.paymentStatus, cancelled: e.status === 'CANCELLED' })),
    ...(aif.data ?? []).map((e): Row => ({ product: 'AIF', id: e.id, month: e.month, client: e.clientName, item: e.fundName, rm: e.rmName, amount: e.commitment, gross: e.gross, invoice: e.invoice, payment: e.paymentStatus, cancelled: e.status === 'CANCELLED' })),
  ].sort((a, b) => b.month.localeCompare(a.month) || a.client.localeCompare(b.client));
  const active = rows.filter((r) => !r.cancelled);
  const byProduct = (p: string) => active.filter((r) => r.product === p).reduce((s, r) => s + r.gross, 0);
  const loading = !fd.data || !bonds.data || !aif.data;

  return (
    <>
      <ListFilters searchHint="Client, PAN, code, FD / bond / fund name, ISIN" />
      {!loading && (
        <div className="cards">
          {(['FD', 'BOND', 'AIF'] as const).map((p) => (
            <div key={p} className="stat-card">
              <span className="label">{PRODUCT_NAME[p]} gross</span>
              <span className="value"><Amount value={byProduct(p)} /></span>
              <span className="hint">{active.filter((r) => r.product === p).length} entries</span>
            </div>
          ))}
        </div>
      )}
      {(fd.error || bonds.error || aif.error) && <p className="error">{fd.error ?? bonds.error ?? aif.error}</p>}
      {!loading && rows.length === 0 && <div className="empty">No entries match. Use “New entry” to add one.</div>}
      {rows.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Product</th>
              <th>Month</th>
              <th>Client</th>
              <th>FD / bond / fund</th>
              <th>RM</th>
              <th className="num">Amount</th>
              <th className="num">Gross</th>
              <th className="num">Invoice</th>
              <th>Payment</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={`${r.product}-${r.id}`} className={r.cancelled ? 'muted' : undefined}>
                <td>
                  <span className="badge">{PRODUCT_NAME[r.product]}</span>
                </td>
                <td>{formatMonth(r.month)}</td>
                <td>
                  <button type="button" className="cell-link" onClick={() => onOpen(r.product, r.id)}>
                    {r.client}
                  </button>
                  {r.cancelled && <span className="badge">cancelled</span>}
                </td>
                <td>{r.item}</td>
                <td>{r.rm}</td>
                <td className="num"><Amount value={r.amount} /></td>
                <td className="num"><Amount value={r.gross} /></td>
                <td className="num"><Amount value={r.invoice} /></td>
                <td>
                  <span className={r.payment === 'RECEIVED' ? 'badge ok' : 'badge warn'}>{PAYMENT_LABEL[r.payment]}</span>
                </td>
              </tr>
            ))}
            <tr className="total">
              <td colSpan={6}>Total ({active.length} entries)</td>
              <td className="num"><Amount value={active.reduce((s, r) => s + r.gross, 0)} /></td>
              <td className="num"><Amount value={active.reduce((s, r) => s + r.invoice, 0)} /></td>
              <td />
            </tr>
          </tbody>
        </table>
      )}
    </>
  );
}
