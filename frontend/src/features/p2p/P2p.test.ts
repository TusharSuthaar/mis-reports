import { describe, expect, it } from 'vitest';
import { p2pFigures } from './P2pPage';

describe('p2p', () => {
  it('works out the brokerage figures like the server (R-87)', () => {
    expect(p2pFigures(1500, true, 2, 18)).toEqual({ gross: 1500, tds: 30, gst: 270, net: 1470, invoice: 1770 });
    expect(p2pFigures(0, false, 0, 18)).toEqual({ gross: 0, tds: 0, gst: 0, net: 0, invoice: 0 });
  });
});
