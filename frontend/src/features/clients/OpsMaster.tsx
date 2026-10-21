import { useLoad } from '../../api/useLoad';
import { formatDateTime } from '../../format';

export interface OpsField {
  label: string;
  value: string;
}

/** All the Ops client master (Finesse ClientMasterReport) has for one client code (R-08). */
export interface OpsRecord {
  code: string;
  name: string;
  pan: string | null;
  guardianPan: string | null;
  familyHeadCode: string | null;
  details: OpsField[];
  addresses: OpsField[][];
  updatedAt: string;
}

/** How the Ops columns are grouped on the page; a column not listed here shows under "Other". */
const GROUPS: { title: string; labels: string[] }[] = [
  { title: 'Contact', labels: ['Email', 'Reporting Email', 'Phone Number', 'Call me as', 'Salutation'] },
  { title: 'Family', labels: ['Family Name', 'Family Head Code', 'Family Head Name', 'Family Head', 'Relationship with Family'] },
  {
    title: 'Personal',
    labels: ['DOB', 'AGE', 'Gender', 'Resident Status', 'Individual', 'Marital Status', 'Spouse Name', 'Anniversary Date', 'Employment', 'Occupation',
      'Company Working for', 'Designation', 'Website', 'Guardian Name'],
  },
  {
    title: 'Holding and nominees',
    labels: ['Holding Nature', 'Joint Holder 1 Name', 'Joint Holder 1 PAN', 'Joint Holder 1 DOB', 'Joint Holder 2 Name', 'Joint Holder 2 PAN',
      'Joint Holder 2 DOB', 'Nominee Name', 'Nominee Relation', 'Nominee Contact'],
  },
  {
    title: 'Account with Epsilon',
    labels: ['RM', 'SRM', 'Branch Name', 'Date of joining', 'Client Status', 'Billable Status', 'Client Tag', 'Client Type', 'Client Category',
      'Login Status', 'Referred By'],
  },
  {
    title: 'Exchanges, mandates and KYC',
    labels: ['BSE Login Status', 'BSE Mandate Status', 'NSE Login Status', 'NSE Mandate Status', 'MFU Login Status', 'MFU Mandate Status',
      'DIGIO Mandate', 'KYC Status', 'KYC Fetch Date', 'Latest Agreement Date', 'Latest Agreement Status', 'Risk Profile', 'Risk Score',
      'Latest Risk Assessment', 'Agreed Debt Ratio', 'Agreed Equity Ratio'],
  },
];

const key = (s: string) => s.trim().toLowerCase();

/** The record's fields in their groups, in the Ops file's order within a group; empty groups are dropped. */
export function groupOpsFields(details: OpsField[]): { title: string; fields: OpsField[] }[] {
  const placed = new Set<string>();
  const groups = GROUPS.map((g) => {
    const wanted = new Set(g.labels.map(key));
    const fields = details.filter((f) => wanted.has(key(f.label)));
    fields.forEach((f) => placed.add(f.label));
    return { title: g.title, fields };
  });
  groups.push({ title: 'Other', fields: details.filter((f) => !placed.has(f.label)) });
  return groups.filter((g) => g.fields.length > 0);
}

/** One address row as a readable block: the address lines, then city, state, PIN, country. */
function addressText(a: OpsField[]): { tag: string | null; lines: string[]; rest: OpsField[] } {
  const get = (l: string) => a.find((f) => key(f.label) === key(l))?.value;
  const lineFields = a.filter((f) => /^address line/i.test(f.label));
  const lines = lineFields.length > 0 ? lineFields.map((f) => f.value) : [get('Address') ?? ''].filter(Boolean);
  const place = [get('City'), get('State'), get('PIN Code')].filter(Boolean).join(', ');
  const country = get('Country');
  const used = new Set(['address tag', 'address', 'city', 'state', 'pin code', 'country', ...lineFields.map((f) => key(f.label))]);
  return { tag: get('Address Tag') ?? null, lines: [...lines, place, country ?? ''].filter(Boolean), rest: a.filter((f) => !used.has(key(f.label))) };
}

/** Everything the Ops client master has for this client, one card per client code (R-08). */
export function OpsMaster({ clientId }: { clientId: number }) {
  const records = useLoad<OpsRecord[]>(`/api/clients/${clientId}/ops-master`);
  if (records.error) return <p className="error">{records.error}</p>;
  if (!records.data) return null;
  if (records.data.length === 0) {
    return (
      <div className="panel-card">
        <h2>Ops client master</h2>
        <p className="muted">Not in the Ops client master: no Finesse code has this client&rsquo;s PAN. Upload the latest ClientMasterReport on Data uploads.</p>
      </div>
    );
  }
  return (
    <>
      {records.data.map((r) => (
        <div key={r.code} className="panel-card ops-master">
          <div className="ops-head">
            <h2>
              Ops client master <span className="badge mono">{r.code}</span>
            </h2>
            <span className="muted">From the Ops file loaded {formatDateTime(r.updatedAt)}</span>
          </div>
          <dl className="facts">
            <dt>Name in Ops</dt>
            <dd>{r.name}</dd>
            <dt>PAN in Ops</dt>
            <dd className="mono">{r.pan ?? '—'}</dd>
            {r.guardianPan && (
              <>
                <dt>Guardian PAN</dt>
                <dd className="mono">{r.guardianPan}</dd>
              </>
            )}
          </dl>
          <div className="ops-groups">
            {groupOpsFields(r.details).map((g) => (
              <section key={g.title}>
                <h3>{g.title}</h3>
                <dl className="facts">
                  {g.fields.map((f) => (
                    <div key={f.label} className="fact-row">
                      <dt>{f.label}</dt>
                      <dd className={/pan/i.test(f.label) ? 'mono' : undefined}>{f.value}</dd>
                    </div>
                  ))}
                </dl>
              </section>
            ))}
            {r.addresses.map((a, i) => {
              const t = addressText(a);
              return (
                <section key={`addr-${i}`}>
                  <h3>Address{t.tag ? ` (${t.tag})` : ''}</h3>
                  <address className="ops-address">
                    {t.lines.map((l, j) => (
                      <div key={j}>{l}</div>
                    ))}
                  </address>
                  {t.rest.length > 0 && (
                    <dl className="facts">
                      {t.rest.map((f) => (
                        <div key={f.label} className="fact-row">
                          <dt>{f.label}</dt>
                          <dd>{f.value}</dd>
                        </div>
                      ))}
                    </dl>
                  )}
                </section>
              );
            })}
          </div>
        </div>
      ))}
    </>
  );
}
