import { useCallback, useMemo, useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import { PARTNER_TYPE_LABEL, PARTNER_TYPES, type Employee, type Partner, type PartnerLink, type PartnerType } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { DatedChangeForm } from '../../components/DatedChangeForm';
import { HistoryTable } from '../../components/HistoryTable';
import { SidePanel } from '../../components/SidePanel';

export function PartnersPage() {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const partners = useLoad<Partner[]>('/api/partners');
  const counts = useLoad<Record<string, number>>('/api/shares/partner-counts');
  const [type, setType] = useState<PartnerType | ''>('');
  const [search, setSearch] = useState('');
  const [panel, setPanel] = useState<'new' | number | null>(null);
  const close = useCallback(() => setPanel(null), []);

  const rows = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (partners.data ?? []).filter((p) => (!type || p.type === type) && (!q || p.name.toLowerCase().includes(q)));
  }, [partners.data, type, search]);
  const selected = typeof panel === 'number' ? partners.data?.find((p) => p.id === panel) : undefined;

  return (
    <section>
      <div className="page-head">
        <h1>Partners</h1>
        {canEdit && (
          <button type="button" onClick={() => setPanel('new')}>
            New partner
          </button>
        )}
      </div>
      <div className="filters">
        <label>
          Type
          <select value={type} onChange={(e) => setType(e.target.value as PartnerType | '')}>
            <option value="">All</option>
            {PARTNER_TYPES.map((t) => (
              <option key={t} value={t}>
                {PARTNER_TYPE_LABEL[t]}
              </option>
            ))}
          </select>
        </label>
        <label>
          Search
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Name" />
        </label>
      </div>
      {partners.error && <p className="error">{partners.error}</p>}
      {partners.data && partners.data.length === 0 && <p className="muted">No partners yet. Add one, or use Import.</p>}
      {partners.data && partners.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Type</th>
              <th>Name</th>
              <th>PAN</th>
              <th>ARN / POSP code</th>
              <th>Clients now</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((p) => (
              <tr key={p.id} className="clickable" onClick={() => setPanel(p.id)}>
                <td>{PARTNER_TYPE_LABEL[p.type]}</td>
                <td>{p.name}</td>
                <td className="mono">{p.pan}</td>
                <td>{p.arnOrPospCode}</td>
                <td className="num">{counts.data?.[p.id] ?? 0}</td>
                <td>{p.status === 'ACTIVE' ? 'Active' : <span className="muted">Inactive</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {panel === 'new' && (
        <SidePanel title="New partner" onClose={close}>
          <PartnerForm
            onDone={() => {
              close();
              partners.reload();
            }}
          />
        </SidePanel>
      )}
      {selected && (
        <SidePanel title={selected.name} onClose={close}>
          <PartnerDetail
            partner={selected}
            others={(partners.data ?? []).filter((p) => p.id !== selected.id && p.type === selected.type && p.status === 'ACTIVE')}
            canEdit={canEdit}
            onChanged={() => {
              partners.reload();
              counts.reload();
            }}
          />
        </SidePanel>
      )}
    </section>
  );
}

/** Create (no partner) or edit. PAN: leave blank to keep the stored one. */
function PartnerForm({ partner, onDone }: { partner?: Partner; onDone: () => void }) {
  const [type, setType] = useState<PartnerType>(partner?.type ?? 'SUB_BROKER');
  const [name, setName] = useState(partner?.name ?? '');
  const [pan, setPan] = useState('');
  const [gstin, setGstin] = useState(partner?.gstin ?? '');
  const [code, setCode] = useState(partner?.arnOrPospCode ?? '');
  const [bankRef, setBankRef] = useState(partner?.bankRef ?? '');
  const [active, setActive] = useState(partner ? partner.status === 'ACTIVE' : true);
  const { busy, error, run } = useSubmit();

  async function submit(e: FormEvent) {
    e.preventDefault();
    const body = {
      name,
      pan: pan.trim() === '' ? undefined : pan,
      gstin,
      arnOrPospCode: code,
      bankRef,
      status: active ? 'ACTIVE' : 'INACTIVE',
    };
    const ok = await run(() =>
      partner
        ? api(`/api/partners/${partner.id}`, { method: 'PATCH', body })
        : api('/api/partners', { method: 'POST', body: { ...body, type } }),
    );
    if (ok) {
      setPan('');
      onDone();
    }
  }

  return (
    <form onSubmit={submit}>
      {!partner && (
        <label>
          Type
          <select value={type} onChange={(e) => setType(e.target.value as PartnerType)}>
            {PARTNER_TYPES.map((t) => (
              <option key={t} value={t}>
                {PARTNER_TYPE_LABEL[t]}
              </option>
            ))}
          </select>
        </label>
      )}
      <label>
        Name
        <input value={name} onChange={(e) => setName(e.target.value)} required />
      </label>
      <label>
        PAN
        <input value={pan} onChange={(e) => setPan(e.target.value)} placeholder={partner?.pan ? `${partner.pan} (leave blank to keep)` : 'ABCDE1234F'} />
      </label>
      <label>
        GSTIN
        <input value={gstin} onChange={(e) => setGstin(e.target.value)} />
      </label>
      <label>
        ARN / POSP code
        <input value={code} onChange={(e) => setCode(e.target.value)} />
      </label>
      <label>
        Bank reference
        <input value={bankRef} onChange={(e) => setBankRef(e.target.value)} />
      </label>
      <label className="check">
        <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
        Active
      </label>
      {error && <p className="error">{error}</p>}
      <button type="submit" disabled={busy}>
        {partner ? 'Save details' : 'Create partner'}
      </button>
    </form>
  );
}

function PartnerDetail({ partner, others, canEdit, onChanged }: { partner: Partner; others: Partner[]; canEdit: boolean; onChanged: () => void }) {
  const links = useLoad<PartnerLink[]>(`/api/partners/${partner.id}/rm-links`);
  const employees = useLoad<Employee[]>(canEdit ? '/api/employees' : null);
  const [rmId, setRmId] = useState('');
  const refresh = () => {
    links.reload();
    onChanged();
  };
  const linkedNow = new Set((links.data ?? []).filter((l) => l.validTo === null).map((l) => l.rmId));
  const rms = (employees.data ?? []).filter((e) => e.type === 'RM' && e.status === 'ACTIVE' && !linkedNow.has(e.id));

  return (
    <>
      {canEdit ? (
        <PartnerForm key={partner.id + partner.name + partner.status} partner={partner} onDone={onChanged} />
      ) : (
        <p>
          {PARTNER_TYPE_LABEL[partner.type]} · PAN {partner.pan ?? 'not set'} · GSTIN {partner.gstin ?? 'not set'}
        </p>
      )}
      <h3>RMs this partner works with</h3>
      {links.error && <p className="error">{links.error}</p>}
      {links.data && (
        <HistoryTable
          label="RM"
          rows={links.data.map((l) => ({ ...l, name: `${l.rmName} (${l.rmCode})` }))}
          actions={
            canEdit
              ? (row) => (row.validTo === null ? <EndLinkButton partnerId={partner.id} linkId={row.id} onDone={refresh} /> : null)
              : undefined
          }
        />
      )}
      {canEdit && partner.status === 'ACTIVE' && (
        <DatedChangeForm
          title="Link an RM"
          dateLabel="From"
          submitLabel="Link RM"
          canSubmit={rmId !== ''}
          onSubmit={(from) => api(`/api/partners/${partner.id}/rm-links`, { method: 'POST', body: { rmId: Number(rmId), from } })}
          onDone={() => {
            setRmId('');
            refresh();
          }}
        >
          <label>
            RM
            <select value={rmId} onChange={(e) => setRmId(e.target.value)} required>
              <option value="">Choose…</option>
              {rms.map((e) => (
                <option key={e.id} value={e.id}>
                  {e.name} ({e.empCode})
                </option>
              ))}
            </select>
          </label>
        </DatedChangeForm>
      )}
      {canEdit && partner.status === 'ACTIVE' && others.length > 0 && <MergeForm partner={partner} others={others} onDone={onChanged} />}
    </>
  );
}

/** A duplicate (another spelling of the same person) hands its clients to the right partner and becomes inactive. */
function MergeForm({ partner, others, onDone }: { partner: Partner; others: Partner[]; onDone: () => void }) {
  const [intoId, setIntoId] = useState('');
  const [result, setResult] = useState<string | null>(null);
  const { busy, error, run } = useSubmit();
  const into = others.find((p) => String(p.id) === intoId);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!into || !window.confirm(`Merge ${partner.name} into ${into.name}? Its clients move to ${into.name} and ${partner.name} becomes inactive.`)) return;
    await run(async () => {
      const r = await api<{ shares: number }>(`/api/partners/${partner.id}/merge`, { method: 'POST', body: { intoId: Number(intoId), reason: 'Duplicate spelling' } });
      setResult(`Merged: ${r.shares} client share(s) moved to ${into.name}.`);
      onDone();
    });
  }

  return (
    <form onSubmit={submit}>
      <h3>Duplicate of another partner?</h3>
      <p className="muted">Merging moves this partner's clients to the right one and remembers this spelling for the next file.</p>
      <label>
        Merge into
        <select value={intoId} onChange={(e) => setIntoId(e.target.value)} required>
          <option value="">Choose…</option>
          {others.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
      </label>
      {error && <p className="error">{error}</p>}
      {result && <p>{result}</p>}
      <button type="submit" className="secondary" disabled={busy || !intoId}>
        Merge
      </button>
    </form>
  );
}

/** Ends a current link, asking for the last day inline. */
function EndLinkButton({ partnerId, linkId, onDone }: { partnerId: number; linkId: number; onDone: () => void }) {
  const [open, setOpen] = useState(false);
  const [lastDay, setLastDay] = useState('');
  const { busy, error, run } = useSubmit();

  if (!open) {
    return (
      <button type="button" className="link" onClick={() => setOpen(true)}>
        End…
      </button>
    );
  }
  return (
    <span className="inline-form">
      <input type="date" value={lastDay} onChange={(e) => setLastDay(e.target.value)} aria-label="Last day" />
      <button
        type="button"
        className="secondary"
        disabled={busy || !lastDay}
        onClick={async () => {
          if (await run(() => api(`/api/partners/${partnerId}/rm-links/${linkId}/end`, { method: 'POST', body: { lastDay } }))) onDone();
        }}
      >
        End
      </button>
      {error && <span className="error">{error}</span>}
    </span>
  );
}
