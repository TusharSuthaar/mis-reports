import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { ClientRow, Employee, InsuranceBusinessType, InsuranceCategory, InsurancePolicy, Page, Partner } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { Amount } from '../../components/Amount';
import { ListFilters, useFilterQuery } from '../../components/ListFilters';
import { Modal } from '../../components/Modal';
import { useEntryPanel } from '../../components/useEntryPanel';
import { useTableView } from '../../components/useTableView';
import { formatMoney, todayIso } from '../../format';
import { formatMonth, indianDigits } from '../fd/FdPage';

const round2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100;

/**
 * The commissions as the server works them out (R-95): expected = net × expected %; the POSP's % = expected % × agreed %;
 * the POSP's commission = net × the POSP's %. A typed amount stands instead of either calculation.
 */
export function insuranceFigures(net: number, expectedPct: number | null, expectedTyped: number | null, hasPosp: boolean, agreedPct: number | null, pospTyped: number | null) {
  const expected = expectedTyped != null ? round2(expectedTyped) : round2((net * (expectedPct ?? 0)) / 100);
  const pospPct = hasPosp && agreedPct != null && expectedPct != null ? (expectedPct * agreedPct) / 100 : null;
  const posp = !hasPosp ? 0 : pospTyped != null ? round2(pospTyped) : pospPct != null ? round2((net * pospPct) / 100) : agreedPct != null ? round2((expected * agreedPct) / 100) : 0;
  return { expected, pospPct, posp, ours: round2(expected - posp) };
}

export const CATEGORY_LABEL: Record<InsuranceCategory, string> = { LIFE: 'Life', HEALTH: 'Health', MOTOR: 'Motor', GENERAL: 'General' };
const TYPE_LABEL: Record<InsuranceBusinessType, string> = { NEW: 'New', RENEWAL: 'Renewal', PORT: 'Port', ENDORSEMENT: 'Endorsement', FYCI: 'FYCI' };
const PAN_FORMAT = /^[A-Z]{5}[0-9]{4}[A-Z]$/;
const OTHER = '__other__';
type MotorCover = 'COMP' | 'OD' | 'TP';

/** What each kind of insurance has and needs (R-96), as the server sends it. */
interface CategoryRule {
  category: InsuranceCategory;
  label: string;
  subTypeLabel: string;
  subTypes: string[];
  businessTypes: InsuranceBusinessType[];
  sumLabel: string;
  sumRequired: boolean;
  pptLabel: string;
  pptChoices: string[];
  termRequired: boolean;
  premium: 'OD_TP' | 'TOTAL' | 'TOTAL_LESS_GST';
  gstPercent: number;
}
const thisMonth = () => todayIso().slice(0, 7);

