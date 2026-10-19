import { useCallback, useMemo, useState, type FormEvent } from 'react';
import { api, errorText } from '../../api/client';
import type { Branch, BranchPartner, ChartBranch, ChartPerson, Employee, HistoryEntry, OrgChart, Partner, Region } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { DatedChangeForm } from '../../components/DatedChangeForm';
import { HistoryTable } from '../../components/HistoryTable';
import { NameForm } from '../../components/NameForm';
import { SidePanel } from '../../components/SidePanel';
import { formatDate, todayIso } from '../../format';

/**
 * The org as a tree: Region (RD) → Branch → RMs, in the regions' set order. A search keeps a whole region when the region or
 * its RD matches, otherwise only the branches that match (by name or by one of their RMs).
 */
export function filterOrg(chart: OrgChart, search: string): OrgChart {
  const q = search.trim().toLowerCase();
  if (!q) return chart;
  const has = (v: string | undefined | null) => !!v && v.toLowerCase().includes(q);
  const branchMatches = (b: ChartBranch) => has(b.name) || b.people.some((p) => has(p.name));
  return {
    ...chart,
    regions: chart.regions
      .map((r) => (has(r.name) || has(r.rd?.name) ? r : { ...r, branches: r.branches.filter(branchMatches) }))
      .filter((r) => has(r.name) || has(r.rd?.name) || r.branches.length > 0),
    branchesWithoutRegion: chart.branchesWithoutRegion.filter(branchMatches),
    peopleWithoutBranch: chart.peopleWithoutBranch.filter((p) => has(p.name)),
  };
}

export function isEmptyOrg(chart: OrgChart): boolean {
  return chart.regions.length === 0 && chart.branchesWithoutRegion.length === 0 && chart.peopleWithoutBranch.length === 0;
}

const HOUSE_HELP =
  'A house book holds clients that have no person as their RM, such as HEAD OFFICE or STAFF. It sits in a branch and shows ' +
  'in the reports like an RM, but it cannot be an RD or one of a partner’s RMs. To add a person, use Add RM.';

type Panel =
  | { kind: 'add-region' | 'add-branch' | 'add-rm' | 'add-house' }
  | { kind: 'region' | 'branch' | 'rm'; id: number };

