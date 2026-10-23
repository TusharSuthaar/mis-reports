import { useLoad } from '../../api/useLoad';
import { Amount } from '../../components/Amount';
import { formatMoney, formatMonth } from '../../format';

export interface PartnerAmount {
  role: string;
  name: string;
  /** A fraction: 0.5 = 50%. */
  percent: number | null;
  amount: number;
}

/** One month of a client's brokerage (R-09); no split yet when the month is saved but not processed. */
export interface BrokerageMonth {
  lineId: number;
  month: string | null;
  status: 'OPEN' | 'PROCESSED' | 'LOCKED' | null;
  adjustsMonth: string | null;
  gross: number;
  gst: number;
  net: number;
  partners: PartnerAmount[];
  partnersTotal: number | null;
  epsilon: number | null;
  rm: string | null;
  branch: string | null;
}

const STATUS: Record<string, { label: string; cls: string }> = {
  OPEN: { label: 'Saved, not processed', cls: 'badge warn' },
  PROCESSED: { label: 'Processed', cls: 'badge' },
  LOCKED: { label: 'Locked', cls: 'badge ok' },
};

const pct = (f: number | null) => (f == null ? '' : ` ${Math.round(f * 10000) / 100}%`);

/** The client's MF brokerage month by month, as saved and split (R-09). */
export function MfBrokerage({ clientId }: { clientId: number }) {
  const rows = useLoad<BrokerageMonth[]>(`/api/clients/${clientId}/brokerage?product=MF`);
  if (rows.error) return <p className="error">{rows.error}</p>;
  if (!rows.data) return <p className="muted">Loading…</p>;
  if (rows.data.length === 0) return <div className="empty">No MF brokerage for this client yet (no saved month has them).</div>;
  const sum = (k: 'gross' | 'gst' | 'net') => rows.data!.reduce((s, r) => s + r[k], 0);
  const split = rows.data.filter((r) => r.epsilon != null);
  const months = new Set(rows.data.map((r) => r.month)).size;
  return (
    <>
      <div className="cards">
        <div className="stat-card">
          <span className="label">MF net brokerage</span>
          <span className="value">
            ₹ <Amount value={sum('net')} />
          </span>
          <span className="hint">
            {months} month{months === 1 ? '' : 's'}
          </span>
        </div>
        <div className="stat-card">
          <span className="label">Partners&rsquo; share</span>
          <span className="value">
            ₹ <Amount value={split.reduce((s, r) => s + (r.partnersTotal ?? 0), 0)} />
          </span>
          <span className="hint">processed months</span>
        </div>
        <div className="stat-card">
          <span className="label">Epsilon keeps</span>
          <span className="value">
            ₹ <Amount value={split.reduce((s, r) => s + (r.epsilon ?? 0), 0)} />
          </span>
          <span className="hint">processed months</span>
        </div>
      </div>
      <table>
        <thead>
          <tr>
            <th>Month</th>
            <th className="num">MF trail (₹)</th>
            <th className="num">GST</th>
            <th className="num">MF net brokerage</th>
            <th>Partners</th>
            <th className="num">Partners&rsquo; share</th>
            <th className="num">Epsilon keeps</th>
            <th>RM / branch</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {rows.data.map((r) => (
            <tr key={r.lineId}>
              <td>
                {r.month ? formatMonth(r.month) : '—'}
                {r.adjustsMonth && <div className="muted">correction for {formatMonth(r.adjustsMonth)}</div>}
              </td>
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
                  <span className="muted">direct (no partner)</span>
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
              <td>{r.rm ? `${r.rm}${r.branch ? ` — ${r.branch}` : ''}` : <span className="muted">—</span>}</td>
              <td>{r.status && <span className={STATUS[r.status].cls}>{STATUS[r.status].label}</span>}</td>
            </tr>
          ))}
          <tr className="total">
            <td>Total</td>
            <td className="num">
              <Amount value={sum('gross')} />
            </td>
            <td className="num">
              <Amount value={sum('gst')} />
            </td>
            <td className="num">
              <Amount value={sum('net')} />
            </td>
            <td />
            <td className="num">
              <Amount value={split.reduce((s, r) => s + (r.partnersTotal ?? 0), 0)} />
            </td>
            <td className="num">
              <Amount value={split.reduce((s, r) => s + (r.epsilon ?? 0), 0)} />
            </td>
            <td colSpan={2} />
          </tr>
        </tbody>
      </table>
    </>
  );
}
