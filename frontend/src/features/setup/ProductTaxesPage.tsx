import { useState } from 'react';
import { api } from '../../api/client';
import type { ProductCode, ProductTaxLine, TaxRate } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { DatedChangeForm } from '../../components/DatedChangeForm';
import { SidePanel } from '../../components/SidePanel';
import { formatDate } from '../../format';

/** Setup → Products & taxes: GST and TDS % per product, changed "from a date" (R-71). */
export function ProductTaxesPage() {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const lines = useLoad<ProductTaxLine[]>('/api/product-taxes');
  const [open, setOpen] = useState<ProductCode | null>(null);
  const line = lines.data?.find((l) => l.product === open);

  return (
    <section>
      <h1>Products &amp; taxes</h1>
      <p className="muted">
        GST and TDS for each product. A change applies from the date you give; earlier months keep the old rate. An FD entry takes the
        GST % of its month and starts with TDS ticked when “TDS by default” is on.
      </p>
      {lines.error && <p className="error">{lines.error}</p>}
      {lines.data && (
        <table>
          <thead>
            <tr>
              <th>Product</th>
              <th className="num">GST %</th>
              <th className="num">TDS %</th>
              <th>TDS by default</th>
              <th>Since</th>
            </tr>
          </thead>
          <tbody>
            {lines.data.map((l) => (
              <tr key={l.product}>
                <td>
                  <button type="button" className="cell-link" onClick={() => setOpen(l.product)}>
                    {l.name}
                  </button>
                </td>
                <td className="num">{l.now ? l.now.gstPercent : <span className="muted">not set</span>}</td>
                <td className="num">{l.now ? l.now.tdsPercent : ''}</td>
                <td>{l.now ? (l.now.tdsByDefault ? 'Yes' : 'No') : ''}</td>
                <td>{l.now ? formatDate(l.now.validFrom) : ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {line && <TaxPanel line={line} canEdit={canEdit} onClose={() => setOpen(null)} onChanged={lines.reload} />}
    </section>
  );
}

function TaxPanel({ line, canEdit, onClose, onChanged }: { line: ProductTaxLine; canEdit: boolean; onClose: () => void; onChanged: () => void }) {
  const history = useLoad<TaxRate[]>(`/api/product-taxes/${line.product}`);
  const [gst, setGst] = useState(String(line.now?.gstPercent ?? 18));
  const [tds, setTds] = useState(String(line.now?.tdsPercent ?? 0));
  const [byDefault, setByDefault] = useState(line.now?.tdsByDefault ?? false);
  const [reason, setReason] = useState('');
  return (
    <SidePanel title={line.name} onClose={onClose}>
      {canEdit && (
        <DatedChangeForm
          title="Change rates"
          dateLabel="From"
          submitLabel="Save"
          canSubmit={gst !== '' && tds !== ''}
          onSubmit={(from) =>
            api(`/api/product-taxes/${line.product}`, {
              method: 'POST',
              body: { gstPercent: Number(gst), tdsPercent: Number(tds), tdsByDefault: byDefault, from, reason },
            })
          }
          onDone={() => {
            setReason('');
            history.reload();
            onChanged();
          }}
        >
          <label>
            GST %
            <input type="number" min={0} max={100} step="0.01" value={gst} onChange={(e) => setGst(e.target.value)} required />
          </label>
          <label>
            TDS %
            <input type="number" min={0} max={100} step="0.01" value={tds} onChange={(e) => setTds(e.target.value)} required />
          </label>
          <label className="check">
            <input type="checkbox" checked={byDefault} onChange={(e) => setByDefault(e.target.checked)} />
            TDS by default (a new entry starts with TDS ticked)
          </label>
          <label>
            Reason
            <input value={reason} onChange={(e) => setReason(e.target.value)} />
          </label>
        </DatedChangeForm>
      )}
      <h3>History</h3>
      {history.data && history.data.length === 0 && <p className="muted">Never set.</p>}
      {history.data && history.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>From</th>
              <th>To</th>
              <th className="num">GST %</th>
              <th className="num">TDS %</th>
              <th>Default</th>
              <th>By</th>
            </tr>
          </thead>
          <tbody>
            {history.data.map((h) => (
              <tr key={h.id}>
                <td>{formatDate(h.validFrom)}</td>
                <td>{h.validTo ? formatDate(h.validTo) : 'now'}</td>
                <td className="num">{h.gstPercent}</td>
                <td className="num">{h.tdsPercent}</td>
                <td>{h.tdsByDefault ? 'Yes' : 'No'}</td>
                <td>{h.changedBy}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </SidePanel>
  );
}