/** Insurance: one row per policy, as in the insurance team's policy sheet (R-94), typed here or uploaded for the month. */
export function InsurancePage({ embedded = false }: { embedded?: boolean } = {}) {
  const { user } = useAuth();
  const canEdit = canEditMasters(user?.role);
  const query = useFilterQuery();
  const list = useLoad<InsurancePolicy[]>(`/api/insurance${query}`);
  const choices = useLoad<{ insurers: string[]; products: string[]; subTypes: string[] }>('/api/insurance/choices');
  const [panel, setPanel] = useEntryPanel();
  const [uploading, setUploading] = useState(false);
  const editing = typeof panel === 'number' ? list.data?.find((e) => e.id === panel) : undefined;
  const active = (list.data ?? []).filter((e) => e.status === 'ACTIVE');
  const done = () => {
    setPanel(null);
    setUploading(false);
    list.reload();
    choices.reload();
  };
  const view = useTableView(list.data, {
    month: (e) => e.month, client: (e) => e.clientName, rm: (e) => e.rmName, insurer: (e) => e.insurer, product: (e) => e.productName,
    category: (e) => e.category, type: (e) => e.businessType, net: (e) => e.netPremium, pct: (e) => e.expectedPercent, expected: (e) => e.expectedComm,
    posp: (e) => e.pospName, posp_comm: (e) => e.pospComm,
  });
  return (
    <section>
      <div className="page-head" style={embedded ? { justifyContent: 'flex-end', marginBottom: 12 } : undefined}>
        {!embedded && <h1>Insurance</h1>}
        {canEdit && (
          <div className="actions">
            <a className="button-link secondary" href={`/api/insurance/export.xlsx${query}`} download>
              Download Excel
            </a>
            <button type="button" className="secondary" onClick={() => setUploading(true)}>
              Upload policy sheet
            </button>
            <button type="button" onClick={() => setPanel('new')}>
              Add policy
            </button>
          </div>
        )}
      </div>
      <p className="muted">
        Each policy counts in its business month: the END shows the expected commission in the Insurance column, and the POSP's commission as POSP Sharing.
        The client is found by PAN and the RM always comes from the client master.
      </p>
      <ListFilters
        searchHint="Client, PAN, code, policy no., product or POSP"
        platformKey="platform"
        platformLabel="Insurer"
        platforms={(choices.data?.insurers ?? []).map((p) => ({ value: p, label: p }))}
        partnerFilter={false}
        entryFilters={false}
      />
      {list.error && <p className="error">{list.error}</p>}
      {list.data && list.data.length === 0 && <p className="muted">No policies match.</p>}
      {list.data && list.data.length > 0 && (
        <>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  {view.th('month', 'Month')}
                  {view.th('client', 'Client')}
                  {view.th('rm', 'RM')}
                  {view.th('insurer', 'Insurer')}
                  {view.th('product', 'Product')}
                  {view.th('category', 'CAT')}
                  {view.th('type', 'Business type')}
                  <th>Policy no.</th>
                  {view.th('net', 'Net premium', 'num')}
                  {view.th('pct', 'Exp. %', 'num')}
                  {view.th('expected', 'Expected comm.', 'num')}
                  {view.th('posp', 'POSP')}
                  {view.th('posp_comm', 'POSP comm.', 'num')}
                </tr>
              </thead>
              <tbody>
                {view.rows.map((e) => (
                  <tr key={e.id} className={e.status === 'CANCELLED' ? 'muted' : undefined}>
                    <td>{formatMonth(e.month)}</td>
                    <td>
                      <button type="button" className="cell-link" onClick={() => setPanel(e.id)}>
                        {e.clientName}
                      </button>
                      {e.status === 'CANCELLED' && <span className="badge">cancelled</span>}
                    </td>
                    <td>{e.rmName}</td>
                    <td>{e.insurer}</td>
                    <td>{e.productName}</td>
                    <td>{CATEGORY_LABEL[e.category]}</td>
                    <td>{TYPE_LABEL[e.businessType]}</td>
                    <td className="mono">{e.policyNo}</td>
                    <td className="num">
                      <Amount value={e.netPremium} />
                    </td>
                    <td className="num">{e.expectedPercent != null ? `${e.expectedPercent}%` : ''}</td>
                    <td className="num">
                      {formatMoney(e.expectedComm)}
                      {e.expectedAsGiven && <span className="muted" title="Typed amount, not net × %"> *</span>}
                    </td>
                    <td>{e.pospName ?? <span className="muted">—</span>}</td>
                    <td className="num">{e.pospComm ? formatMoney(e.pospComm) : ''}</td>
                  </tr>
                ))}
                <tr className="total">
                  <td colSpan={8}>Total ({active.length} policies)</td>
                  <td className="num">
                    <Amount value={active.reduce((s, e) => s + e.netPremium, 0)} />
                  </td>
                  <td />
                  <td className="num">
                    <Amount value={active.reduce((s, e) => s + e.expectedComm, 0)} />
                  </td>
                  <td />
                  <td className="num">
                    <Amount value={active.reduce((s, e) => s + e.pospComm, 0)} />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          {view.more}
        </>
      )}
      {panel === 'new' && (
        <Modal title="Add insurance policy" onClose={() => setPanel(null)}>
          <PolicyForm onDone={done} choices={choices.data ?? undefined} />
        </Modal>
      )}
      {editing && (
        <Modal title={`${editing.clientName} — ${editing.insurer} ${editing.policyNo}`} onClose={() => setPanel(null)}>
          {canEdit && editing.status === 'ACTIVE' ? (
            <PolicyForm policy={editing} onDone={done} choices={choices.data ?? undefined} />
          ) : (
            <dl className="facts">
              <dt>Product</dt>
              <dd>{editing.productName}</dd>
              <dt>Expected commission</dt>
              <dd>{formatMoney(editing.expectedComm)}</dd>
            </dl>
          )}
        </Modal>
      )}
      {uploading && (
        <Modal title="Upload the month's policy sheet" onClose={() => setUploading(false)}>
          <UploadPolicies onDone={done} />
        </Modal>
      )}
    </section>
  );
}

