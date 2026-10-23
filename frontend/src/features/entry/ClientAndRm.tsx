import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { api } from '../../api/client';
import type { ClientRow, Employee, OrgChart, Page, Partner, Share } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';

/*
 * What every product entry form (FD, bonds, AIF, loans, GIFT City, P2P, NPS, PMS) has in common: the client, found by
 * PAN or client code or added as new (R-01), their contact details, the month, the RM and the referral / sub-broker.
 */

export const PAN_FORMAT = /^[A-Z]{5}[0-9]{4}[A-Z]$/;

/**
 * Clients that are this PAN or this client code. The search also matches names, so keep only real hits: for a PAN, the
 * PAN (shown in full, or masked ABCDE****F); otherwise the client code.
 */
export function matchClients(items: ClientRow[], looked: string): ClientRow[] {
  const q = looked.trim().toUpperCase();
  if (PAN_FORMAT.test(q)) {
    return items.filter(
      (c) => c.pan != null && (c.pan.toUpperCase() === q || (c.pan.includes('*') && c.pan.slice(0, 5) === q.slice(0, 5) && c.pan.slice(-1) === q.slice(-1))),
    );
  }
  return items.filter((c) => c.iwellCode != null && c.iwellCode.toUpperCase() === q);
}

/** The client and RM fields every saved entry carries. */
export interface EntryParty {
  month: string;
  rmId: number;
  partnerId: number | null;
  clientId: number;
  clientName: string;
  pan: string | null;
  clientType: string;
  mobile: string | null;
  email: string | null;
  address: string | null;
}

/**
 * The client / month / RM part of an entry form. {@code src}: the entry being edited (or copied); its client is fixed.
 * Otherwise the client is looked up: one match is taken, several are offered, none asks for a new client.
 */
export function useClientAndRm(src: EntryParty | undefined, initialMonth: string) {
  const [month, setMonth] = useState(initialMonth);
  const [rmId, setRmId] = useState(src ? String(src.rmId) : '');
  const [lookup, setLookup] = useState('');
  const [looked, setLooked] = useState('');
  const [client, setClient] = useState<ClientRow | null>(null);
  const [newName, setNewName] = useState('');
  const [newPan, setNewPan] = useState('');
  const [mobile, setMobile] = useState(src?.mobile ?? '');
  const [email, setEmail] = useState(src?.email ?? '');
  const [address, setAddress] = useState(src?.address ?? '');
  const [partnerId, setPartnerId] = useState(src?.partnerId ? String(src.partnerId) : '');

  const monthDay = `${month.slice(0, 7)}-01`;
  const employees = useLoad<Employee[]>('/api/employees');
  const partners = useLoad<Partner[]>('/api/partners');
  const chart = useLoad<OrgChart>(`/api/org?date=${monthDay}`);
  const found = useLoad<Page<ClientRow>>(!src && looked ? `/api/clients?q=${encodeURIComponent(looked)}&size=20` : null);
  const matches = useMemo(() => (looked && found.data ? matchClients(found.data.items, looked) : null), [found.data, looked]);
  /** After a lookup: the client found, a choice when several match, or the new-client fields when nothing matches. */
  const clientMode: 'lookup' | 'found' | 'pick' | 'new' = src ? 'found' : !matches ? 'lookup' : matches.length === 0 ? 'new' : client ? 'found' : 'pick';
  const rmLocked = !src && !!client?.rmId;
  const shares = useLoad<Record<string, Share[]>>(client ? `/api/shares?clientIds=${client.id}` : null);

  useEffect(() => {
    if (matches && matches.length === 1) setClient(matches[0]);
    if (matches && matches.length === 0 && PAN_FORMAT.test(looked)) setNewPan(looked);
  }, [matches, looked]);
  useEffect(() => {
    if (!client) return;
    if (client.rmId) setRmId(String(client.rmId));
    const own = shares.data?.[client.id]?.find((s) => !s.branch);
    if (own && !partnerId) setPartnerId(String(own.partnerId));
    // eslint-disable-next-line react-hooks/exhaustive-deps -- fill in the client's partner once, never over one already chosen
  }, [client, shares.data]);
  const findClient = () => {
    setClient(null);
    setLooked(lookup.trim().toUpperCase());
  };
  const place = useMemo(() => {
    for (const r of chart.data?.regions ?? []) {
      for (const b of r.branches) if (b.people.some((p) => String(p.id) === rmId)) return { region: r.name, rd: r.rd?.name ?? '—', branch: b.name };
    }
    return null;
  }, [chart.data, rmId]);
  const people = (employees.data ?? []).filter((e) => e.status === 'ACTIVE');
  const sharePartners = (partners.data ?? []).filter((p) => p.type === 'SUB_BROKER' || p.type === 'REFERRAL');

  /** The request fields this part of the form fills. */
  const body = () => ({
    clientId: src ? src.clientId : clientMode === 'found' ? client?.id : null,
    newClientName: clientMode === 'new' ? newName : null,
    newClientPan: clientMode === 'new' && newPan ? newPan : null,
    month: monthDay,
    rmId: Number(rmId),
    partnerId: partnerId ? Number(partnerId) : null,
    mobile,
    email,
    address,
  });

  return {
    src, month, setMonth, monthDay, rmId, setRmId, partnerId, setPartnerId, lookup, setLookup, findClient, matches, client, setClient,
    clientMode, rmLocked, newName, setNewName, newPan, setNewPan, mobile, setMobile, email, setEmail, address, setAddress, place,
    people, sharePartners, body,
    /** The client is settled: found, picked or entered as new. */
    ready: clientMode === 'found' || clientMode === 'new',
  };
}

