import { useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { ExchangeRate } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { formatDate, formatDateTime, todayIso } from '../../format';

/** USD → ₹ rates by day (R-84): fetched from Frankfurter (ECB) when a form needs a day, or set by hand here. */
export function ExchangeRatesPage() {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const list = useLoad<ExchangeRate[]>('/api/exchange-rates/USD');
  const [day, setDay] = useState(todayIso());
  const [rate, setRate] = useState('');
  const [reason, setReason] = useState('');
  const [checked, setChecked] = useState<ExchangeRate | null>(null);
  const { busy, error, run } = useSubmit();

  const save = async (e: FormEvent) => {
    e.preventDefault();
    if (await run(() => api(`/api/exchange-rates/USD/${day}`, { method: 'PUT', body: { rate: Number(rate), reason: reason || undefined } }))) {
      setRate('');
      setReason('');
      list.reload();
    }
  };
  const fetchDay = async () => {
    const out: { r?: ExchangeRate } = {};
    if (await run(async () => { out.r = await api<ExchangeRate>(`/api/exchange-rates/USD/lookup?date=${day}`, { method: 'POST' }); })) {
      setChecked(out.r ?? null);
      list.reload();
    }
  };

  return (
    <section>
      <p className="muted">
        US dollar to rupee, one rate per day. The GIFT City form fetches a day&rsquo;s rate from Frankfurter (free European Central Bank rates) the first
        time it is needed and keeps it here; only the day and &ldquo;USD to INR&rdquo; are sent. On a weekend or holiday it is the last working day&rsquo;s
        rate. A rate set here by hand is used for that day from then on; saved entries keep the rate they were saved with.
      </p>
      {canEdit && (
        <form className="panel-card inline-form" onSubmit={save} style={{ flexWrap: 'wrap', alignItems: 'flex-end', gap: 12 }}>
          <label>
            Day
            <input type="date" value={day} onChange={(e) => setDay(e.target.value)} required />
          </label>
          <button type="button" className="secondary" disabled={busy || !day} onClick={fetchDay}>
            Fetch ECB rate
          </button>
          <label>
            Rate (₹ per USD)
            <input type="number" min={0} step="0.0001" value={rate} onChange={(e) => setRate(e.target.value)} placeholder="e.g. 95.97" />
          </label>
          <label className="grow">
            Reason
            <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. rate on the fund statement" />
          </label>
          <button type="submit" disabled={busy || !rate}>
            Set by hand
          </button>
          {checked && <p className="muted" style={{ width: '100%', margin: 0 }}>{checked.rate != null ? `${formatDate(checked.day)}: ₹${checked.rate} (${checked.note})` : checked.note}</p>}
          {error && <p className="error" style={{ width: '100%', margin: 0 }}>{error}</p>}
        </form>
      )}
      {list.error && <p className="error">{list.error}</p>}
      {list.data && list.data.length === 0 && <p className="muted">No rates yet: they are added the first time a GIFT City entry needs one.</p>}
      {list.data && list.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Day</th>
              <th className="num">₹ per USD</th>
              <th>Source</th>
              <th>Reason</th>
              <th>By</th>
            </tr>
          </thead>
          <tbody>
            {list.data.map((r) => (
              <tr key={r.day}>
                <td>{formatDate(r.day)}</td>
                <td className="num">{r.rate}</td>
                <td>
                  <span className={r.source === 'MANUAL' ? 'badge warn' : 'badge'}>{r.source === 'MANUAL' ? 'By hand' : 'ECB'}</span> <span className="muted">{r.note}</span>
                </td>
                <td>{r.reason ?? ''}</td>
                <td className="muted">
                  {r.changedBy} {r.changedAt && `· ${formatDateTime(r.changedAt)}`}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
