import { describe, expect, it } from 'vitest';
import { safeReturnPath } from './LoginPage';

describe('after signing in', () => {
  it('goes back only to a page of this app', () => {
    expect(safeReturnPath('/month')).toBe('/month');
    expect(safeReturnPath('/products?tab=INS')).toBe('/products?tab=INS');
    expect(safeReturnPath(undefined)).toBe('/');
    expect(safeReturnPath('//evil.example')).toBe('/');
    expect(safeReturnPath('/\\evil.example')).toBe('/');
    expect(safeReturnPath('https://evil.example')).toBe('/');
    expect(safeReturnPath('/x\u0000y')).toBe('/');
  });
});
