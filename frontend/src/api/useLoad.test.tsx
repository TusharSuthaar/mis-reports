import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { api } from './client';
import { useLoad } from './useLoad';

/** fetch stub whose answers the test releases one by one. */
function controlledFetch() {
  const pending = new Map<string, (body: unknown) => void>();
  const fetchMock = vi.fn(
    (input: RequestInfo | URL, init?: RequestInit) =>
      new Promise<Response>((resolve) => {
        const key = `${init?.method ?? 'GET'} ${new URL(String(input), 'http://localhost').pathname}${new URL(String(input), 'http://localhost').search}`;
        pending.set(key, (body) => resolve(new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })));
      }),
  );
  vi.stubGlobal('fetch', fetchMock);
  return { fetchMock, answer: (key: string, body: unknown) => pending.get(key)!(body) };
}

describe('useLoad', () => {
  it('drops a slow answer for an earlier filter that arrives after the current one', async () => {
    const { answer } = controlledFetch();
    const { result, rerender } = renderHook(({ path }) => useLoad<string>(path), { initialProps: { path: '/api/x?month=2026-07' } });
    rerender({ path: '/api/x?month=2026-08' });

    await act(async () => answer('GET /api/x?month=2026-08', 'august'));
    await waitFor(() => expect(result.current.data).toBe('august'));
    await act(async () => answer('GET /api/x?month=2026-07', 'july'));

    expect(result.current.data).toBe('august');
    expect(result.current.loading).toBe(false);
  });

  it('shares one call between parts of a screen asking for the same list, and asks again after a save', async () => {
    const { fetchMock, answer } = controlledFetch();
    const a = renderHook(() => useLoad<string[]>('/api/employees'));
    const b = renderHook(() => useLoad<string[]>('/api/employees'));
    await act(async () => answer('GET /api/employees', ['Asha']));
    await waitFor(() => expect(b.result.current.data).toEqual(['Asha']));
    expect(a.result.current.data).toEqual(['Asha']);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    document.cookie = 'XSRF-TOKEN=t';
    const saved = api('/api/employees', { method: 'POST', body: {} });
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    await act(async () => answer('POST /api/employees', {}));
    await saved;
    renderHook(() => useLoad<string[]>('/api/employees'));
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });
});