/** The whole org on one screen. Every change is "what, and from when". */
export function OrgPage() {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const [date, setDate] = useState(todayIso());
  const chart = useLoad<OrgChart>(`/api/org?date=${date}`);
  const regions = useLoad<Region[]>('/api/regions');
  const branches = useLoad<Branch[]>('/api/branches');
  const employees = useLoad<Employee[]>('/api/employees');
  const [search, setSearch] = useState('');
  const [panel, setPanel] = useState<Panel | null>(null);
  const close = useCallback(() => setPanel(null), []);
  const reloadAll = () => {
    chart.reload();
    regions.reload();
    branches.reload();
    employees.reload();
  };
  const done = () => {
    close();
    reloadAll();
  };

  const tree = useMemo(() => (chart.data ? filterOrg(chart.data, search) : null), [chart.data, search]);

  const counts = useLoad<Record<string, number>>('/api/clients/count-by-rm');
  const open = (p: Panel) => () => setPanel(p);
  // Which regions are opened up. null = as first shown: only the first region; a search opens them all.
  const [opened, setOpened] = useState<Set<number> | null>(null);
  const isOpen = (id: number, idx: number) => (search.trim() ? true : opened ? opened.has(id) : idx === 0);
  const toggle = (id: number, idx: number) => {
    const next = new Set(opened ?? (tree?.regions[0] ? [tree.regions[0].id] : []));
    if (isOpen(id, idx)) next.delete(id);
    else next.add(id);
    setOpened(next);
  };
  const allOpen = !!tree && tree.regions.every((r, i) => isOpen(r.id, i));


  const rmTable = (list: ChartPerson[]) =>
    list.length === 0 ? (
      <p className="muted org-none">No RM yet.</p>
    ) : (
      <table className="org-rms">
        <thead>
          <tr>
            <th className="org-no">#</th>
            <th>Relationship Manager</th>
            {counts.data && <th className="num">Clients</th>}
          </tr>
        </thead>
        <tbody>
          {list.map((p, i) => (
            <tr key={p.id}>
              <td className="org-no">{i + 1}</td>
              <td>
                <button type="button" className="cell-link" aria-label={p.name} onClick={open({ kind: 'rm', id: p.id })}>
                  {p.name}
                </button>
                {p.type === 'HOUSE' && <span className="badge">house</span>}
              </td>
              {counts.data && <td className="num">{(counts.data[p.id] ?? 0).toLocaleString('en-IN')}</td>}
            </tr>
          ))}
        </tbody>
      </table>
    );
  const branchBlocks = (list: ChartBranch[]) =>
    list.map((b, j) => (
      <div key={b.id} className="org-branch">
        <div className="org-branch-head">
          <span className="org-no">{j + 1}.</span>
          <button type="button" className="cell-link" onClick={open({ kind: 'branch', id: b.id })}>
            {b.name}
          </button>
          <span className="pill">{b.people.length} RM{b.people.length === 1 ? '' : 's'}</span>
        </div>
        {rmTable(b.people)}
      </div>
    ));

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Organisation</h1>
          <p className="muted" style={{ margin: 0 }}>
            Manage regions, branches and relationship managers. Click any name to change it.
          </p>
        </div>
        {canEdit && (
          <div className="actions">
            <button type="button" className="secondary" onClick={open({ kind: 'add-region' })}>
              Add region
            </button>
            <button type="button" className="secondary" onClick={open({ kind: 'add-branch' })}>
              Add branch
            </button>
            <button type="button" className="secondary" onClick={open({ kind: 'add-house' })}>
              Add house book
            </button>
            <button type="button" onClick={open({ kind: 'add-rm' })}>
              Add RM
            </button>
          </div>
        )}
      </div>
      <div className="filters list-filters">
        <label>
          As on
          <input type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} />
        </label>
        <label className="grow">
          Search
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Region, branch or RM…" />
        </label>
        {tree && tree.regions.length > 1 && !search.trim() && (
          <button type="button" className="link" onClick={() => setOpened(allOpen ? new Set() : new Set(tree.regions.map((r) => r.id)))}>
            {allOpen ? 'Collapse all' : 'Expand all'}
          </button>
        )}
      </div>
      {chart.error && <p className="error">{chart.error}</p>}
      {tree && isEmptyOrg(tree) && <div className="empty">{search ? 'Nothing matches.' : 'Nothing set up for this date yet.'}</div>}
      {tree && !isEmptyOrg(tree) && (
        <div className="org-tree">
          {tree.regions.map((r, i) => {
            const shown = isOpen(r.id, i);
            return (
              <article key={r.id} className={shown ? 'org-region open' : 'org-region'}>
                <header className="org-region-head" onClick={() => toggle(r.id, i)}>
                  <button
                    type="button"
                    className="org-toggle"
                    aria-expanded={shown}
                    aria-label={`${shown ? 'Collapse' : 'Expand'} ${r.name}`}
                    onClick={(e) => {
                      e.stopPropagation();
                      toggle(r.id, i);
                    }}
                  >
                    ›
                  </button>
                  <span className="org-no">{i + 1}.</span>
                  <button
                    type="button"
                    className="cell-link org-region-name"
                    onClick={(e) => {
                      e.stopPropagation();
                      setPanel({ kind: 'region', id: r.id });
                    }}
                  >
                    {r.name}
                  </button>
                  <span className={r.rd ? 'muted' : 'badge warn'}>— RD: {r.rd?.name ?? 'none'}</span>
                  <span className="pill">{r.branches.length} branch{r.branches.length === 1 ? '' : 'es'}</span>
                </header>
                {shown && <div className="org-region-body">{r.branches.length === 0 ? <p className="muted org-none">No branch yet.</p> : branchBlocks(r.branches)}</div>}
              </article>
            );
          })}
          {tree.branchesWithoutRegion.length > 0 && (
            <article className="org-region open">
              <header className="org-region-head">
                <span className="muted">Branches not in a region</span>
              </header>
              <div className="org-region-body">{branchBlocks(tree.branchesWithoutRegion)}</div>
            </article>
          )}
          {tree.peopleWithoutBranch.length > 0 && (
            <article className="org-region open">
              <header className="org-region-head">
                <span className="muted">Not in a branch (e.g. RDs only)</span>
              </header>
              <div className="org-region-body">
                <div className="org-branch">{rmTable(tree.peopleWithoutBranch)}</div>
              </div>
            </article>
          )}
        </div>
      )}

      {panel?.kind === 'add-region' && (
        <SidePanel title="Add region" onClose={close}>
          <NameForm label="Region name" submitLabel="Add region" onSubmit={(name) => api('/api/regions', { method: 'POST', body: { name } })} onDone={done} />
        </SidePanel>
      )}
      {panel?.kind === 'add-branch' && (
        <SidePanel title="Add branch" onClose={close}>
          <AddBranchForm regions={regions.data ?? []} onDone={done} />
        </SidePanel>
      )}
      {panel?.kind === 'add-rm' && (
        <SidePanel title="Add RM" onClose={close}>
          <AddRmForm branches={branches.data ?? []} onDone={done} />
        </SidePanel>
      )}
      {panel?.kind === 'add-house' && (
        <SidePanel title="Add house book" onClose={close}>
          <AddRmForm branches={branches.data ?? []} house onDone={done} />
        </SidePanel>
      )}
      {panel?.kind === 'region' && (
        <RegionPanel
          region={regions.data?.find((x) => x.id === panel.id)}
          rms={(employees.data ?? []).filter((e) => e.type === 'RM')}
          canEdit={canEdit}
          onClose={close}
          onChanged={reloadAll}
        />
      )}
      {panel?.kind === 'branch' && (
        <BranchPanel branch={branches.data?.find((x) => x.id === panel.id)} regions={regions.data ?? []} canEdit={canEdit} onClose={close} onChanged={reloadAll} />
      )}
      {panel?.kind === 'rm' && (
        <RmPanel
          rm={employees.data?.find((x) => x.id === panel.id)}
          branches={branches.data ?? []}
          people={employees.data ?? []}
          rdOf={(chart.data?.regions ?? []).filter((r) => r.rd?.id === panel.id).map((r) => r.name)}
          clients={counts.data?.[panel.id]}
          canEdit={canEdit}
          onClose={close}
          onChanged={() => {
            reloadAll();
            counts.reload();
          }}
        />
      )}
    </section>
  );
}

