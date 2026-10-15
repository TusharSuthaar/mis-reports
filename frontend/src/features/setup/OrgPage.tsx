import { useCallback, useMemo, useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { Branch, Employee, HistoryEntry, OrgChart, Region } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { DatedChangeForm } from '../../components/DatedChangeForm';
import { HistoryTable } from '../../components/HistoryTable';
import { NameForm } from '../../components/NameForm';
import { SidePanel } from '../../components/SidePanel';
import { todayIso } from '../../format';

/** One row per RM, shaped like the report: Region | RD | Branch | RM. Empty branches/regions get a row too. */
export interface OrgRow {
  key: string;
  regionId: number | null;
  region: string;
  rd: string;
  branchId: number | null;
  branch: string;
  rmId: number | null;
  rm: string;
  house: boolean;
}

export function orgRows(chart: OrgChart): OrgRow[] {
  const rows: OrgRow[] = [];
  const empty = { branchId: null, branch: '', rmId: null, rm: '', house: false };
  for (const r of chart.regions) {
    const base = { regionId: r.id, region: r.name, rd: r.rd?.name ?? '' };
    if (r.branches.length === 0) rows.push({ key: `r${r.id}`, ...base, ...empty });
    for (const b of r.branches) {
      if (b.people.length === 0) rows.push({ key: `b${b.id}`, ...base, ...empty, branchId: b.id, branch: b.name });
      for (const p of b.people) {
        rows.push({ key: `p${p.id}`, ...base, branchId: b.id, branch: b.name, rmId: p.id, rm: p.name, house: p.type === 'HOUSE' });
      }
    }
  }
  for (const b of chart.branchesWithoutRegion) {
    const base = { regionId: null, region: '', rd: '', branchId: b.id, branch: b.name };
    if (b.people.length === 0) rows.push({ key: `b${b.id}`, ...base, rmId: null, rm: '', house: false });
    for (const p of b.people) rows.push({ key: `p${p.id}`, ...base, rmId: p.id, rm: p.name, house: p.type === 'HOUSE' });
  }
  for (const p of chart.peopleWithoutBranch) {
    rows.push({ key: `p${p.id}`, regionId: null, region: '', rd: '', branchId: null, branch: '', rmId: p.id, rm: p.name, house: p.type === 'HOUSE' });
  }
  return rows;
}

type Panel =
  | { kind: 'add-region' | 'add-branch' | 'add-rm' }
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

  const rows = useMemo(() => {
    const q = search.trim().toLowerCase();
    const all = chart.data ? orgRows(chart.data) : [];
    return q ? all.filter((r) => [r.region, r.rd, r.branch, r.rm].some((v) => v.toLowerCase().includes(q))) : all;
  }, [chart.data, search]);

  const link = (label: string, open: Panel | null) =>
    label && open ? (
      <button type="button" className="cell-link" onClick={() => setPanel(open)}>
        {label}
      </button>
    ) : (
      <span className="muted">—</span>
    );

  return (
    <section>
      <div className="page-head">
        <h1>Org</h1>
        {canEdit && (
          <div className="actions">
            <button type="button" className="secondary" onClick={() => setPanel({ kind: 'add-region' })}>
              Add region
            </button>
            <button type="button" className="secondary" onClick={() => setPanel({ kind: 'add-branch' })}>
              Add branch
            </button>
            <button type="button" onClick={() => setPanel({ kind: 'add-rm' })}>
              Add RM
            </button>
          </div>
        )}
      </div>
      <div className="filters">
        <label>
          As on
          <input type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} />
        </label>
        <label>
          Search
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Region, branch or RM" />
        </label>
      </div>
      {chart.error && <p className="error">{chart.error}</p>}
      {chart.data && rows.length === 0 && <p className="muted">{search ? 'Nothing matches.' : 'Nothing set up for this date yet.'}</p>}
      {rows.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Region</th>
              <th>RD</th>
              <th>Branch</th>
              <th>RM</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.key}>
                <td>{link(r.region, r.regionId === null ? null : { kind: 'region', id: r.regionId })}</td>
                <td>{r.rd || <span className="muted">—</span>}</td>
                <td>{link(r.branch, r.branchId === null ? null : { kind: 'branch', id: r.branchId })}</td>
                <td>
                  {link(r.rm, r.rmId === null ? null : { kind: 'rm', id: r.rmId })}
                  {r.house && <span className="badge">house</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
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
        <RmPanel rm={employees.data?.find((x) => x.id === panel.id)} branches={branches.data ?? []} canEdit={canEdit} onClose={close} onChanged={reloadAll} />
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

function AddRmForm({ branches, onDone }: { branches: Branch[]; onDone: () => void }) {
  const [name, setName] = useState('');
  const [branchId, setBranchId] = useState('');
  const [from, setFrom] = useState(todayIso());
  const [house, setHouse] = useState(false);
  const { busy, error, run } = useSubmit();
  async function submit(e: FormEvent) {
    e.preventDefault();
    const body = { name, type: house ? 'HOUSE' : 'RM', branchId: Number(branchId), from };
    if (await run(() => api('/api/employees', { method: 'POST', body }))) onDone();
  }
  return (
    <form onSubmit={submit}>
      <label>
        RM name (as in the Investwell file)
        <input value={name} onChange={(e) => setName(e.target.value)} required />
      </label>
      <Select label="Branch" value={branchId} onChange={setBranchId} options={branches} />
      <label>
        From
        <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} required />
      </label>
      <label className="check">
        <input type="checkbox" checked={house} onChange={(e) => setHouse(e.target.checked)} />
        House book (HEAD OFFICE, STAFF): no RM share
      </label>
      {error && <p className="error">{error}</p>}
      <button type="submit" disabled={busy}>
        Add RM
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
    </SidePanel>
  );
}

function RmPanel({ rm, branches, canEdit, onClose, onChanged }: { rm?: Employee; branches: Branch[]; canEdit: boolean; onClose: () => void; onChanged: () => void }) {
  const [branchId, setBranchId] = useState('');
  const { error, run } = useSubmit();
  if (!rm) return null;
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
            House book (HEAD OFFICE, STAFF): no RM share
          </label>
          {error && <p className="error">{error}</p>}
        </>
      )}
      <PastChanges path={`/api/employees/${rm.id}/branch`} label="Branch" />
    </SidePanel>
  );
}
