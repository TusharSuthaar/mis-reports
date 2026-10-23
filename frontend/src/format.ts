const dateTime = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' });

export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}

const dateOnly = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });

/** "2026-04-01" → "01 Apr 2026"; null/undefined → "". Parsed as a calendar date, not shifted by time zone. */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '';
  const [y, m, d] = iso.split('-').map(Number);
  return dateOnly.format(new Date(y, m - 1, d));
}

/** Today as yyyy-mm-dd in the browser's time zone, for date inputs. */
export function todayIso(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

const rupees = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** 1000000.1 → "10,00,000.10" (Indian grouping, 2 decimals). */
export function formatMoney(n: number | null | undefined): string {
  return n == null ? '' : rupees.format(n);
}

/** How lists, totals and the Dashboard show amounts: full rupees, thousands (K) or lakhs (L). */
export type AmountUnit = 'full' | 'K' | 'L';

const short = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** 1234567 → "12.35 L" (L), "1,234.57 K" (K) or "12,34,567.00" (full). */
export function formatAmount(n: number | null | undefined, unit: AmountUnit): string {
  if (n == null) return '';
  if (unit === 'full') return rupees.format(n);
  return `${short.format(n / (unit === 'K' ? 1000 : 100000))} ${unit}`;
}

/** "1000000" → "10,00,000" while typing an amount (Indian grouping, no decimals shown unless typed). */
export function indianDigits(text: string): string {
  const clean = text.replace(/[^0-9.]/g, '');
  const [whole, ...rest] = clean.split('.');
  if (!whole) return clean;
  const grouped = Number(whole).toLocaleString('en-IN');
  return rest.length ? `${grouped}.${rest.join('').slice(0, 2)}` : grouped;
}

/** "2026-08" → "Aug-26", as in the registers. */
export function formatMonth(ym: string): string {
  const [y, m] = ym.split('-').map(Number);
  return `${['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][m - 1]}-${String(y).slice(2)}`;
}
