import { describe, expect, it } from 'vitest';
import { groupOpsFields } from './OpsMaster';

describe('ops master', () => {
  it('groups the Ops columns, keeping their order, and puts unknown ones under Other (R-08)', () => {
    const groups = groupOpsFields([
      { label: 'Family Name', value: 'X' },
      { label: 'Email', value: 'a@example.com' },
      { label: 'Phone Number', value: '9000000000' },
      { label: 'BSE Login Status', value: 'Active' },
      { label: 'Some New Column', value: 'y' },
    ]);
    expect(groups.map((g) => g.title)).toEqual(['Contact', 'Family', 'Exchanges, mandates and KYC', 'Other']);
    expect(groups[0].fields.map((f) => f.label)).toEqual(['Email', 'Phone Number']);
    expect(groups[3].fields[0].label).toBe('Some New Column');
  });
});
