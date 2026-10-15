import { useState, type FormEvent, type ReactNode } from 'react';
import { useSubmit } from '../api/useSubmit';
import { todayIso } from '../format';

/**
 * A small form for one dated change: "what" (children) and "from when" (the date).
 * On success the form resets and {@code onDone} runs.
 */
export function DatedChangeForm({
  title,
  dateLabel,
  submitLabel,
  children,
  canSubmit = true,
  onSubmit,
  onDone,
}: {
  title: string;
  dateLabel: string;
  submitLabel: string;
  children?: ReactNode;
  canSubmit?: boolean;
  onSubmit: (date: string) => Promise<unknown>;
  onDone: () => void;
}) {
  const [date, setDate] = useState(todayIso());
  const { busy, error, run } = useSubmit();

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (await run(() => onSubmit(date))) onDone();
  }

  return (
    <form onSubmit={submit} className="separated">
      <h3>{title}</h3>
      {children}
      <label>
        {dateLabel}
        <input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
      </label>
      {error && <p className="error">{error}</p>}
      <button type="submit" className="secondary" disabled={busy || !canSubmit}>
        {submitLabel}
      </button>
    </form>
  );
}
