import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PAYMENT_LABEL, type Employee, type Partner } from '../api/types';
import { useLoad } from '../api/useLoad';

/** The filter keys a product list understands (R-77); they live in the page address, so a refresh or a shared link keeps them. */
export const FILTER_KEYS = ['from', 'to', 'q', 'rmId', 'partnerId', 'platform', 'platformId', 'payment', 'includeCancelled'] as const;

/** The current filters as a query string for the list / Excel endpoints ("" when none). */
export function useFilterQuery(): string {
  const [params] = useSearchParams();
  const q = new URLSearchParams();
  for (const k of FILTER_KEYS) {
    const v = params.get(k);
    if (v) q.set(k, v);
  }
  const s = q.toString();
  return s ? `?${s}` : '';
}

/**
 * All months by default; narrow by month range, text, RM, referral / sub-broker, platform, payment status, cancelled.
 * {@code platformKey}: "platform" (FD: a name) or "platformId" (Bonds: a platform of Setup → Platforms).
 */
export function ListFilters({
  searchHint,
  platformKey,
  platforms,
}: {
  searchHint: string;
  platformKey?: 'platform' | 'platformId';
  platforms?: { value: string; label: string }[];
}) {
  const [params, setParams] = useSearchParams();
  const employees = useLoad<Employee[]>('/api/employees');
  const partners = useLoad<Partner[]>('/api/partners');
  const [text, setText] = useState(params.get('q') ?? '');

  const set = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    setParams(next, { replace: true });
  };
  // Search as you type, once typing pauses.
  useEffect(() => {
    const t = window.setTimeout(() => {
      if ((params.get('q') ?? '') !== text.trim()) set('q', text.trim());
    }, 350);
    return () => window.clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [text]);

  const active = FILTER_KEYS.some((k) => params.get(k));
  const rms = (employees.data ?? []).filter((e) => e.status === 'ACTIVE');
  const sharePartners = (partners.data ?? []).filter((p) => p.type === 'SUB_BROKER' || p.type === 'REFERRAL');

  return (
    <div className="filters list-filters">
      <label>
        From month
        <input type="month" value={params.get('from') ?? ''} onChange={(e) => set('from', e.target.value)} />
      </label>
      <label>
        To month
        <input type="month" value={params.get('to') ?? ''} onChange={(e) => set('to', e.target.value)} />
      </label>
      <label className="grow">
        Search
        <input value={text} onChange={(e) => setText(e.target.value)} placeholder={searchHint} />
      </label>
      <label>
        RM
        <select value={params.get('rmId') ?? ''} onChange={(e) => set('rmId', e.target.value)}>
          <option value="">All</option>
          {rms.map((e) => (
            <option key={e.id} value={e.id}>
              {e.branchName ? `${e.name} — ${e.branchName}` : e.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        Referral / sub-broker
        <select value={params.get('partnerId') ?? ''} onChange={(e) => set('partnerId', e.target.value)}>
          <option value="">All</option>
          {sharePartners.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
      </label>
      {platformKey && (
      <label>
        Platform
        <select value={params.get(platformKey) ?? ''} onChange={(e) => set(platformKey, e.target.value)}>
          <option value="">All</option>
          {(platforms ?? []).map((p) => (
            <option key={p.value} value={p.value}>
              {p.label}
            </option>
          ))}
        </select>
      </label>
      )}
      <label>
        Payment
        <select value={params.get('payment') ?? ''} onChange={(e) => set('payment', e.target.value)}>
          <option value="">All</option>
          {Object.entries(PAYMENT_LABEL).map(([k, v]) => (
            <option key={k} value={k}>
              {v}
            </option>
          ))}
        </select>
      </label>
      <label className="check">
        <input type="checkbox" checked={params.get('includeCancelled') === 'true'} onChange={(e) => set('includeCancelled', e.target.checked ? 'true' : '')} />
        Show cancelled
      </label>
      {active && (
        <button
          type="button"
          className="link"
          onClick={() => {
            setText('');
            // Only the filters go: the tab and any open pop-up stay.
            const next = new URLSearchParams(params);
            FILTER_KEYS.forEach((k) => next.delete(k));
            setParams(next, { replace: true });
          }}
        >
          Clear filters
        </button>
      )}
    </div>
  );
}
