import { useState, type FormEvent } from 'react';
import { useSubmit } from '../api/useSubmit';

/** One-field form used for create and rename. */
export function NameForm({
  label,
  initial = '',
  submitLabel,
  onSubmit,
  onDone,
}: {
  label: string;
  initial?: string;
  submitLabel: string;
  onSubmit: (name: string) => Promise<unknown>;
  onDone: () => void;
}) {
  const [name, setName] = useState(initial);
  const { busy, error, run } = useSubmit();

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (await run(() => onSubmit(name))) onDone();
  }

  return (
    <form onSubmit={submit}>
      <label>
        {label}
        <input value={name} onChange={(e) => setName(e.target.value)} required />
      </label>
      {error && <p className="error">{error}</p>}
      <button type="submit" disabled={busy || name.trim() === '' || name === initial}>
        {submitLabel}
      </button>
    </form>
  );
}
