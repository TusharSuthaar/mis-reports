/** Loans API types (moved out of api/types.ts, audit M-06; api/types.ts still re-exports them). */

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
