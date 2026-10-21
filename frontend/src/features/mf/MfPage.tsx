import { Link } from 'react-router-dom';
import { useLoad } from '../../api/useLoad';
import { Amount } from '../../components/Amount';
import { ListFilters, useFilterQuery } from '../../components/ListFilters';
import type { PartnerAmount } from '../clients/MfBrokerage';
import { formatMoney } from '../../format';
import { formatMonth } from '../fd/FdPage';

/** One client's MF brokerage in one month (R-81); no split yet when the month is saved but not processed. */
export interface MfRow {
  lineId: number;
  month: string;
  status: 'OPEN' | 'PROCESSED' | 'LOCKED';
  adjustsMonth: string | null;
  clientId: number;
  clientName: string;
  pan: string | null;
  rm: string | null;
  branch: string | null;
  region: string | null;
  gross: number;
  gst: number;
  net: number;
  partners: PartnerAmount[];
  partnersTotal: number | null;
  epsilon: number | null;
}

export interface MfList {
  rows: MfRow[];
  totals: { rows: number; gross: number; gst: number; net: number; partners: number; epsilon: number };
  truncated: boolean;
}

const STATUS: Record<MfRow['status'], { label: string; cls: string }> = {
  OPEN: { label: 'Saved', cls: 'badge warn' },
  PROCESSED: { label: 'Processed', cls: 'badge' },
  LOCKED: { label: 'Locked', cls: 'badge ok' },
};

const pct = (f: number | null) => (f == null ? '' : ` ${Math.round(f * 10000) / 100}%`);

/** MF brokerage of every client across months, from the saved months (uploaded on Month close). */
export function MfPage() {
  const query = useFilterQuery();
  const list = useLoad<MfList>(`/api/mf${query}`);
  const t = list.data?.totals;
  return (
    <section>
      <ListFilters searchHint="Client name, full PAN or client code" entryFilters={false} />
      {list.error && <p className="error">{list.error}</p>}
      {t && (
        <div className="cards">
          <div className="stat-card">
            <span className="label">MF trail (incl. GST)</span>
            <span className="value">
              ₹ <Amount value={t.gross} />
            </span>
            <span className="hint">GST ₹ <Amount value={t.gst} /></span>
          </div>
          <div className="stat-card">
            <span className="label">MF net brokerage</span>
            <span className="value">
              ₹ <Amount value={t.net} />
            </span>
            <span className="hint">{t.rows.toLocaleString('en-IN')} client-months</span>
          </div>
          <div className="stat-card">
            <span className="label">Partners&rsquo; share</span>
            <span className="value">
              ₹ <Amount value={t.partners} />
            </span>
            <span className="hint">processed months</span>
          </div>
          <div className="stat-card">
            <span className="label">Epsilon keeps</span>
            <span className="value">
              ₹ <Amount value={t.epsilon} />
            </span>
            <span className="hint">processed months</span>
          </div>
        </div>
      )}
      {list.data && list.data.rows.length === 0 && (
        <div className="empty">
          No MF brokerage matches. MF comes from the monthly Finesse brokerage file uploaded on <Link to="/month">Month close</Link>.
        </div>
      )}
      {list.data?.truncated && (
        <p className="muted">
          Showing the first {list.data.rows.length.toLocaleString('en-IN')} of {t?.rows.toLocaleString('en-IN')} rows (totals cover them all). Narrow by
          month, RM or client to see the rest.
        </p>
      )}
      {list.data && list.data.rows.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Month</th>
              <th>Client</th>
              <th>RM / branch</th>
              <th className="num">MF trail (₹)</th>
              <th className="num">GST</th>
              <th className="num">MF net</th>
              <th>Partners</th>
              <th className="num">Partners&rsquo; share</th>
              <th className="num">Epsilon keeps</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {list.data.rows.map((r) => (
              <tr key={r.lineId}>
                <td>
                  {formatMonth(r.month)}
                  {r.adjustsMonth && <div className="muted">correction for {formatMonth(r.adjustsMonth)}</div>}
                </td>
                <td>
                  <Link to={`/clients/${r.clientId}?tab=mf`}>{r.clientName}</Link>
                  {r.pan && <div className="muted mono">{r.pan}</div>}
                </td>
                <td>{r.rm ? `${r.rm}${r.branch ? ` — ${r.branch}` : ''}` : <span className="muted">—</span>}</td>
                <td className="num">
                  {formatMoney(r.gross)}
                </td>
                <td className="num">
                  {formatMoney(r.gst)}
                </td>
                <td className="num">
                  {formatMoney(r.net)}
                </td>
                <td>
                  {r.epsilon == null ? (
                    <span className="muted">not split yet</span>
                  ) : r.partners.length === 0 ? (
                    <span className="muted">direct</span>
                  ) : (
                    r.partners.map((p) => (
                      <div key={`${p.role}-${p.name}`}>
                        {p.name} <span className="muted">· {p.role}{pct(p.percent)}</span>
                      </div>
                    ))
                  )}
                </td>
                <td className="num">{r.partnersTotal == null ? '' : formatMoney(r.partnersTotal)}</td>
                <td className="num">{r.epsilon == null ? '' : formatMoney(r.epsilon)}</td>
                <td>
                  <span className={STATUS[r.status].cls}>{STATUS[r.status].label}</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
