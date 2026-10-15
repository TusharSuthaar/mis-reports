export type Role = 'RM' | 'RD' | 'HO' | 'FINANCE' | 'ADMIN';

export const ROLES: Role[] = ['RM', 'RD', 'HO', 'FINANCE', 'ADMIN'];

export interface Me {
  id: number;
  username: string;
  displayName: string;
  role: Role;
}

export interface User {
  id: number;
  username: string;
  displayName: string;
  role: Role;
  employeeId: number | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AuditEntry {
  id: number;
  entity: string;
  entityId: string | null;
  action: string;
  before: unknown;
  after: unknown;
  by: string;
  at: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export type EmployeeType = 'RM' | 'HOUSE';
export type PartnerType = 'SUB_BROKER' | 'REFERRAL' | 'POSP';
export const PARTNER_TYPES: PartnerType[] = ['SUB_BROKER', 'REFERRAL', 'POSP'];
export const PARTNER_TYPE_LABEL: Record<PartnerType, string> = {
  SUB_BROKER: 'Sub-broker',
  REFERRAL: 'Referral partner',
  POSP: 'POSP',
};

export interface PersonRef {
  id: number;
  empCode: string;
  name: string;
}

export interface Region {
  id: number;
  name: string;
  currentRd: PersonRef | null;
}

export interface Branch {
  id: number;
  name: string;
  regionId: number | null;
  regionName: string | null;
}

export interface Employee {
  id: number;
  empCode: string;
  name: string;
  type: EmployeeType;
  status: 'ACTIVE' | 'EXITED';
  joinedOn: string | null;
  exitedOn: string | null;
  branchId: number | null;
  branchName: string | null;
}

/** One row of a dated history. */
export interface HistoryEntry {
  id: number;
  targetId: number;
  targetName: string;
  validFrom: string;
  validTo: string | null;
  reason: string | null;
  changedBy: string;
  changedAt: string;
}

export interface Partner {
  id: number;
  type: PartnerType;
  name: string;
  pan: string | null;
  gstin: string | null;
  arnOrPospCode: string | null;
  bankRef: string | null;
  status: 'ACTIVE' | 'INACTIVE';
  currentRmLinks: number;
}

export interface PartnerLink {
  id: number;
  partnerId: number;
  rmId: number;
  rmCode: string;
  rmName: string;
  validFrom: string;
  validTo: string | null;
  reason: string | null;
  changedBy: string;
  changedAt: string;
}

export interface ChartPerson {
  id: number;
  empCode: string;
  name: string;
  type: EmployeeType;
}

export interface ChartBranch {
  id: number;
  name: string;
  people: ChartPerson[];
}

export interface OrgChart {
  date: string;
  regions: { id: number; name: string; rd: PersonRef | null; branches: ChartBranch[] }[];
  branchesWithoutRegion: ChartBranch[];
  peopleWithoutBranch: ChartPerson[];
}

export interface Job<R = unknown> {
  id: number;
  type: string;
  status: 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED';
  result: R | null;
  error: string | null;
}

export interface ImportResult {
  fileName: string;
  dryRun: boolean;
  applied: boolean;
  sheets: { sheet: string; created: number; updated: number; unchanged: number }[];
  errors: { sheet: string; row: number; message: string }[];
}

export interface ClientRow {
  id: number;
  name: string;
  pan: string | null;
  iwellCode: string | null;
  rmId: number | null;
  rmName: string | null;
  branchName: string | null;
}

export interface ClientDetail {
  client: ClientRow;
  iwellCode2: string | null;
  familyHeadCode: string | null;
  familyHeadName: string | null;
}

export type MatchStatus = 'MATCHED' | 'REMEMBERED' | 'SEVERAL' | 'UNKNOWN';

export interface Choice {
  id: number;
  label: string;
}

export interface LoadPreview {
  token: string;
  fileName: string;
  rows: number;
  totals: { gross: number; gst: number; net: number };
  errors: { row: number; message: string }[];
  warningCount: number;
  warnings: { row: number; message: string }[];
  newClients: number;
  existingClients: number;
  withoutPan: number;
  rms: { spelling: string; clients: number; net: number; status: MatchStatus; rmId: number | null; rmLabel: string | null; candidates: Choice[] }[];
  subBrokers: { spelling: string; clients: number; net: number; status: MatchStatus; partnerId: number | null; partnerName: string | null }[];
  allRms: Choice[];
}

export interface LoadResult {
  fileName: string;
  clientsCreated: number;
  clientsAlreadyThere: number;
  rmAssigned: number;
  needsRm: number;
  partnersCreated: number;
  spellingsRemembered: number;
  familyHeadsLinked: number;
}

/** A client's partner share; percent is 0–100 of net. */
export interface Share {
  id: number;
  clientId: number;
  partnerId: number;
  partnerName: string;
  partnerRole: PartnerType;
  percent: number;
  validFrom: string;
  validTo: string | null;
  reason: string | null;
  changedBy: string;
  changedAt: string;
}

export interface ShareLoadPreview {
  token: string;
  fileName: string;
  error: string | null;
  rows: number;
  rowsWithPartner: number;
  matchedClients: number;
  toSet: number;
  toEnd: number;
  unchanged: number;
  zeroPct: number;
  bothRoles: number;
  matchedByPanOnly: number;
  conflicts: number;
  newPartners: { name: string; type: PartnerType; clients: number }[];
  unmatchedCount: number;
  unmatched: { row: number; clientName: string; maskedPan: string | null; reason: string }[];
  problems: string[];
  from: string;
  newClients: number;
  newClientsWithoutPan: number;
  rmChangeCount: number;
  rmUnchanged: number;
  clientsWithoutRm: number;
  rmChanges: { clientId: number; client: string; pan: string | null; fromRm: string | null; toRm: string }[];
  unknownRms: { spelling: string; note: string | null; clients: number }[];
}

export interface ShareLoadResult {
  fileName: string;
  clientsCreated: number;
  rmChanged: number;
  sharesSet: number;
  sharesEnded: number;
  unchanged: number;
  partnersCreated: number;
  zeroPct: number;
  unmatched: number;
}

export interface MoneyTotals {
  gross: number;
  gst: number;
  net: number;
}

export interface UploadInfo {
  id: number;
  fileName: string;
  status: 'STAGED' | 'COMMITTED' | 'SUPERSEDED' | 'DISCARDED';
  month: string; // 2026-08
  periodId: number;
  fileTotals: MoneyTotals;
  rows: number;
  newClients: number;
  warnings: number;
  uploadedBy: string;
  uploadedAt: string;
  committedBy: string | null;
  committedAt: string | null;
}

export interface ReviewClient {
  clientId: number;
  code: string;
  name: string;
  fileRm: string | null;
  ourRm: string | null;
  net: number;
}

/** A client of a brokerage upload that is not in Master MIS (no RM for the month): Process waits for it. */
export interface NotInMaster {
  clientId: number;
  codes: string;
  name: string;
  pan: string | null;
  reason: string;
  net: number;
}

export interface UploadReview {
  upload: UploadInfo;
  codes: number;
  clients: number;
  byPan: number;
  byGuardianPan: number;
  byName: number;
  notInMaster: NotInMaster[];
  canSave: boolean;
  correction: boolean;
}

export interface FinesseMasterStatus {
  codes: number;
  withPan: number;
  withGuardianPan: number;
  updatedAt: string | null;
  updatedBy: string | null;
}

export interface FinesseMasterLoad {
  fileName: string;
  rows: number;
  added: number;
  updated: number;
  unchanged: number;
  withPan: number;
  withGuardianPan: number;
  withoutAnyPan: number;
  invalidPans: number;
}

export interface MonthRow {
  periodId: number;
  month: string;
  status: 'OPEN' | 'PROCESSED' | 'LOCKED';
  saved: (MoneyTotals & { lines: number }) | null;
  savedUpload: UploadInfo | null;
  pendingUpload: UploadInfo | null;
  processedBy: string | null;
  processedAt: string | null;
  lockedBy: string | null;
  lockedAt: string | null;
  notInMaster: number;
}

export interface StageResult {
  uploadId: number | null;
  errors: { row: number; message: string }[];
  warnings: number;
  notes: string[];
}

export interface SplitSummary {
  periodId: number;
  month: string;
  status: string;
  lines: number;
  adjustments: number;
  net: number;
  subBrokers: number;
  referrals: number;
  epsilon: number;
  byPartner: { partnerId: number; partnerName: string; role: PartnerType; clients: number; amount: number }[];
  byBranch: { region: string | null; branch: string | null; net: number; partners: number; epsilon: number }[];
}

export interface RunResult {
  runId: number;
  month: string;
  lines: number;
  net: number;
  partners: number;
  epsilon: number;
}
