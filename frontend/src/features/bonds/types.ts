/** Bonds API types (moved out of api/types.ts, audit M-06; api/types.ts still re-exports them). */
import type { FdPaymentStatus } from '../../api/types';

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
