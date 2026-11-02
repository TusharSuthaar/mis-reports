import { describe, expect, it } from 'vitest';
import { fakeApi } from '../test/fakeApi';
import { api } from './client';
describe('saving an entry someone else may have changed (R-99)', () => {
    it('sends the version the form opened as If-Match, and nothing when there is none', async () => {
        document.cookie = 'XSRF-TOKEN=tok';
        const seen = [];
        fakeApi({
            'PUT /api/bonds/7': (init) => {
                seen.push(new Headers(init?.headers).get('If-Match'));
                return { status: 200, body: {} };
            },
        });
        await api('/api/bonds/7', { method: 'PUT', body: {}, ifMatch: '2026-10-19T10:17:31.498137Z' });
        await api('/api/bonds/7', { method: 'PUT', body: {} });
        expect(seen).toEqual(['2026-10-19T10:17:31.498137Z', null]);
    });
});
