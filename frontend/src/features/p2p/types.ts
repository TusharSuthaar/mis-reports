/** P2P API types (moved out of api/types.ts, audit M-06; api/types.ts still re-exports them). */

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
