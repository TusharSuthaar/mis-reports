import { useMemo, useState } from 'react';
import type { ClientRow, Page } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { Amount } from '../../components/Amount';
import { matchClients } from './ClientAndRm';

/** One client found by its name only (no PAN): what the file says and the client suggested (R-100). */
export interface NameMatchRow {
  key: string;
  /** The file's reference: Finesse code, party code or policy number. */
  ref: string;
  nameInFile: string | null;
  clientId: number;
  clientName: string | null;
  pan: string | null;
  rmName: string | null;
  amount: number;
  confirmed?: boolean;
}

/**
 * Clients found by name only, each confirmed once (R-100): "Confirm" keeps the client suggested; "Different client" finds
 * the right one by PAN or client code. A confirmed match is remembered, so the same code or name is not asked again.
 */
export function NameMatches({ title, refLabel, amountLabel, rows, canEdit, onConfirm }: {
  title: string;
  refLabel: string;
  amountLabel: string;
  rows: NameMatchRow[];
  canEdit: boolean;
  onConfirm: (row: NameMatchRow, clientId: number) => Promise<unknown>;
}) {
  const [picking, setPicking] = useState<string | null>(null);
  const { busy, error, run } = useSubmit();
  const open = rows.filter((r) => !r.confirmed);
  if (rows.length === 0) return null;
  const confirm = (row: NameMatchRow, clientId: number) => run(async () => {
    await onConfirm(row, clientId);
    setPicking(null);
  });
  return (
    <div className="card-block" style={{ marginTop: 8 }}>
      <p className={open.length > 0 ? 'error' : 'ok'} style={{ marginTop: 0 }}>
        {open.length > 0
          ? `${title}: ${open.length} found by name only (no PAN). Check each one is the right person, then confirm it or pick the right client. Each is asked once.`
          : `${title}: every client found by name is confirmed.`}
      </p>
      <table className="compact highlight-rows">
        <thead>
          <tr>
            <th>{refLabel}</th>
            <th>Name in the file</th>
            <th>Client suggested</th>
            <th>RM</th>
            <th className="num">{amountLabel}</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.key} className={r.confirmed ? 'muted' : undefined}>
              <td className="mono">{r.ref}</td>
              <td>{r.nameInFile ?? '—'}</td>
              <td>
                {r.clientName} <span className="mono">{r.pan ?? 'no PAN'}</span>
              </td>
              <td>{r.rmName ?? <span className="muted">no RM</span>}</td>
              <td className="num"><Amount value={r.amount} /></td>
              <td>
                {r.confirmed ? (
                  <span className="badge ok">confirmed</span>
                ) : canEdit && picking !== r.key ? (
                  <span className="inline-form">
                    <button type="button" disabled={busy} onClick={() => confirm(r, r.clientId)}>
                      Confirm
                    </button>
                    <button type="button" className="secondary" disabled={busy} onClick={() => setPicking(r.key)}>
                      Different client…
                    </button>
                  </span>
                ) : null}
                {picking === r.key && <PickClient busy={busy} onPick={(id) => confirm(r, id)} onCancel={() => setPicking(null)} />}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {error && <p className="error">{error}</p>}
    </div>
  );
}

/** Finds a client by PAN or client code (the name alone is what was not enough). */
function PickClient({ busy, onPick, onCancel }: { busy: boolean; onPick: (clientId: number) => void; onCancel: () => void }) {
  const [lookup, setLookup] = useState('');
  const [looked, setLooked] = useState('');
  const found = useLoad<Page<ClientRow>>(looked ? `/api/clients?q=${encodeURIComponent(looked)}&size=20` : null);
  const matches = useMemo(() => (looked && found.data ? matchClients(found.data.items, looked) : null), [found.data, looked]);
  return (
    <div className="inline-form" style={{ marginTop: 6, flexWrap: 'wrap' }}>
      <label>
        PAN or client code
        <input
          value={lookup}
          onChange={(e) => setLookup(e.target.value.toUpperCase().replace(/\s/g, ''))}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              setLooked(lookup.trim());
            }
          }}
          placeholder="ABCDE1234F or FC01234"
        />
      </label>
      <button type="button" className="secondary" disabled={lookup.trim().length < 3} onClick={() => setLooked(lookup.trim())}>
        Find
      </button>
      <button type="button" className="link" onClick={onCancel}>
        Cancel
      </button>
      {matches && matches.length === 0 && <p className="muted">No client has this PAN or code.</p>}
      {(matches ?? []).map((c) => (
        <button key={c.id} type="button" className="link" disabled={busy} onClick={() => onPick(c.id)}>
          Use {c.name} · <span className="mono">{c.pan ?? 'no PAN'}</span> · {c.rmName ?? 'no RM'}
        </button>
      ))}
    </div>
  );
}
