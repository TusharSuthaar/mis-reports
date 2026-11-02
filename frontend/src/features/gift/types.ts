/** GIFT City API types (moved out of api/types.ts, audit M-06; api/types.ts still re-exports them). */

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
