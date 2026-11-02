/** FD API types (moved out of api/types.ts, audit M-06; api/types.ts still re-exports them). */
import type { FdCertificate, FdCompounding, FdPaymentStatus, InterestFrequency } from '../../api/types';

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