/**
 * Add or edit one policy, in short blocks: who (client found by PAN / code / name, shown with its RM before saving), the
 * policy, the chosen kind of insurance's own fields (R-96), and the money. The expected % and the POSP's agreed % fill in from earlier policies; the rest is worked out.
 */
function PolicyForm({ policy, onDone, choices }: { policy?: InsurancePolicy; onDone: () => void; choices?: { insurers: string[]; products: string[]; subTypes: string[] } }) {
  const num = (s: string) => Number(s.replace(/,/g, '')) || 0;
  const str = (n: number | null | undefined) => (n == null ? '' : String(n));
  // Who
  const [lookup, setLookup] = useState('');
  const [looked, setLooked] = useState('');
  const [client, setClient] = useState<ClientRow | null>(null);
  const [changeClient, setChangeClient] = useState(false);
  const [newName, setNewName] = useState('');
  const [newPan, setNewPan] = useState('');
  const [rmId, setRmId] = useState('');
  // Policy
  const [month, setMonth] = useState(policy?.month ?? thisMonth());
  const [category, setCategory] = useState<InsuranceCategory | ''>(policy?.category ?? '');
  const [insurer, setInsurer] = useState(policy?.insurer ?? '');
  const [productName, setProductName] = useState(policy?.productName ?? '');
  const [policyNo, setPolicyNo] = useState(policy?.policyNo ?? '');
  const [businessType, setBusinessType] = useState<InsuranceBusinessType | ''>(policy?.businessType ?? '');
  const [subType, setSubType] = useState(policy?.subType ?? '');
  const [sumAssured, setSumAssured] = useState(policy?.sumAssured != null ? indianDigits(String(policy.sumAssured)) : '');
  const [ppt, setPpt] = useState(policy?.ppt ?? '');
  const [policyTerm, setPolicyTerm] = useState(str(policy?.policyTerm));
  const [od, setOd] = useState(policy?.odPremium != null ? indianDigits(String(policy.odPremium)) : '');
  const [tp, setTp] = useState(policy?.tpPremium != null ? indianDigits(String(policy.tpPremium)) : '');
  // Money
  const [total, setTotal] = useState(policy ? indianDigits(String(policy.totalPremium)) : '');
  const [net, setNet] = useState(policy ? indianDigits(String(policy.netPremium)) : '');
  const [expectedPct, setExpectedPct] = useState(str(policy?.expectedPercent));
  const [expectedTyped, setExpectedTyped] = useState(policy?.expectedAsGiven ? str(policy.expectedComm) : '');
  const [typeExpected, setTypeExpected] = useState(!!policy?.expectedAsGiven);
  const [posp, setPosp] = useState(policy?.pospName ?? '');
  const [agreedPct, setAgreedPct] = useState(str(policy?.pospShare));
  const [pospTyped, setPospTyped] = useState(policy?.pospAsGiven ? str(policy.pospComm) : '');
  const [typePosp, setTypePosp] = useState(!!policy?.pospAsGiven);
  const [actualPct, setActualPct] = useState(str(policy?.actualPercent));
  const [remarks, setRemarks] = useState(policy?.remarks ?? '');
  const [netEdited, setNetEdited] = useState(!!policy);
  const [cover, setCover] = useState<MotorCover>(policy?.odPremium && !policy.tpPremium ? 'OD' : !policy?.odPremium && policy?.tpPremium ? 'TP' : 'COMP');
  const { busy, error, run } = useSubmit();

  const cats = useLoad<CategoryRule[]>('/api/insurance/categories');
  const rule = cats.data?.find((r) => r.category === category);
  // A sub type not in the list (typed, or from the sheet) shows as "Other…" with its text.
  const subTypeOptions = rule?.subTypes ?? [];
  const showOd = cover !== 'TP';
  const showTp = cover !== 'OD';
  // The net premium follows the category (R-96) until it is typed over: Motor = OD + TP, Life / Health = total, General = total less GST.
  const autoNet = !rule ? null
    : rule.premium === 'OD_TP' ? (od || tp ? round2((showOd ? num(od) : 0) + (showTp ? num(tp) : 0)) : null)
    : total === '' ? null
    : rule.premium === 'TOTAL' ? num(total) : round2((num(total) * 100) / (100 + rule.gstPercent));
  const netValue = netEdited || autoNet == null ? net : indianDigits(String(autoNet));
  // A new category: its first policy period, and no business type it does not have.
  useEffect(() => {
    if (!rule) return;
    if (businessType && !rule.businessTypes.includes(businessType)) setBusinessType('');
    if (!ppt && rule.pptChoices.length) setPpt(rule.pptChoices[0]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rule?.category]);

  const lookingUp = !policy || changeClient;
  const employees = useLoad<Employee[]>('/api/employees');
  const partners = useLoad<Partner[]>('/api/partners');
  const found = useLoad<Page<ClientRow>>(lookingUp && looked ? `/api/clients?q=${encodeURIComponent(looked)}&size=20` : null);
  // A PAN or client code must match exactly; anything else is a name: every client whose name has it, to pick from.
  const matches = useMemo(() => {
    if (!looked || !found.data) return null;
    const q = looked.toUpperCase();
    if (PAN_FORMAT.test(q)) return found.data.items.filter((c) => c.pan != null && (c.pan.toUpperCase() === q || (c.pan.includes('*') && c.pan.slice(0, 5) === q.slice(0, 5) && c.pan.slice(-1) === q.slice(-1))));
    const byCode = found.data.items.filter((c) => c.iwellCode != null && c.iwellCode.toUpperCase() === q);
    return byCode.length ? byCode : found.data.items;
  }, [found.data, looked]);
  const clientMode: 'lookup' | 'found' | 'pick' | 'new' = !lookingUp ? 'found' : !matches ? 'lookup' : matches.length === 0 ? 'new' : client ? 'found' : 'pick';
  const posps = (partners.data ?? []).filter((p) => p.type === 'POSP');
  const pospMatch = posps.find((p) => p.name.trim().toLowerCase() === posp.trim().toLowerCase());
  const noPosp = posp.trim() === '' || ['NA', 'N/A', 'NONE', '-'].includes(posp.trim().toUpperCase());
  const needsRm = clientMode === 'new' || (clientMode === 'found' && lookingUp && client != null && !client.rmId);
  const defaults = useLoad<{ category: InsuranceCategory | null; subType: string | null; expectedPercent: number | null; pospShare: number | null }>(
    !policy && (insurer && productName || pospMatch)
      ? `/api/insurance/defaults?insurer=${encodeURIComponent(insurer)}&productName=${encodeURIComponent(productName)}${pospMatch ? `&pospId=${pospMatch.id}` : ''}`
      : null,
  );

  useEffect(() => {
    if (matches && matches.length === 1) setClient(matches[0]);
    if (matches && matches.length === 0 && PAN_FORMAT.test(looked)) setNewPan(looked);
  }, [matches, looked]);
  // Fill in from earlier policies only what is still empty.
  useEffect(() => {
    const d = defaults.data;
    if (!d) return;
    if (d.category && !category) setCategory(d.category);
    if (d.subType && !subType) setSubType(d.subType);
    if (d.expectedPercent != null && expectedPct === '') setExpectedPct(String(d.expectedPercent));
    if (d.pospShare != null && agreedPct === '') setAgreedPct(String(d.pospShare));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [defaults.data]);

  const findClient = () => {
    setClient(null);
    setLooked(lookup.trim().toUpperCase());
  };
  const fig = insuranceFigures(num(netValue), expectedPct === '' ? null : num(expectedPct), typeExpected && expectedTyped !== '' ? num(expectedTyped) : null,
    !noPosp, agreedPct === '' ? null : num(agreedPct), typePosp && pospTyped !== '' ? num(pospTyped) : null);
  const people = (employees.data ?? []).filter((e) => e.status === 'ACTIVE');

  async function submit(e: FormEvent) {
    e.preventDefault();
    const body = {
      clientId: policy && !changeClient ? policy.clientId : clientMode === 'found' ? client?.id : null,
      newClientName: clientMode === 'new' ? newName : null,
      newClientPan: clientMode === 'new' && newPan ? newPan : null,
      rmId: needsRm && rmId ? Number(rmId) : null,
      month: `${month.slice(0, 7)}-01`,
      pospId: noPosp ? null : pospMatch?.id ?? null,
      newPospName: noPosp || pospMatch ? null : posp.trim(),
      insurer,
      category: category || null,
      subType: subType.trim(),
      productName,
      policyNo,
      businessType: businessType || null,
      ppt,
      policyTerm: category === 'LIFE' && policyTerm !== '' ? num(policyTerm) : null,
      sumAssured: sumAssured === '' ? null : num(sumAssured),
      odPremium: category === 'MOTOR' && showOd && od !== '' ? num(od) : null,
      tpPremium: category === 'MOTOR' && showTp && tp !== '' ? num(tp) : null,
      totalPremium: total === '' ? null : num(total),
      netPremium: netValue === '' ? null : num(netValue),
      expectedPercent: expectedPct === '' ? null : num(expectedPct),
      expectedComm: typeExpected && expectedTyped !== '' ? num(expectedTyped) : null,
      actualPercent: actualPct === '' ? null : num(actualPct),
      actualComm: null,
      pospShare: noPosp || agreedPct === '' ? null : num(agreedPct),
      pospComm: !noPosp && typePosp && pospTyped !== '' ? num(pospTyped) : null,
      remarks,
    };
    const ok = await run(() => (policy ? api(`/api/insurance/${policy.id}`, { method: 'PUT', body }) : api('/api/insurance', { method: 'POST', body })));
    if (ok) onDone();
  }

  return (
    <form onSubmit={submit} className="fd-form">
      <fieldset>
        <legend>1 · Client</legend>
        {!lookingUp && policy ? (
          <p className="ok">
            {policy.clientName} · <span className="mono">{policy.pan ?? 'no PAN'}</span> · {policy.clientCode} · RM {policy.rmName}
            {policy.branchName ? ` · ${policy.branchName}` : ''}{' '}
            <button type="button" className="link" onClick={() => setChangeClient(true)}>
              Wrong client?
            </button>
          </p>
        ) : (
          <span className="inline-form">
            <label>
              PAN, client code or name
              <input
                value={lookup}
                onChange={(e) => setLookup(e.target.value.toUpperCase())}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    findClient();
                  }
                }}
                placeholder="ABCDE1234F, FC01234 or a name"
                autoFocus
              />
            </label>
            <button type="button" className="secondary" disabled={lookup.trim().length < 3} onClick={findClient}>
              Find
            </button>
          </span>
        )}
        {clientMode === 'pick' && (
          <>
            <p className="muted">Pick the client:</p>
            {(matches ?? []).map((c) => (
              <label key={c.id} className="check">
                <input type="radio" name="ins-client" checked={client?.id === c.id} onChange={() => setClient(c)} />
                {c.name} · <span className="mono">{c.pan ?? 'no PAN'}</span> · {c.iwellCode ?? 'OFFLINE'} · {c.rmName ?? 'no RM'}
              </label>
            ))}
          </>
        )}
        {lookingUp && client && (
          <p className="ok">
            ✓ {client.name} · <span className="mono">{client.pan ?? 'no PAN'}</span> · {client.iwellCode ?? 'OFFLINE'} ·{' '}
            {client.rmId ? `RM ${client.rmName}${client.branchName ? ` · ${client.branchName}` : ''}` : 'no RM yet'}
          </p>
        )}
        {clientMode === 'new' && (
          <>
            <p className="muted">No client has this PAN or code. Check it, or add the client here: they are added to Clients with the RM below.</p>
            <label>
              Client name
              <input value={newName} onChange={(e) => setNewName(e.target.value)} required />
            </label>
            <label>
              PAN
              <input value={newPan} onChange={(e) => setNewPan(e.target.value.toUpperCase().replace(/\s/g, ''))} maxLength={10} placeholder="ABCDE1234F" />
            </label>
          </>
        )}
        {needsRm && (
          <label>
            RM (saved on the client)
            <select value={rmId} onChange={(e) => setRmId(e.target.value)} required>
              <option value="">Choose…</option>
              {people.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.branchName ? `${p.name} — ${p.branchName}` : p.name}
                </option>
              ))}
            </select>
          </label>
        )}
      </fieldset>

      <fieldset>
        <legend>2 · Policy</legend>
        <div className="chip-row" style={{ gridColumn: '1 / -1' }}>
          <span className="muted">Type of insurance</span>
          <span className="segmented" style={{ marginBottom: 0 }}>
            {(Object.keys(CATEGORY_LABEL) as InsuranceCategory[]).map((c) => (
              <button
                key={c}
                type="button"
                className={category === c ? 'on' : undefined}
                onClick={() => {
                  if (c === category) return;
                  // Another kind of insurance: its own sub types and policy periods.
                  setCategory(c);
                  setSubType('');
                  setPpt('');
                }}
              >
                {CATEGORY_LABEL[c]}
              </button>
            ))}
          </span>
          {rule && (
            <>
              <span className="muted">Business type</span>
              <span className="segmented" style={{ marginBottom: 0 }}>
                {rule.businessTypes.map((t) => (
                  <button key={t} type="button" className={businessType === t ? 'on' : undefined} onClick={() => setBusinessType(t)}>
                    {TYPE_LABEL[t]}
                  </button>
                ))}
              </span>
            </>
          )}
        </div>
        <label>
          Business month
          <input type="month" value={month.slice(0, 7)} onChange={(e) => e.target.value && setMonth(e.target.value)} required />
        </label>
        <label>
          Insurance company
          <input value={insurer} onChange={(e) => setInsurer(e.target.value)} list="ins-insurers" required />
        </label>
        <label>
          Product name
          <input value={productName} onChange={(e) => setProductName(e.target.value)} list="ins-products" required />
        </label>
        <label>
          Policy no.
          <input value={policyNo} onChange={(e) => setPolicyNo(e.target.value)} required />
        </label>
        <datalist id="ins-insurers">{(choices?.insurers ?? []).map((n) => <option key={n} value={n} />)}</datalist>
        <datalist id="ins-products">{(choices?.products ?? []).map((n) => <option key={n} value={n} />)}</datalist>
      </fieldset>

      {rule && (
        <fieldset>
          <legend>3 · {rule.label} details</legend>
          <label>
            {rule.subTypeLabel}
            <select
              value={subTypeOptions.includes(subType) ? subType : subType ? OTHER : ''}
              onChange={(e) => setSubType(e.target.value === OTHER ? ' ' : e.target.value)}
              required={!policy || policy.source === 'SCREEN'}
            >
              <option value="">Choose…</option>
              {subTypeOptions.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
              <option value={OTHER}>Other…</option>
            </select>
          </label>
          {subType !== '' && !subTypeOptions.includes(subType) && (
            <label>
              {rule.subTypeLabel} (other)
              <input value={subType.trim()} onChange={(e) => setSubType(e.target.value || ' ')} required autoFocus />
            </label>
          )}
          {category === 'MOTOR' && (
            <label>
              Cover
              <select value={cover} onChange={(e) => setCover(e.target.value as MotorCover)}>
                <option value="COMP">Comprehensive (OD + TP)</option>
                <option value="OD">Own damage only</option>
                <option value="TP">Third party only</option>
              </select>
            </label>
          )}
          <label>
            {rule.sumLabel} (₹){rule.sumRequired ? '' : ' — optional'}
            <input value={sumAssured} onChange={(e) => setSumAssured(indianDigits(e.target.value))} inputMode="decimal" required={rule.sumRequired} />
          </label>
          <label>
            {rule.pptLabel}
            {rule.pptChoices.length ? (
              <select value={ppt} onChange={(e) => setPpt(e.target.value)} required>
                {[...rule.pptChoices, ...(ppt && !rule.pptChoices.includes(ppt) ? [ppt] : [])].map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            ) : (
              <input value={ppt} onChange={(e) => setPpt(e.target.value)} inputMode="numeric" placeholder="e.g. 10" required />
            )}
          </label>
          {category === 'LIFE' && (
            <label>
              Policy term (years)
              <input type="number" min={1} value={policyTerm} onChange={(e) => setPolicyTerm(e.target.value)} required />
            </label>
          )}
          {category === 'MOTOR' && showOd && (
            <label>
              OD premium (₹)
              <input value={od} onChange={(e) => setOd(indianDigits(e.target.value))} inputMode="decimal" required />
            </label>
          )}
          {category === 'MOTOR' && showTp && (
            <label>
              TP premium (₹)
              <input value={tp} onChange={(e) => setTp(indianDigits(e.target.value))} inputMode="decimal" required />
            </label>
          )}
        </fieldset>
      )}

      <fieldset>
        <legend>{rule ? '4' : '3'} · Premium and commission</legend>
        <label>
          Total premium (₹, with GST)
          <input value={total} onChange={(e) => setTotal(indianDigits(e.target.value))} inputMode="decimal" required />
        </label>
        <label>
          Net premium (₹, without GST)
          <input
            value={netValue}
            onChange={(e) => {
              setNetEdited(true);
              setNet(indianDigits(e.target.value));
            }}
            inputMode="decimal"
            required
          />
        </label>
        {rule && !netEdited && autoNet != null && (
          <p className="muted">
            {rule.premium === 'OD_TP' ? 'Net = OD + TP premium.' : rule.premium === 'TOTAL' ? `Net = total (no GST on ${rule.label.toLowerCase()}).` : `Net = total less ${rule.gstPercent}% GST.`} Type over it if the
            insurer's figure differs.
          </p>
        )}
        <label>
          Expected commission %
          <input type="number" min={0} max={100} step="0.0001" value={expectedPct} onChange={(e) => setExpectedPct(e.target.value)} required={!typeExpected} />
        </label>
        {typeExpected && (
          <label>
            Expected commission (₹, typed)
            <input type="number" step="0.01" value={expectedTyped} onChange={(e) => setExpectedTyped(e.target.value)} required />
          </label>
        )}
        <label>
          POSP (blank or NA = none)
          <input value={posp} onChange={(e) => setPosp(e.target.value)} list="ins-posps" placeholder="Pick or type a new POSP" />
        </label>
        <datalist id="ins-posps">{posps.map((p) => <option key={p.id} value={p.name} />)}</datalist>
        {!noPosp && (
          <label>
            % agreed with the POSP
            <input type="number" min={0} max={100} step="0.01" value={agreedPct} onChange={(e) => setAgreedPct(e.target.value)} required={!typePosp} />
          </label>
        )}
        {!noPosp && typePosp && (
          <label>
            POSP commission (₹, typed)
            <input type="number" step="0.01" value={pospTyped} onChange={(e) => setPospTyped(e.target.value)} required />
          </label>
        )}
        {!noPosp && fig.posp > fig.expected && <p className="error">The POSP's commission is more than the expected commission: check the amounts (it is saved as typed).</p>}
        {!noPosp && !pospMatch && <p className="muted">New POSP: “{posp.trim()}” is added as a POSP partner when you save.</p>}
        <div className="figures">
          <div className="figure key">
            <span>Expected commission{typeExpected ? ' (typed)' : ''}</span>
            <b>{formatMoney(fig.expected)}</b>
          </div>
          {!noPosp && (
            <div className="figure">
              <span>POSP % of premium</span>
              <b>{fig.pospPct != null ? `${round2(fig.pospPct * 10000) / 10000}%` : '—'}</b>
            </div>
          )}
          {!noPosp && (
            <div className="figure">
              <span>POSP commission{typePosp ? ' (typed)' : ''}</span>
              <b>{formatMoney(fig.posp)}</b>
            </div>
          )}
          <div className="figure">
            <span>Epsilon keeps</span>
            <b>{formatMoney(fig.ours)}</b>
          </div>
        </div>
        <p className="muted">
          <button type="button" className="link" onClick={() => setTypeExpected(!typeExpected)}>
            {typeExpected ? 'Work out the expected commission from the %' : 'Type the expected commission amount instead'}
          </button>
          {!noPosp && (
            <>
              {' · '}
              <button type="button" className="link" onClick={() => setTypePosp(!typePosp)}>
                {typePosp ? "Work out the POSP's commission" : "Type the POSP's commission instead"}
              </button>
            </>
          )}
        </p>
        <details style={{ gridColumn: '1 / -1' }} open={!!(policy?.actualPercent != null || policy?.remarks)}>
          <summary className="muted">Actual commission and remarks (optional, can be added later)</summary>
          <div className="inline-form" style={{ flexWrap: 'wrap', marginTop: 8 }}>
            <label>
              Actual commission %
              <input type="number" min={0} max={100} step="0.0001" value={actualPct} onChange={(e) => setActualPct(e.target.value)} />
            </label>
            <label style={{ flex: 1 }}>
              Remarks
              <input value={remarks} onChange={(e) => setRemarks(e.target.value)} />
            </label>
          </div>
        </details>
      </fieldset>

      {error && <p className="error">{error}</p>}
      <span className="inline-form">
        <button type="submit" disabled={busy || clientMode === 'lookup' || clientMode === 'pick' || !category || !businessType}>
          {policy ? 'Save changes' : 'Add policy'}
        </button>
        {(!category || !businessType) && <span className="muted">Choose the category and business type.</span>}
        {policy && <CancelPolicy id={policy.id} onDone={onDone} />}
      </span>
    </form>
  );
}

function CancelPolicy({ id, onDone }: { id: number; onDone: () => void }) {
  const { busy, run } = useSubmit();
  return (
    <button
      type="button"
      className="link"
      disabled={busy}
      onClick={async () => {
        const reason = window.prompt('Why cancel this policy?');
        if (reason === null) return;
        if (await run(() => api(`/api/insurance/${id}/cancel`, { method: 'POST', body: { reason } }))) onDone();
      }}
    >
      Cancel this policy
    </button>
  );
}

interface UploadResult {
  month: string;
  rows: number;
  saved: number;
  replaced: number;
  newPosps: number;
  expectedTotal: number;
  pospTotal: number;
  notSaved: { row: number; client: string | null; pan: string | null; reason: string }[];
}

/** The month's "Policy level data" sheet: rows are mapped to clients by PAN; those that cannot be are listed by row. */
function UploadPolicies({ onDone }: { onDone: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [month, setMonth] = useState(thisMonth());
  const [result, setResult] = useState<UploadResult | null>(null);
  const { busy, error, run } = useSubmit();
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!file) return;
    const form = new FormData();
    form.append('file', file);
    form.append('month', `${month}-01`);
    await run(async () => setResult(await api('/api/insurance/upload', { method: 'POST', body: form })));
  }
  if (result) {
    return (
      <div>
        <p className="ok">
          {result.saved} of {result.rows} policies saved for {formatMonth(result.month)}: expected commission {formatMoney(result.expectedTotal)}, POSP{' '}
          {formatMoney(result.pospTotal)}.
        </p>
        {result.replaced > 0 && <p className="muted">{result.replaced} policies from the earlier upload of this month were replaced.</p>}
        {result.newPosps > 0 && <p className="muted">{result.newPosps} new POSPs were added as POSP partners.</p>}
        {result.notSaved.length > 0 && (
          <>
            <p className="error">
              {result.notSaved.length} rows were not saved. Fix them (Clients screen or the sheet) and upload again, or add those policies by hand:
            </p>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th className="num">Row</th>
                    <th>Client (as in the sheet)</th>
                    <th>PAN</th>
                    <th>Why</th>
                  </tr>
                </thead>
                <tbody>
                  {result.notSaved.map((r) => (
                    <tr key={r.row}>
                      <td className="num">{r.row}</td>
                      <td>{r.client ?? ''}</td>
                      <td className="mono">{r.pan ?? '—'}</td>
                      <td>{r.reason}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
        <button type="button" onClick={onDone}>
          Done
        </button>
      </div>
    );
  }
  return (
    <form onSubmit={submit} className="fd-form">
      <p className="muted">
        The insurance team's monthly file, sheet “Policy level data” (Business Month, Policy No, POSP Name, Insurance Company, CAT, Client, PAN, …). Each row is
        matched to a client by PAN; the RM comes from the client master, not the sheet. Uploading the month again replaces its earlier upload; policies added by
        hand stay.
      </p>
      <label>
        Business month
        <input type="month" value={month} onChange={(e) => e.target.value && setMonth(e.target.value)} required />
      </label>
      <label>
        File
        <input type="file" accept=".xlsx" onChange={(e) => setFile(e.target.files?.[0] ?? null)} required />
      </label>
      {error && <p className="error">{error}</p>}
      <button type="submit" disabled={busy || !file}>
        Upload
      </button>
    </form>
  );
}
