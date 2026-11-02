/** PMS API types (moved out of api/types.ts, audit M-06; api/types.ts still re-exports them). */

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