export type ClientAndRm = ReturnType<typeof useClientAndRm>;

/** The Client fieldset: lookup by PAN or code, a pick among several, a new client, and contact details. */
export function ClientFieldset({ party, radioName, codeHint = 'FC01234', showClientType = false, mobileLabel = 'Mobile No', emailLabel = 'Email Id' }: {
  party: ClientAndRm;
  radioName: string;
  codeHint?: string;
  showClientType?: boolean;
  mobileLabel?: string;
  emailLabel?: string;
}) {
  const { src, lookup, setLookup, findClient, clientMode, matches, client, setClient } = party;
  return (
    <fieldset>
      <legend>Client</legend>
      {src ? (
        <p>
          {src.clientName} <span className="mono">{src.pan}</span>
          {showClientType && ` · Client type: ${src.clientType}`}
        </p>
      ) : (
        <span className="inline-form">
          <label>
            Client PAN or client code
            <input
              value={lookup}
              onChange={(e) => setLookup(e.target.value.toUpperCase().replace(/\s/g, ''))}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  findClient();
                }
              }}
              placeholder={`ABCDE1234F or ${codeHint}`}
            />
          </label>
          <button type="button" className="secondary" disabled={lookup.trim().length < 3} onClick={findClient}>
            Find
          </button>
        </span>
      )}
      {clientMode === 'pick' &&
        (matches ?? []).map((c) => (
          <label key={c.id} className="check">
            <input type="radio" name={radioName} checked={client?.id === c.id} onChange={() => setClient(c)} />
            {c.name} · <span className="mono">{c.pan ?? 'no PAN'}</span> · {c.iwellCode ?? 'no code'} · {c.rmName ?? 'no RM'}
          </label>
        ))}
      {!src && client && (
        <p className="ok">
          {client.name} · <span className="mono">{client.pan ?? 'no PAN'}</span> · Client type: {client.iwellCode ?? 'OFFLINE'}
        </p>
      )}
      {clientMode === 'new' && (
        <>
          <p className="muted">No client has this PAN or code: enter the new client (client type OFFLINE). They are added to Clients with the RM below.</p>
          <label>
            Client name
            <input value={party.newName} onChange={(e) => party.setNewName(e.target.value)} required />
          </label>
          <label>
            PAN
            <input value={party.newPan} onChange={(e) => party.setNewPan(e.target.value.toUpperCase().replace(/\s/g, ''))} maxLength={10} placeholder="ABCDE1234F" />
          </label>
        </>
      )}
      {party.ready && (
        <>
          <label>
            {mobileLabel}
            <input value={party.mobile} onChange={(e) => party.setMobile(e.target.value)} inputMode="tel" />
          </label>
          <label>
            {emailLabel}
            <input type="email" value={party.email} onChange={(e) => party.setEmail(e.target.value)} />
          </label>
          <label>
            Address
            <textarea value={party.address} onChange={(e) => party.setAddress(e.target.value)} rows={2} />
          </label>
        </>
      )}
    </fieldset>
  );
}

/**
 * Month, RM (the client's own when it has one) and referral / sub-broker, with the region, RD and branch the RM sits in.
 * {@code children} go after the partner; {@code note} is added to the line under them.
 */
export function MonthRmFieldset({ party, legend = 'Month and RM', partnerLabel = 'Ref / sub-broker', children, note }: {
  party: ClientAndRm;
  legend?: string;
  partnerLabel?: string;
  children?: ReactNode;
  note?: ReactNode;
}) {
  const { month, setMonth, rmLocked, client, rmId, setRmId, people, partnerId, setPartnerId, sharePartners, place } = party;
  return (
    <fieldset>
      <legend>{legend}</legend>
      <label>
        Month
        <input type="month" value={month.slice(0, 7)} onChange={(e) => e.target.value && setMonth(e.target.value)} required />
      </label>
      {rmLocked ? (
        <label>
          RM
          <input value={client?.rmName ?? ''} readOnly />
        </label>
      ) : (
        <label>
          RM
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
      <label>
        {partnerLabel}
        <select value={partnerId} onChange={(e) => setPartnerId(e.target.value)}>
          <option value="">None</option>
          {sharePartners.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name} ({p.type === 'SUB_BROKER' ? 'sub-broker' : 'referral'})
            </option>
          ))}
        </select>
      </label>
      {children}
      <p className="muted">
        {place ? `Region: ${place.region} · RD: ${place.rd} · Branch: ${place.branch}` : 'Region and RD come from the RM.'}
        {rmLocked && ' · The RM comes from the client: change it on the Clients screen.'}
        {note}
      </p>
    </fieldset>
  );
}

/** "Cancel this …": asks why, then cancels the entry (it stays, marked cancelled). */
export function CancelEntry({ url, question, label = 'Cancel this entry', onDone }: { url: string; question: string; label?: string; onDone: () => void }) {
  const { busy, run } = useSubmit();
  return (
    <button
      type="button"
      className="link"
      disabled={busy}
      onClick={async () => {
        const reason = window.prompt(question);
        if (reason === null) return;
        if (await run(() => api(url, { method: 'POST', body: { reason } }))) onDone();
      }}
    >
      {label}
    </button>
  );
}
