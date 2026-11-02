import { describe, expect, it } from 'vitest';
import { insuranceFigures } from './InsurancePage';

describe('insuranceFigures (R-95)', () => {
  it('works out expected = net × % and the POSP at expected % × agreed %', () => {
    const f = insuranceFigures(12345.67, 17.5, null, true, 40, null);
    expect(f.expected).toBe(2160.49);
    expect(f.pospPct).toBe(7);
    expect(f.posp).toBe(864.2);
    expect(f.ours).toBe(1296.29);
  });
  it('takes typed amounts instead of the calculation', () => {
    const f = insuranceFigures(10000, 15, 1400, true, 50, 600);
    expect(f.expected).toBe(1400);
    expect(f.posp).toBe(600);
  });
  it('shares nothing without a POSP', () => {
    expect(insuranceFigures(10000, 15, null, false, 50, null).posp).toBe(0);
  });
});
