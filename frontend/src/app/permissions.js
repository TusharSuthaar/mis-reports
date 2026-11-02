/** Mirrors the backend: masters are read by ADMIN, HO, FINANCE and changed by ADMIN, HO. */
export const MASTER_READ = ['ADMIN', 'HO', 'FINANCE'];
export const MASTER_WRITE = ['ADMIN', 'HO'];
export function canEditMasters(role) {
    return !!role && MASTER_WRITE.includes(role);
}
