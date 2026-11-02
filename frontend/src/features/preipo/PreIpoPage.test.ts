import { describe, expect, it } from 'vitest';
import { linkedPrices, preIpoRevenue } from './PreIpoPage';

describe('Pre-IPO prices (R-93)', () => {
  it('fills mark-up, mark-up % and client price from any one of them on a buy', () => {
    // As in the register: base 51, mark-up 2.04 a share = 4%, client pays 53.04.
    expect(linkedPrices('BUY', 51, 'pct', 4)).toEqual({ markup: 2.04, markupPercent: 4, clientPrice: 53.04 });
    expect(linkedPrices('BUY', 51, 'markup', 2.04)).toEqual({ markup: 2.04, markupPercent: 4, clientPrice: 53.04 });
    expect(linkedPrices('BUY', 51, 'client', 53.04)).toEqual({ markup: 2.04, markupPercent: 4, clientPrice: 53.04 });
  });

  it('takes the mark-up off on a buyback', () => {
    // The vendor pays 100, our 5% comes off: the client gets 95.
    expect(linkedPrices('BUYBACK', 100, 'pct', 5)).toEqual({ markup: 5, markupPercent: 5, clientPrice: 95 });
    expect(linkedPrices('BUYBACK', 100, 'client', 95)).toEqual({ markup: 5, markupPercent: 5, clientPrice: 95 });
    expect(preIpoRevenue('BUYBACK', 50, 95, 100, 18, true, 5)).toMatchObject({ brokerage: 250, gross: 211.86, tds: 12.5, net: 237.5 });
  });

  it('needs a base price', () => {
    expect(linkedPrices('BUY', 0, 'pct', 4)).toBeNull();
  });
});
