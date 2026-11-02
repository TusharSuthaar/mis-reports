/** NPS API types (moved out of api/types.ts, audit M-06; api/types.ts still re-exports them). */

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
