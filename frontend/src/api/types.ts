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
  /** Place in lists and in the END region section (1 = first). */
  position: number;
  /** END region section shows this region's branches as rows above it. */
  endBranchRows: boolean;
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

export type PartnerStatus = 'ACTIVE' | 'PENDING' | 'INACTIVE';

export const PARTNER_STATUS_LABEL: Record<PartnerStatus, string> = {
  ACTIVE: 'Active',
  PENDING: 'Pending approval',
  INACTIVE: 'Inactive',
};

/** Products a partner can share in (codes as the API sends them), in Master MIS column order. */
export type Product = 'MF' | 'PREIPO' | 'FD' | 'BOND' | 'LOAN' | 'EQUITY' | 'PMS' | 'AIF' | 'ADVISORY' | 'GIFT' | 'NPS' | 'P2P' | 'INS';

export const PRODUCTS: { code: Product; label: string }[] = [
  { code: 'MF', label: 'MF' },
  { code: 'PREIPO', label: 'Pre-IPO' },
  { code: 'FD', label: 'FD' },
  { code: 'BOND', label: 'Bonds' },
  { code: 'LOAN', label: 'Loans' },
  { code: 'EQUITY', label: 'Equity' },
  { code: 'PMS', label: 'PMS' },
  { code: 'AIF', label: 'AIF' },
  { code: 'ADVISORY', label: 'Advisory' },
  { code: 'GIFT', label: 'GIFT' },
  { code: 'NPS', label: 'NPS' },
  { code: 'P2P', label: 'P2P' },
  { code: 'INS', label: 'Insurance' },
];

/** One dated % of a partner for one product; percent is 0–100 of net. */
export interface ProductShare {
  id: number;
  partnerId: number;
  product: Product;
  percent: number;
  validFrom: string;
  validTo: string | null;
  reason: string | null;
  changedBy: string;
  changedAt: string;
}

/** A partner for every client of a branch, from a date (paid first). */
export interface BranchPartner {
  id: number;
  branchId: number;
  partnerId: number;
  partnerName: string;
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
  /** PENDING: approval under process — linked to clients but paid nothing. */
  status: PartnerStatus;
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
  /** What the job does, in plain words. */
  title: string;
  status: 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED';
  result: R | null;
  error: string | null;
  /** Same reference as in the server logs and on the Activity screen. */
  reference: string | null;
  startedAt?: string | null;
  /** How far a running job is; null until it reports. */
  progress?: JobProgress | null;
}

/** Step {@code step} of {@code steps}; {@code done} of {@code total} items in it (total 0 = unknown). */
export interface JobProgress {
  step: number;
  steps: number;
  label: string | null;
  done: number;
  total: number;
}

export type ActivityKind = 'JOB' | 'EVENT' | 'ERROR';

export interface Activity {
  kind: ActivityKind;
  id: number;
  title: string;
  detail: string | null;
  result: 'WAITING' | 'RUNNING' | 'DONE' | 'FAILED';
  by: string;
  at: string;
  finishedAt: string | null;
  reference: string | null;
}

