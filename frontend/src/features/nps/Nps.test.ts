import { describe, expect, it } from 'vitest';
import { npsFigures } from './NpsPage';

describe('nps', () => {
  it('works out the revenue like the server (R-86), GST shown as CGST + SGST', () => {
    expect(npsFigures(2000, 60, 0, 18)).toEqual({ revenue: 1200, cgst: 108, sgst: 108, gst: 216, invoice: 1416 });
    expect(npsFigures(null, null, 213.5, 18)).toEqual({ revenue: 213.5, cgst: 19.22, sgst: 19.21, gst: 38.43, invoice: 251.93 });
  });
});
