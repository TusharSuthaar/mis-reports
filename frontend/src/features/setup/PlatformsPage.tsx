import { useState } from 'react';
import { api } from '../../api/client';
import type { Platform, PlatformTerms } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { DatedChangeForm } from '../../components/DatedChangeForm';
import { NameForm } from '../../components/NameForm';
import { SidePanel } from '../../components/SidePanel';
import { formatDate } from '../../format';

/** Setup → Platforms: where deals are booked (GoldenPi, SMC…) and their brokerage terms, changed "from a date" (R-75). */
export function PlatformsPage() {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const list = useLoad<Platform[]>('/api/platforms');
  const [open, setOpen] = useState<number | 'new' | null>(null);
  const platform = typeof open === 'number' ? list.data?.find((p) => p.id === open) : undefined;

  return (
    <section>
      <div className="page-head">
        <h1>Platforms</h1>
        {canEdit && (
          <div className="actions">
            <button type="button" onClick={() => setOpen('new')}>
              Add platform
            </button>
          </div>
        )}
      </div>
      <p className="muted">
        Each platform&apos;s brokerage terms: whether its brokerage already includes GST, the TDS it deducts, and the share of the mark-up
        it pays Epsilon. A bond deal takes the terms of its month.
      </p>
      {list.error && <p className="error">{list.error}</p>}
      {list.data && list.data.length === 0 && <p className="muted">No platforms yet.</p>}
      {list.data && list.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Platform</th>
              <th>Brokerage</th>
              <th className="num">TDS %</th>
              <th className="num">Our share %</th>
              <th>Since</th>
            </tr>
          </thead>
          <tbody>
            {list.data.map((p) => (
              <tr key={p.id}>
                <td>
                  <button type="button" className="cell-link" onClick={() => setOpen(p.id)}>
                    {p.name}
                  </button>
                </td>
                <td>{p.now ? (p.now.gstInclusive ? 'Includes GST' : 'GST on top') : <span className="muted">no terms</span>}</td>
                <td className="num">{p.now?.tdsPercent ?? ''}</td>
                <td className="num">{p.now?.ourShare ?? ''}</td>
                <td>{p.now ? formatDate(p.now.validFrom) : ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {open === 'new' && (
        <SidePanel title="Add platform" onClose={() => setOpen(null)}>
          <NameForm
            label="Platform name"
            submitLabel="Add platform"
            onSubmit={(name) => api('/api/platforms', { method: 'POST', body: { name } })}
            onDone={() => {
              setOpen(null);
              list.reload();
            }}
          />
        </SidePanel>
      )}
      {platform && <TermsPanel platform={platform} canEdit={canEdit} onClose={() => setOpen(null)} onChanged={list.reload} />}
    </section>
  );
}

function TermsPanel({ platform, canEdit, onClose, onChanged }: { platform: Platform; canEdit: boolean; onClose: () => void; onChanged: () => void }) {
  const history = useLoad<PlatformTerms[]>(`/api/platforms/${platform.id}/terms`);
  const [inclusive, setInclusive] = useState(platform.now?.gstInclusive ?? false);
  const [tds, setTds] = useState(String(platform.now?.tdsPercent ?? 5));
  const [share, setShare] = useState(String(platform.now?.ourShare ?? 100));
  const [reason, setReason] = useState('');
  return (
    <SidePanel title={platform.name} onClose={onClose}>
      {canEdit && (
        <>
          <DatedChangeForm
            title={platform.now ? 'Change terms' : 'Set terms'}
            dateLabel="From"
            submitLabel="Save"
            canSubmit={tds !== '' && share !== ''}
            onSubmit={(from) =>
              api(`/api/platforms/${platform.id}/terms`, {
                method: 'POST',
                body: { gstInclusive: inclusive, tdsPercent: Number(tds), ourShare: Number(share), from, reason },
              })
            }
            onDone={() => {
              setReason('');
              history.reload();
              onChanged();
            }}
          >
            <label className="check">
              <input type="checkbox" checked={inclusive} onChange={(e) => setInclusive(e.target.checked)} />
              Brokerage includes GST (gross = brokerage ÷ 1.18)
            </label>
            <label>
              TDS %
              <input type="number" min={0} max={100} step="0.01" value={tds} onChange={(e) => setTds(e.target.value)} required />
            </label>
            <label>
              Our share of the mark-up %
              <input type="number" min={0.01} max={100} step="0.01" value={share} onChange={(e) => setShare(e.target.value)} required />
            </label>
            <label>
              Reason
              <input value={reason} onChange={(e) => setReason(e.target.value)} />
            </label>
          </DatedChangeForm>
          <NameForm key={platform.name} label="Rename platform" initial={platform.name} submitLabel="Rename" onSubmit={(name) => api(`/api/platforms/${platform.id}`, { method: 'PATCH', body: { name } })} onDone={onChanged} />
        </>
      )}
      <h3>History</h3>
      {history.data && history.data.length === 0 && <p className="muted">No terms yet.</p>}
      {history.data && history.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>From</th>
              <th>To</th>
              <th>GST</th>
              <th className="num">TDS %</th>
              <th className="num">Share %</th>
            </tr>
          </thead>
          <tbody>
            {history.data.map((h) => (
              <tr key={h.id}>
                <td>{formatDate(h.validFrom)}</td>
                <td>{h.validTo ? formatDate(h.validTo) : 'now'}</td>
                <td>{h.gstInclusive ? 'incl.' : 'on top'}</td>
                <td className="num">{h.tdsPercent}</td>
                <td className="num">{h.ourShare}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </SidePanel>
  );
}