export interface ActivitySummary {
  runningJobs: number;
  failedJobs: number;
  errors: number;
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
  /** The % that counts (0–100). */
  percent: number;
  /** The client's own % (an exception); null = the partner's % for the product applies. */
  clientPercent: number | null;
  source: 'CLIENT' | 'PARTNER' | 'PENDING' | 'NO_PARTNER_PERCENT';
  /** Set when this partner comes from the branch's rule (a partner for all its clients), not the client's own link. */
  branch: string | null;
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
  /** What the workbook's partner sheets change; null when the file could not be read. */
  partnerRates: {
    sheetPartners: number;
    percentChanges: number;
    followsPartner: number;
    clientExceptions: number;
    pending: string[];
    approved: string[];
  } | null;
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
  productPercentsSet: number;
  clientExceptions: number;
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

export type ProductCode = 'MF' | 'PREIPO' | 'FD' | 'BOND' | 'LOAN' | 'EQUITY' | 'PMS' | 'AIF' | 'ADVISORY' | 'GIFT' | 'NPS' | 'P2P' | 'INS';

/** GST and TDS of a product over a period (Products & taxes). Percent values: 18 = 18%. */
export interface TaxRate {
  id: number;
  product: ProductCode;
  gstPercent: number;
  tdsPercent: number;
  tdsByDefault: boolean;
  validFrom: string;
  validTo: string | null;
  reason: string | null;
  changedBy: string;
  changedAt: string;
}

export interface ProductTaxLine {
  product: ProductCode;
  name: string;
  now: TaxRate | null;
}

export type InterestFrequency = 'MONTHLY' | 'QUARTERLY' | 'HALF_YEARLY' | 'YEARLY' | 'CUMULATIVE';
export type FdCertificate = 'PENDING' | 'RECEIVED' | 'NOT_APPLICABLE';
export type FdCompounding = 'QUARTERLY' | 'HALF_YEARLY' | 'YEARLY';
export const COMPOUNDING_LABEL: Record<FdCompounding, string> = { QUARTERLY: 'Quarterly', HALF_YEARLY: 'Half-yearly', YEARLY: 'Yearly' };
export type FdPaymentStatus = 'PENDING' | 'RECEIVED' | 'RECEIVED_GST_PENDING';

export const INTEREST_FREQUENCY_LABEL: Record<InterestFrequency, string> = {
  MONTHLY: 'Monthly',
  QUARTERLY: 'Quarterly',
  HALF_YEARLY: 'Half-yearly',
  YEARLY: 'Yearly',
  CUMULATIVE: 'Cumulative (at maturity)',
};
export const CERTIFICATE_LABEL: Record<FdCertificate, string> = { PENDING: 'Pending', RECEIVED: 'Received', NOT_APPLICABLE: 'Not applicable' };
export const PAYMENT_LABEL: Record<FdPaymentStatus, string> = { PENDING: 'Pending', RECEIVED: 'Received', RECEIVED_GST_PENDING: 'Received – GST pending' };

/** One FD booked, as entered on the FD screen, with its commission worked out (R-70). */
export interface FdEntry {
  id: number;
  month: string;
  rmId: number;
  rmName: string;
  branchName: string | null;
  region: string | null;
  rd: string | null;
  partnerId: number | null;
  partnerName: string | null;
  partnerType: string | null;
  clientId: number;
  clientName: string;
  pan: string | null;
  /** The client's code, or OFFLINE. */
  clientType: string;
  mobile: string | null;
  email: string | null;
  address: string | null;
  fdName: string;
  fdNo: string | null;
  interestFrequency: InterestFrequency;
  /** The platform the FD is booked through (the register's PARTNER, e.g. J M Finance). */
  platform: string | null;
  depositDate: string;
  periodMonths: number;
  maturityDate: string;
  ratePercent: number;
  amount: number;
  compounding: FdCompounding | null;
  maturityValue: number | null;
  certificate: FdCertificate;
  marginPercent: number;
  gross: number;
  tdsApplied: boolean;
  tdsPercent: number;
  tds: number;
  gstPercent: number;
  gst: number;
  net: number;
  invoice: number;
  /** The register's figures kept as they are (R-79). */
  figuresAsGiven: boolean;
  invoiceRaised: boolean;
  /** A tick on the form only (not in the Excel register). */
  onPortal: boolean;
  paymentStatus: FdPaymentStatus;
  remarks: string | null;
  status: 'ACTIVE' | 'CANCELLED';
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
}

export interface FdSuggestions {
  fdNames: string[];
  platforms: string[];
  margins: { fdName: string; platform: string | null; periodMonths: number; marginPercent: number }[];
}

/** A platform's brokerage terms over a period (Setup → Platforms, R-75). Percent values: 5 = 5%. */
export interface PlatformTerms {
  id: number;
  platformId: number;
  gstInclusive: boolean;
  tdsPercent: number;
  ourShare: number;
  validFrom: string;
  validTo: string | null;
  reason: string | null;
  changedBy: string;
  changedAt: string;
}

export interface Platform {
  id: number;
  name: string;
  now: PlatformTerms | null;
}

export type BondPayoutFrequency = 'MONTHLY' | 'QUARTERLY' | 'HALF_YEARLY' | 'YEARLY' | 'CUMULATIVE' | 'AT_MATURITY';
export const BOND_PAYOUT_LABEL: Record<BondPayoutFrequency, string> = {
  MONTHLY: 'Monthly',
  QUARTERLY: 'Quarterly',
  HALF_YEARLY: 'Half-yearly',
  YEARLY: 'Yearly',
  CUMULATIVE: 'Cumulative',
  AT_MATURITY: 'At maturity',
};

/** One bond deal, as entered on the Bonds screen, with its brokerage worked out (R-76). */
export interface BondEntry {
  id: number;
  month: string;
  rmId: number;
  rmName: string;
  branchName: string | null;
  region: string | null;
  rd: string | null;
  partnerId: number | null;
  partnerName: string | null;
  partnerType: string | null;
  clientId: number;
  clientName: string;
  pan: string | null;
  clientType: string;
  mobile: string | null;
  email: string | null;
  address: string | null;
  platformId: number;
  platformName: string;
  bondName: string;
  isin: string | null;
  couponPercent: number | null;
  ytmPercent: number | null;
  payoutFrequency: BondPayoutFrequency | null;
  paymentSchedule: string | null;
  investmentDate: string;
  allotmentDate: string | null;
  maturityDate: string | null;
  quantity: number;
  ratePerUnit: number | null;
  faceValue: number | null;
  investmentAmount: number;
  accruedInterest: number;
  consideration: number;
  markupPercent: number;
  ourShare: number;
  gstInclusive: boolean;
  brokerage: number;
  gross: number;
  tdsApplied: boolean;
  tdsPercent: number;
  tds: number;
  gstPercent: number;
  gst: number;
  net: number;
  invoice: number;
  /** The register's figures kept as they are (R-79). */
  figuresAsGiven: boolean;
  invoiceRaised: boolean;
  invoiceDate: string | null;
  paymentReceivedOn: string | null;
  paymentStatus: FdPaymentStatus;
  onPortal: boolean;
  remarks: string | null;
  status: 'ACTIVE' | 'CANCELLED';
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
}

export interface BondSuggestions {
  bondNames: string[];
  isins: { bondName: string; isin: string }[];
  markups: { platformId: number; bondName: string; isin: string | null; markupPercent: number }[];
}

/** One drawdown of an AIF commitment. */
export interface AifInstalment {
  seq: number;
  amount: number;
  paidOn: string | null;
}

/** One AIF commitment, as entered on the AIF screen, with its revenue worked out (R-78). */
export interface AifEntry {
  id: number;
  month: string;
  rmId: number;
  rmName: string;
  branchName: string | null;
  region: string | null;
  rd: string | null;
  partnerId: number | null;
  partnerName: string | null;
  partnerType: string | null;
  /** The deal's own sharing % for its partner; null = the partner's AIF %. */
  sharingPercent: number | null;
  clientId: number;
  clientName: string;
  pan: string | null;
  clientType: string;
  mobile: string | null;
  email: string | null;
  address: string | null;
  platformId: number;
  platformName: string;
  fundName: string;
  commitment: number;
  instalments: AifInstalment[];
  called: number;
  agreementDate: string | null;
  tenure: string | null;
  maturityDate: string | null;
  soaReceived: boolean;
  applicationForm: boolean;
  investmentMail: boolean;
  quantity: number | null;
  price: number | null;
  certificate: FdCertificate;
  payoutTerms: string | null;
  markupPercent: number;
  ourShare: number;
  gstInclusive: boolean;
  brokerage: number;
  gross: number;
  tdsApplied: boolean;
  tdsPercent: number;
  tds: number;
  gstPercent: number;
  gst: number;
  net: number;
  invoice: number;
  /** The register's figures kept as they are (R-79). */
  figuresAsGiven: boolean;
  invoiceRaised: boolean;
  invoiceDate: string | null;
  paymentReceivedOn: string | null;
  paymentStatus: FdPaymentStatus;
  partnerPaid: boolean;
  onInvestwell: boolean;
  remarks: string | null;
  status: 'ACTIVE' | 'CANCELLED';
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
}

export interface AifSuggestions {
  fundNames: string[];
  markups: { platformId: number; fundName: string; markupPercent: number }[];
}

export type LoanPayoutKind = 'DISBURSAL' | 'TRAIL';
export type LoanPayoutOn = 'SANCTIONED' | 'UTILISED';
export type LoanPaymentStatus = 'PENDING' | 'RECEIVED';
export const LOAN_PAYMENT_LABEL: Record<LoanPaymentStatus, string> = { PENDING: 'Pending', RECEIVED: 'Received (credited)' };

/** One loan payout, as entered on the Loans screen (a row of the LOANS register, R-80). */
export interface LoanEntry {
  id: number;
  month: string;
  rmId: number;
  rmName: string;
  branchName: string | null;
  region: string | null;
  rd: string | null;
  partnerId: number | null;
  partnerName: string | null;
  partnerType: string | null;
  clientId: number;
  clientName: string;
  pan: string | null;
  clientType: string;
  mobile: string | null;
  email: string | null;
  address: string | null;
  /** The register's PARTNER: the lender (TATA, Rupee Boss…). */
  lender: string;
  loanType: string;
  /** The register's "Invt Date". */
  disbursalDate: string;
  sanctionedAmount: number;
  utilisedAmount: number | null;
  ratePercent: number | null;
  payoutKind: LoanPayoutKind;
  payoutOn: LoanPayoutOn;
  /** The register's "Revenue sharing" %. */
  payoutPercent: number | null;
  fixedAmount: number | null;
  gross: number;
  tdsApplied: boolean;
  tdsPercent: number;
  tds: number;
  gstPercent: number;
  gst: number;
  net: number;
  invoice: number;
  figuresAsGiven: boolean;
  invoiceRaised: boolean;
  paymentStatus: LoanPaymentStatus;
  /** The register's Payment Status text, kept as written (e.g. "Invoice will be raised in Oct"). */
  paymentNote: string | null;
  receivedOn: string | null;
  loanClosed: boolean;
  remarks: string | null;
  status: 'ACTIVE' | 'CANCELLED';
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
}

export interface LoanSuggestions {
  lenders: string[];
  loanTypes: string[];
  payouts: { lender: string; loanType: string; payoutKind: LoanPayoutKind; payoutOn: LoanPayoutOn; payoutPercent: number }[];
}

export type GiftTrxnStatus = 'PENDING' | 'DONE';
export type GiftPaymentStatus = 'PENDING' | 'RECEIVED';
export const GIFT_PAYMENT_LABEL: Record<GiftPaymentStatus, string> = { PENDING: 'Pending', RECEIVED: 'Received' };

/** One GIFT City investment, as entered on the GIFT CITY screen (a row of the GIFT CITY register, R-82). */
export interface GiftEntry {
  id: number;
  month: string;
  rmId: number;
  rmName: string;
  branchName: string | null;
  region: string | null;
  rd: string | null;
  partnerId: number | null;
  partnerName: string | null;
  partnerType: string | null;
  clientId: number;
  clientName: string;
  pan: string | null;
  clientType: string;
  mobile: string | null;
  email: string | null;
  address: string | null;
  /** The register's PARTNER: the GIFT City fund. */
  fund: string;
  amountUsd: number | null;
  fxRate: number | null;
  /** The day the rate is for, and where it came from ("ECB rate of …", "entered by hand"). */
  fxRateDate: string | null;
  fxSource: string | null;
  amountInr: number | null;
  /** The register's ENTRY STATUS: entered on the portal. */
  onPortal: boolean;
  trxnStatus: GiftTrxnStatus;
  /** Typed for now (excl. GST); null = not known yet. */
  brokerage: number | null;
  gross: number;
  tdsApplied: boolean;
  tdsPercent: number;
  tds: number;
  gstPercent: number;
  gst: number;
  net: number;
  invoice: number;
  invoiceRaised: boolean;
  paymentStatus: GiftPaymentStatus;
  receivedOn: string | null;
  remarks: string | null;
  status: 'ACTIVE' | 'CANCELLED';
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
}

/** A USD → ₹ rate for a day (R-84); rate null = not in the list and could not be fetched. */
export interface ExchangeRate {
  currency: string;
  day: string;
  rate: number | null;
  rateDate: string | null;
  source: 'ECB' | 'MANUAL' | null;
  note: string;
  reason: string | null;
  changedBy: string | null;
  changedAt: string | null;
}

export type PmsPayoutFrequency = 'MONTHLY' | 'QUARTERLY' | 'NONE';
export type PmsCertificate = 'PENDING' | 'RECEIVED' | 'NOT_APPLICABLE';
export type PmsHolding = 'ACTIVE' | 'REDEEMED';

/** One client's PMS investment (a row of the PMS register, R-85). Entry only: no revenue yet. */
export interface PmsInvestment {
  id: number;
  month: string;
  rmId: number;
  rmName: string;
  branchName: string | null;
  region: string | null;
  rd: string | null;
  partnerId: number | null;
  partnerName: string | null;
  partnerType: string | null;
  clientId: number;
  clientName: string;
  pan: string | null;
  clientType: string;
  mobile: string | null;
  email: string | null;
  address: string | null;
  /** The register's PARTNER: the AMC. */
  amc: string;
  /** The register's PMS column: the strategy. */
  strategy: string;
  amount: number;
  investmentDate: string;
  onPortal: boolean;
  /** The register's MARK UP %: yearly trail. */
  trailPercent: number | null;
  payoutFrequency: PmsPayoutFrequency;
  qty: number | null;
  price: number | null;
  consideration: number | null;
  certificate: PmsCertificate;
  investmentMail: boolean;
  holdingStatus: PmsHolding;
  redeemedOn: string | null;
  remarks: string | null;
  status: 'ACTIVE' | 'CANCELLED';
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
}

/** One AMC payout for an invoice period (R-85). */
export interface PmsPayout {
  id: number;
  amc: string;
  periodFrom: string;
  periodTo: string;
  amount: number;
  tdsApplied: boolean;
  tdsPercent: number;
  tds: number;
  gstPercent: number;
  gst: number;
  net: number;
  invoice: number;
  invoiceRaised: boolean;
  receivedOn: string | null;
  remarks: string | null;
  status: 'ACTIVE' | 'CANCELLED';
  updatedBy: string;
  updatedAt: string;
}

export type NpsCertificate = 'PENDING' | 'RECEIVED' | 'NOT_APPLICABLE';

/** One NPS investment (a row of the NPS register, R-86), with its revenue when there is one. */
export interface NpsEntry {
  id: number;
  month: string;
  rmId: number;
  rmName: string;
  branchName: string | null;
  region: string | null;
  rd: string | null;
  partnerId: number | null;
  partnerName: string | null;
  partnerType: string | null;
  clientId: number;
  clientName: string;
  pan: string | null;
  clientType: string;
  mobile: string | null;
  email: string | null;
  address: string | null;
  /** The register's PARTNER: the POP (Stock Holding, CAMS, ICICI NPS…). */
  pop: string;
  investmentDate: string;
  amount: number;
  welcomeMail: boolean;
  investmentMail: boolean;
  certificate: NpsCertificate;
  onPortal: boolean;
  revenueRemarks: string | null;
  popCharges: number | null;
  sharePercent: number | null;
  /** Invoice amount without GST (0 = no revenue). */
  revenue: number;
  gstPercent: number;
  gst: number;
  invoice: number;
  receivedOn: string | null;
  remarks: string | null;
  status: 'ACTIVE' | 'CANCELLED';
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
}

/** One P2P investment (a row of the P2P register, R-87), with a typed brokerage when known. */
export interface P2pEntry {
  id: number;
  month: string;
  rmId: number;
  rmName: string;
  branchName: string | null;
  region: string | null;
  rd: string | null;
  partnerId: number | null;
  partnerName: string | null;
  partnerType: string | null;
  clientId: number;
  clientName: string;
  pan: string | null;
  clientType: string;
  mobile: string | null;
  email: string | null;
  address: string | null;
  /** The register's PARTNER: the P2P platform. */
  platform: string;
  scheme: string;
  roiPercent: number | null;
  amount: number | null;
  fundsInitiatedOn: string | null;
  startDate: string | null;
  quarterEndDate: string | null;
  onPortal: boolean;
  brokerage: number | null;
  gross: number;
  tdsApplied: boolean;
  tdsPercent: number;
  tds: number;
  gstPercent: number;
  gst: number;
  net: number;
  invoice: number;
  /** The register's PAYMENT STATUS, as written (e.g. "BILL RAISED -13 OCT"). */
  paymentNote: string | null;
  receivedOn: string | null;
  revSharingStatus: string | null;
  remarks: string | null;
  status: 'ACTIVE' | 'CANCELLED';
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
}
