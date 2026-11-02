import { vi } from 'vitest';
/** Stubs global fetch with a table of "METHOD /path" → response. Unknown routes return 404. */
export function fakeApi(routes) {
    const fetchMock = vi.fn(async (input, init) => {
        const url = new URL(String(input), 'http://localhost');
        const key = `${init?.method ?? 'GET'} ${url.pathname}`;
        const handler = routes[key];
        const { status, body } = handler ? handler(init) : { status: 404, body: { detail: 'not found' } };
        return new Response(body === undefined ? null : JSON.stringify(body), {
            status,
            headers: { 'Content-Type': 'application/json' },
        });
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}
