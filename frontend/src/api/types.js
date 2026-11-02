export const ROLES = ['RM', 'RD', 'HO', 'FINANCE', 'ADMIN'];
export const PARTNER_TYPES = ['SUB_BROKER', 'REFERRAL', 'POSP'];
export const PARTNER_TYPE_LABEL = {
    SUB_BROKER: 'Sub-broker',
    REFERRAL: 'Referral partner',
    POSP: 'POSP',
};
export const PARTNER_STATUS_LABEL = {
    ACTIVE: 'Active',
    PENDING: 'Pending approval',
    INACTIVE: 'Inactive',
};
export const PRODUCTS = [
    { code: 'MF', label: 'MF' },
    { code: 'PREIPO', label: 'Unlisted' },
    { code: 'FD', label: 'FD' },
    { code: 'BOND', label: 'Bonds' },
    { code: 'LOAN', label: 'Loans' },
    { code: 'EQUITY', label: 'Equity' },
    { code: 'PMS', label: 'PMS' },
    { code: 'AIF', label: 'AIF' },
    { code: 'ADVISORY', label: 'Advisory' },
    { code: 'GIFT', label: 'GIFT' },
    { code: 'NPS', label: 'NPS' },
    { code: 'P2P', label: 'P2P' },
    { code: 'INS', label: 'Insurance' },
];
export const COMPOUNDING_LABEL = { QUARTERLY: 'Quarterly', HALF_YEARLY: 'Half-yearly', YEARLY: 'Yearly' };
export const INTEREST_FREQUENCY_LABEL = {
    MONTHLY: 'Monthly',
    QUARTERLY: 'Quarterly',
    HALF_YEARLY: 'Half-yearly',
    YEARLY: 'Yearly',
    CUMULATIVE: 'Cumulative (at maturity)',
};
export const CERTIFICATE_LABEL = { PENDING: 'Pending', RECEIVED: 'Received', NOT_APPLICABLE: 'Not applicable' };
export const PAYMENT_LABEL = { PENDING: 'Pending', RECEIVED: 'Received', RECEIVED_GST_PENDING: 'Received – GST pending' };
// Each product's own types live next to it (audit M-06); re-exported here so every import keeps working.
export * from '../features/fd/types';
export * from '../features/bonds/types';
export * from '../features/aif/types';
export * from '../features/loans/types';
export * from '../features/gift/types';
export * from '../features/pms/types';
export * from '../features/nps/types';
export * from '../features/p2p/types';
export * from '../features/insurance/types';