function Select({ label, value, onChange, options }: { label: string; value: string; onChange: (v: string) => void; options: { id: number; name: string }[] }) {
  return (
    <label>
      {label}
      <select value={value} onChange={(e) => onChange(e.target.value)} required>
        <option value="">Choose…</option>
        {options.map((o) => (
          <option key={o.id} value={o.id}>
            {o.name}
          </option>
        ))}
      </select>
    </label>
  );
}

function PastChanges({ path, label }: { path: string; label: string }) {
  const history = useLoad<HistoryEntry[]>(path);
  return (
    <details className="past">
      <summary>Past changes</summary>
      {history.error && <p className="error">{history.error}</p>}
      {history.data && <HistoryTable label={label} rows={history.data.map((h) => ({ ...h, name: h.targetName }))} />}
    </details>
  );
}

function AddBranchForm({ regions, onDone }: { regions: Region[]; onDone: () => void }) {
  const [name, setName] = useState('');
  const [regionId, setRegionId] = useState('');
  const [from, setFrom] = useState(todayIso());
  const { busy, error, run } = useSubmit();
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (await run(() => api('/api/branches', { method: 'POST', body: { name, regionId: Number(regionId), from } }))) onDone();
  }
  return (
    <form onSubmit={submit}>
      <label>
        Branch name
        <input value={name} onChange={(e) => setName(e.target.value)} required />
      </label>
      <Select label="Region" value={regionId} onChange={setRegionId} options={regions} />
      <label>
        From
        <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} required />
      </label>
      {error && <p className="error">{error}</p>}
      <button type="submit" disabled={busy}>
        Add branch
      </button>
    </form>
  );
}

