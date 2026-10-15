import type { Role } from '../api/types';

/** Mirrors the backend: masters are read by ADMIN, HO, FINANCE and changed by ADMIN, HO. */
export const MASTER_READ: Role[] = ['ADMIN', 'HO', 'FINANCE'];
export const MASTER_WRITE: Role[] = ['ADMIN', 'HO'];

export function canEditMasters(role: Role | undefined): boolean {
  return !!role && MASTER_WRITE.includes(role);
}
