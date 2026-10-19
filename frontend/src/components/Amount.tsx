import { useSyncExternalStore } from 'react';
import { formatAmount, formatMoney, type AmountUnit } from '../format';

/*
 * The "Amounts" switch (₹ / K / Lakhs). It only changes how lists, totals and the Dashboard show figures — entry forms,
 * Excel downloads and the END file always keep exact rupees. Remembered in this browser.
 */
const KEY = 'amountUnit';
const listeners = new Set<() => void>();

function read(): AmountUnit {
  try {
    const v = localStorage.getItem(KEY);
    return v === 'full' || v === 'K' || v === 'L' ? v : 'L';
  } catch {
    return 'L';
  }
}

let current: AmountUnit = read();

export function setAmountUnit(unit: AmountUnit) {
  current = unit;
  try {
    localStorage.setItem(KEY, unit);
  } catch {
    /* private window: still works for this visit */
  }
  listeners.forEach((l) => l());
}

export function useAmountUnit(): AmountUnit {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    () => current,
  );
}

/** A figure in the chosen unit; hover shows the exact rupees. */
export function Amount({ value }: { value: number | null | undefined }) {
  const unit = useAmountUnit();
  if (value == null) return null;
  return unit === 'full' ? <>{formatMoney(value)}</> : <span title={`₹ ${formatMoney(value)}`}>{formatAmount(value, unit)}</span>;
}

/** For text built from strings (e.g. "net 12.35 L"). */
export function useMoney(): (n: number | null | undefined) => string {
  const unit = useAmountUnit();
  return (n) => formatAmount(n, unit);
}

const OPTIONS: { unit: AmountUnit; label: string; hint: string }[] = [
  { unit: 'full', label: '₹', hint: 'Exact rupees' },
  { unit: 'K', label: 'K', hint: 'Thousands' },
  { unit: 'L', label: 'Lakhs', hint: 'Lakhs' },
];

export function AmountSwitch() {
  const unit = useAmountUnit();
  return (
    <div className="amount-switch" role="group" aria-label="Show amounts in">
      <span>Amounts</span>
      {OPTIONS.map((o) => (
        <button key={o.unit} type="button" title={o.hint} aria-pressed={unit === o.unit} className={unit === o.unit ? 'on' : undefined} onClick={() => setAmountUnit(o.unit)}>
          {o.label}
        </button>
      ))}
    </div>
  );
}