function AddRmForm({ branches, house = false, onDone }: { branches: Branch[]; house?: boolean; onDone: () => void }) {
  const [name, setName] = useState('');
  const [branchId, setBranchId] = useState('');
  const [from, setFrom] = useState(todayIso());
  const { busy, error, run } = useSubmit();
  async function submit(e: FormEvent) {
    e.preventDefault();
    const body = { name, type: house ? 'HOUSE' : 'RM', branchId: Number(branchId), from };
    if (await run(() => api('/api/employees', { method: 'POST', body }))) onDone();
  }
  return (
    <form onSubmit={submit}>
      {house && <p className="muted">{HOUSE_HELP}</p>}
      <label>
        {house ? 'Name (e.g. DIRECT, HO WALK-IN)' : 'RM name (as in the Master MIS)'}
        <input value={name} onChange={(e) => setName(e.target.value)} required />
      </label>
      <Select label="Branch" value={branchId} onChange={setBranchId} options={branches} />
      <label>
        From
        <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} required />
      </label>
      {error && <p className="error">{error}</p>}
      <button type="submit" disabled={busy}>
        {house ? 'Add house book' : 'Add RM'}
      </button>
    </form>
  );
}

function RegionPanel({ region, rms, canEdit, onClose, onChanged }: { region?: Region; rms: Employee[]; canEdit: boolean; onClose: () => void; onChanged: () => void }) {
  const [rdId, setRdId] = useState('');
  if (!region) return null;
  return (
    <SidePanel title={region.name} onClose={onClose}>
      <p>RD now: {region.currentRd?.name ?? <span className="muted">none</span>}</p>
      {canEdit && (
        <>
          <DatedChangeForm
            title={region.currentRd ? 'Change RD' : 'Set RD'}
            dateLabel="From"
            submitLabel="Save"
            canSubmit={rdId !== ''}
            onSubmit={(from) => api(`/api/regions/${region.id}/rd`, { method: 'POST', body: { employeeId: Number(rdId), from } })}
            onDone={() => {
              setRdId('');
              onChanged();
            }}
          >
            <Select label="RD" value={rdId} onChange={setRdId} options={rms.filter((e) => e.id !== region.currentRd?.id)} />
          </DatedChangeForm>
          <NameForm key={region.name} label="Rename region" initial={region.name} submitLabel="Rename" onSubmit={(name) => api(`/api/regions/${region.id}`, { method: 'PATCH', body: { name } })} onDone={onChanged} />
          <RegionLayoutForm key={`${region.id}-${region.position}-${region.endBranchRows}`} region={region} onDone={onChanged} />
        </>
      )}
      <PastChanges path={`/api/regions/${region.id}/rd`} label="RD" />
    </SidePanel>
  );
}

function BranchPanel({ branch, regions, canEdit, onClose, onChanged }: { branch?: Branch; regions: Region[]; canEdit: boolean; onClose: () => void; onChanged: () => void }) {
  const [regionId, setRegionId] = useState('');
  if (!branch) return null;
  return (
    <SidePanel title={branch.name} onClose={onClose}>
      <p>Region now: {branch.regionName ?? <span className="muted">none</span>}</p>
      {canEdit && (
        <>
          <DatedChangeForm
            title="Move to another region"
            dateLabel="From"
            submitLabel="Move"
            canSubmit={regionId !== ''}
            onSubmit={(from) => api(`/api/branches/${branch.id}/region`, { method: 'POST', body: { regionId: Number(regionId), from } })}
            onDone={() => {
              setRegionId('');
              onChanged();
            }}
          >
            <Select label="Region" value={regionId} onChange={setRegionId} options={regions.filter((r) => r.id !== branch.regionId)} />
          </DatedChangeForm>
          <NameForm key={branch.name} label="Rename branch" initial={branch.name} submitLabel="Rename" onSubmit={(name) => api(`/api/branches/${branch.id}`, { method: 'PATCH', body: { name } })} onDone={onChanged} />
        </>
      )}
      <PastChanges path={`/api/branches/${branch.id}/region`} label="Region" />
      <BranchPartnerBlock branchId={branch.id} branchName={branch.name} canEdit={canEdit} />
    </SidePanel>
  );
}

