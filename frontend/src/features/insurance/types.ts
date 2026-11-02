/** Insurance API types (moved out of api/types.ts, audit M-06; api/types.ts still re-exports them). */

export type InsuranceCategory = 'LIFE' | 'HEALTH' | 'MOTOR' | 'GENERAL';
export type InsuranceBusinessType = 'NEW' | 'RENEWAL' | 'PORT' | 'ENDORSEMENT' | 'FYCI';

/** One insurance policy (a row of the insurance team's "Policy level data" sheet, R-94), with its commissions (R-95). */
export interface InsurancePolicy {
  id: number;
  month: string;
  clientId: number;
  clientName: string;
  pan: string | null;
  clientCode: string;
  rmId: number;
  rmName: string;
  branchName: string | null;
  region: string | null;
  rd: string | null;
  pospId: number | null;
  pospName: string | null;
  insurer: string;
  category: InsuranceCategory;
  subType: string | null;
  productName: string;
  policyNo: string;
  businessType: InsuranceBusinessType;
  ppt: string | null;
  policyTerm: number | null;
  sumAssured: number | null;
  odPremium: number | null;
  tpPremium: number | null;
  totalPremium: number;
  netPremium: number;
  expectedPercent: number | null;
  expectedComm: number;
  expectedAsGiven: boolean;
  actualPercent: number | null;
  actualComm: number | null;
  diffPercent: number | null;
  diffComm: number | null;
  pospShare: number | null;
  pospPercent: number | null;
  pospComm: number;
  pospAsGiven: boolean;
  remarks: string | null;
  source: 'SCREEN' | 'UPLOAD';
  uploadRow: number | null;
  status: 'ACTIVE' | 'CANCELLED';
  createdBy: string;
  /** Sent back on a save so a change by someone else since is not overwritten (R-99). */
  updatedAt: string | null;
}
