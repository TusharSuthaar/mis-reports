import { describe, expect, it } from 'vitest';
import { formatAmount } from './format';

describe('formatAmount', () => {
  it('shows lakhs, thousands or exact rupees', () => {
    expect(formatAmount(1234567, 'L')).toBe('12.35 L');
    expect(formatAmount(650000, 'L')).toBe('6.50 L');
    expect(formatAmount(5932.2, 'K')).toBe('5.93 K');
    expect(formatAmount(1234567, 'K')).toBe('1,234.57 K');
    expect(formatAmount(1234567, 'full')).toBe('12,34,567.00');
    expect(formatAmount(null, 'L')).toBe('');
  });
});