/**
 * A partner for every client of the branch (e.g. one sub-broker for all its clients): paid first, at its rate card;
 * the client's own sub-broker and referral partner share what is left.
 */
function BranchPartnerBlock({ branchId, branchName, canEdit }: { branchId: number; branchName: string; canEdit: boolean }) {
  const history = useLoad<BranchPartner[]>(`/api/branches/${branchId}/partner`);
  const partners = useLoad<Partner[]>('/api/partners');
  const [partnerId, setPartnerId] = useState('');
  const [lastDay, setLastDay] = useState('');
  const { busy, error, run } = useSubmit();
  const now = (history.data ?? []).find((r) => r.validTo === null);
  const options = (partners.data ?? []).filter((p) => p.status !== 'INACTIVE' && p.type !== 'POSP');

  return (
    <div className="card-block">
      <h3>Partner for all clients of {branchName}</h3>
      <p>{now ? `${now.partnerName}, from ${formatDate(now.validFrom)}` : <span className="muted">None</span>}</p>
      <p className="muted">Paid first, at the partner’s product %. The client’s own partners then share what is left.</p>
      {canEdit && (
        <DatedChangeForm
          title={now ? 'Change the partner' : 'Set a partner for all clients'}
          dateLabel="From"
          submitLabel="Save"
          canSubmit={partnerId !== ''}
          onSubmit={(from) => api(`/api/branches/${branchId}/partner`, { method: 'POST', body: { partnerId: Number(partnerId), from } })}
          onDone={() => {
            setPartnerId('');
            history.reload();
          }}
        >
          <Select label="Partner" value={partnerId} onChange={setPartnerId} options={options} />
        </DatedChangeForm>
      )}
      {canEdit && now && (
        <form
          className="inline-form"
          onSubmit={async (e) => {
            e.preventDefault();
            if (await run(() => api(`/api/branches/${branchId}/partner/end`, { method: 'POST', body: { lastDay } }))) history.reload();
          }}
        >
          <span>No branch partner after</span>
          <input type="date" value={lastDay} onChange={(e) => setLastDay(e.target.value)} aria-label="Last day with the branch partner" required />
          <button type="submit" className="secondary" disabled={busy || !lastDay}>
            Remove
          </button>
          {error && <span className="error">{error}</span>}
        </form>
      )}
      {(history.data ?? []).length > 1 && (
        <details>
          <summary>Earlier</summary>
          <ul>
            {(history.data ?? []).filter((r) => r.validTo !== null).map((r) => (
              <li key={r.id}>
                {r.partnerName}: {formatDate(r.validFrom)} – {formatDate(r.validTo)}
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}

function RmPanel({
  rm,
  branches,
  people,
  rdOf,
  clients,
  canEdit,
  onClose,
  onChanged,
}: {
  rm?: Employee;
  branches: Branch[];
  people: Employee[];
  rdOf: string[];
  clients?: number;
  canEdit: boolean;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [branchId, setBranchId] = useState('');
  const [leaving, setLeaving] = useState(false);
  const { error, run } = useSubmit();
  if (!rm) return null;
  if (leaving) {
    return (
      <SidePanel title={`${rm.name} is leaving`} onClose={onClose}>
        <HandoverForm rm={rm} people={people} rdOf={rdOf} clients={clients} onCancel={() => setLeaving(false)} onDone={onChanged} />
      </SidePanel>
    );
  }
  return (
    <SidePanel title={rm.name} onClose={onClose}>
      <p>Branch now: {rm.branchName ?? <span className="muted">none</span>}</p>
      {canEdit && (
        <>
          <DatedChangeForm
            title="Move to another branch"
            dateLabel="From"
            submitLabel="Move"
            canSubmit={branchId !== ''}
            onSubmit={(from) => api(`/api/employees/${rm.id}/branch`, { method: 'POST', body: { branchId: Number(branchId), from } })}
            onDone={() => {
              setBranchId('');
              onChanged();
            }}
          >
            <Select label="Branch" value={branchId} onChange={setBranchId} options={branches.filter((b) => b.id !== rm.branchId)} />
          </DatedChangeForm>
          <NameForm key={rm.name} label="Rename RM" initial={rm.name} submitLabel="Rename" onSubmit={(name) => api(`/api/employees/${rm.id}`, { method: 'PATCH', body: { name } })} onDone={onChanged} />
          <label className="check separated">
            <input
              type="checkbox"
              checked={rm.type === 'HOUSE'}
              onChange={async (e) => {
                if (await run(() => api(`/api/employees/${rm.id}`, { method: 'PATCH', body: { type: e.target.checked ? 'HOUSE' : 'RM' } }))) onChanged();
              }}
            />
            House book: not a person (like HEAD OFFICE, STAFF)
          </label>
          <p className="muted">{HOUSE_HELP}</p>
          {error && <p className="error">{error}</p>}
          {rm.status === 'ACTIVE' && (
            <div className="separated">
              <button type="button" className="secondary" onClick={() => setLeaving(true)}>
                Leaving the company — hand over…
              </button>
            </div>
          )}
        </>
      )}
      <PastChanges path={`/api/employees/${rm.id}/branch`} label="Branch" />
    </SidePanel>
  );
}

/** Where the region sits in lists and in the END region section, and whether the END shows its branches as rows. */
function RegionLayoutForm({ region, onDone }: { region: Region; onDone: () => void }) {
  const [position, setPosition] = useState(String(region.position));
  const [branchRows, setBranchRows] = useState(region.endBranchRows);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await api(`/api/regions/${region.id}`, { method: 'PATCH', body: { position: Number(position), endBranchRows: branchRows } });
      onDone();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };
  return (
    <form onSubmit={submit}>
      <h3>Order and END</h3>
      <label>
        Position (1 = first)
        <input type="number" min={1} value={position} onChange={(e) => setPosition(e.target.value)} required />
      </label>
      <label className="check">
        <input type="checkbox" checked={branchRows} onChange={(e) => setBranchRows(e.target.checked)} />
        Show its branches as rows above it in the END region section
      </label>
      {error && <p className="error">{error}</p>}
      <button type="submit" disabled={busy}>
        Save
      </button>
    </form>
  );
}

type HandoverResult = { leaver: Employee; replacement: Employee; rdRegionIds: number[] };

function nextDay(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number);
  const n = new Date(y, m - 1, d + 1);
  const pad = (v: number) => String(v).padStart(2, '0');
  return `${n.getFullYear()}-${pad(n.getMonth() + 1)}-${pad(n.getDate())}`;
}

/**
 * R-61: the RM leaves on their last working day and, from the next day, the person picked (or a new RM made in the same
 * branch) has all their clients and partners, and their RD regions unless unticked. One step, all or nothing.
 */
function HandoverForm({ rm, people, rdOf, clients, onCancel, onDone }: { rm: Employee; people: Employee[]; rdOf: string[]; clients?: number; onCancel: () => void; onDone: () => void }) {
  const [lastDay, setLastDay] = useState(todayIso());
  const [mode, setMode] = useState<'existing' | 'new'>('new');
  const [replacementId, setReplacementId] = useState('');
  const [newName, setNewName] = useState('');
  const [newCode, setNewCode] = useState('');
  const [takeOverRd, setTakeOverRd] = useState(true);
  const [note, setNote] = useState('');
  const [done, setDone] = useState<HandoverResult | null>(null);
  const { busy, error, run } = useSubmit();
  const others = people.filter((p) => p.id !== rm.id && p.status === 'ACTIVE').sort((a, b) => a.name.localeCompare(b.name));
  const picked = others.find((p) => String(p.id) === replacementId);
  const takerIsHouse = mode === 'existing' && picked?.type === 'HOUSE';
  const from = lastDay ? nextDay(lastDay) : '';
  const ready = !!lastDay && (mode === 'new' ? newName.trim() !== '' : replacementId !== '');

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const reason = note.trim() || undefined;
    const body =
      mode === 'new'
        ? { lastDay, newRmName: newName.trim(), newRmCode: newCode.trim() || undefined, takeOverRd, reason }
        : { lastDay, replacementId: Number(replacementId), takeOverRd: takerIsHouse ? false : takeOverRd, reason };
    const out: { result?: HandoverResult } = {};
    const ok = await run(async () => {
      out.result = await api<HandoverResult>(`/api/employees/${rm.id}/hand-over`, { method: 'POST', body });
    });
    if (ok && out.result) {
      setDone(out.result);
      onDone();
    }
  };

  if (done) {
    return (
      <p role="status" className="success">
        Done. {rm.name}’s last working day is {formatDate(lastDay)}. From {formatDate(from)} <strong>{done.replacement.name}</strong> has their clients
        {done.replacement.type === 'RM' ? ' and partners' : ''}
        {done.rdRegionIds.length > 0 ? ` and is RD of ${rdOf.join(', ')}` : ''}.
      </p>
    );
  }

  return (
    <form className="handover" onSubmit={submit}>
      <label>
        Last working day
        <input type="date" value={lastDay} onChange={(e) => setLastDay(e.target.value)} required />
      </label>

      <fieldset>
        <legend>Who takes over</legend>
        <label className="check">
          <input type="radio" name="taker" checked={mode === 'new'} onChange={() => setMode('new')} />
          A new RM{rm.branchName ? ` (joins ${rm.branchName})` : ''}
        </label>
        <label className="check">
          <input type="radio" name="taker" checked={mode === 'existing'} onChange={() => setMode('existing')} />
          Someone already here
        </label>
      </fieldset>

      {mode === 'new' ? (
        <>
          <label>
            New RM’s name
            <input value={newName} onChange={(e) => setNewName(e.target.value)} required />
          </label>
          <label>
            Emp code (optional — made for you if blank)
            <input value={newCode} onChange={(e) => setNewCode(e.target.value)} />
          </label>
        </>
      ) : (
        <label>
          Takes over
          <select value={replacementId} onChange={(e) => setReplacementId(e.target.value)} required>
            <option value="">Choose…</option>
            {others.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
                {p.branchName ? ` — ${p.branchName}` : ''}
                {p.type === 'HOUSE' ? ' (house book)' : ''}
              </option>
            ))}
          </select>
        </label>
      )}

      <div className="handover-summary">
        <strong>From {from ? formatDate(from) : '…'} they get:</strong>
        <ul>
          <li>
            {clients == null ? 'All' : clients.toLocaleString('en-IN')} client{clients === 1 ? '' : 's'} of {rm.name}
          </li>
          <li>{takerIsHouse ? 'Partners: not moved (a house book has no partners; the links end)' : 'Their partners (sub-brokers, referral partners)'}</li>
          {rdOf.length > 0 && !takerIsHouse && (
            <li>
              <label className="check">
                <input type="checkbox" checked={takeOverRd} onChange={(e) => setTakeOverRd(e.target.checked)} />
                RD of {rdOf.join(', ')}
              </label>
            </li>
          )}
        </ul>
        <p className="muted">Earlier months keep {rm.name}. Everything stays in the history.</p>
      </div>

      <label>
        Note (optional)
        <input value={note} onChange={(e) => setNote(e.target.value)} placeholder="e.g. Resigned" />
      </label>
      {error && <p className="error">{error}</p>}
      <div className="actions">
        <button type="submit" disabled={!ready || busy}>
          {busy ? 'Handing over…' : 'Hand over'}
        </button>
        <button type="button" className="secondary" onClick={onCancel}>
          Back
        </button>
      </div>
    </form>
  );
}
