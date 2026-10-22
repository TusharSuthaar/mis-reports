import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';
import { clearLoadCache } from '../api/useLoad';
import { setAmountUnit } from '../components/Amount';

// Tests check exact rupee figures.
beforeEach(() => setAmountUnit('full'));

afterEach(() => {
  cleanup();
  clearLoadCache();
  vi.unstubAllGlobals();
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
});
