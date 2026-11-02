/** AIF API types (moved out of api/types.ts, audit M-06; api/types.ts still re-exports them). */
import type { FdCertificate, FdPaymentStatus } from '../../api/types';

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
