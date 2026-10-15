import { formatDate, formatDateTime } from '../format';

interface Row {
  id: number;
  name: string;
  validFrom: string;
  validTo: string | null;
  changedBy: string;
  changedAt: string;
}

/** A dated history, newest first. An empty "To" means the entry is current. */
export function HistoryTable({ rows, label, actions }: { rows: Row[]; label: string; actions?: (row: Row) => React.ReactNode }) {
  if (rows.length === 0) return <p className="muted">No history yet.</p>;
  return (
    <table className="compact">
      <thead>
        <tr>
          <th>{label}</th>
          <th>From</th>
          <th>To</th>
          {actions && <th />}
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => (
          <tr key={r.id} title={`Last changed by ${r.changedBy}, ${formatDateTime(r.changedAt)}`}>
            <td>{r.name}</td>
            <td>{formatDate(r.validFrom)}</td>
            <td>{r.validTo ? formatDate(r.validTo) : <span className="badge">current</span>}</td>
            {actions && <td>{actions(r)}</td>}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
