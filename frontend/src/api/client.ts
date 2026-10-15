/** Thin fetch wrapper: same-origin session cookie, CSRF header on writes, RFC 7807 errors as ApiError. */

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly fieldErrors: Record<string, string> = {},
    /** The request's reference: the same one is in the server logs. Quote it when reporting a problem. */
    readonly reference: string | null = null,
  ) {
    super(message);
  }
}

type Method = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';

let sessionExpired: () => void = () => {};

/** Called when the server says we are no longer signed in (401) on any call except the sign-in checks. */
export function onSessionExpired(handler: () => void) {
  sessionExpired = handler;
}

function csrfCookie(): string | undefined {
  const entry = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='));
  return entry ? decodeURIComponent(entry.substring('XSRF-TOKEN='.length)) : undefined;
}

async function csrfToken(): Promise<string> {
  let token = csrfCookie();
  if (!token) {
    await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    token = csrfCookie();
  }
  return token ?? '';
}

export async function api<T>(path: string, options: { method?: Method; body?: unknown } = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = await csrfToken();
  const isForm = options.body instanceof FormData;
  if (options.body !== undefined && !isForm) headers['Content-Type'] = 'application/json';

  const res = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: options.body === undefined ? undefined : isForm ? (options.body as FormData) : JSON.stringify(options.body),
  });

  if (!res.ok) {
    if (res.status === 401 && path !== '/api/auth/me' && path !== '/api/auth/login') sessionExpired();
    const problem = await res.json().catch(() => null);
    const reference: string | null = problem?.reference ?? res.headers.get('X-Request-Id');
    throw new ApiError(res.status, problem?.detail ?? res.statusText ?? 'Request failed', problem?.errors ?? {}, reference);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

/** A readable message for any error thrown by {@link api}, including per-field validation errors. */
export function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    const fields = Object.entries(e.fieldErrors).map(([f, m]) => `${f}: ${m}`);
    if (fields.length) return fields.join('; ');
    // Server faults: make sure the reference is on screen (the server's own 500 message already names it).
    if (e.status >= 500 && e.reference && !e.message.includes(e.reference)) return `${e.message} (reference ${e.reference})`;
    return e.message;
  }
  return 'Something went wrong';
}
