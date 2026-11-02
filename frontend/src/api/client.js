/** Thin fetch wrapper: same-origin session cookie, CSRF header on writes, RFC 7807 errors as ApiError. */
export class ApiError extends Error {
    status;
    fieldErrors;
    reference;
    constructor(status, message, fieldErrors = {}, 
    /** The request's reference: the same one is in the server logs. Quote it when reporting a problem. */
    reference = null) {
        super(message);
        this.status = status;
        this.fieldErrors = fieldErrors;
        this.reference = reference;
    }
}
let sessionExpired = () => { };
const writeListeners = [];
/** Called after every successful save (any method but GET), e.g. to forget cached lists. */
export function onWrite(handler) {
    writeListeners.push(handler);
}
/** Called when the server says we are no longer signed in (401) on any call except the sign-in checks. */
export function onSessionExpired(handler) {
    sessionExpired = handler;
}
function csrfCookie() {
    const entry = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='));
    return entry ? decodeURIComponent(entry.substring('XSRF-TOKEN='.length)) : undefined;
}
async function csrfToken() {
    let token = csrfCookie();
    if (!token) {
        await fetch('/api/auth/csrf', { credentials: 'same-origin' });
        token = csrfCookie();
    }
    return token ?? '';
}
/** {@code ifMatch}: the entry's updatedAt as the form opened it; the server refuses the save if someone saved since (R-99). */
export async function api(path, options = {}) {
    const method = options.method ?? 'GET';
    const headers = { Accept: 'application/json' };
    if (method !== 'GET')
        headers['X-XSRF-TOKEN'] = await csrfToken();
    const isForm = options.body instanceof FormData;
    if (options.body !== undefined && !isForm)
        headers['Content-Type'] = 'application/json';
    if (options.ifMatch)
        headers['If-Match'] = options.ifMatch;
    const res = await fetch(path, {
        method,
        headers,
        credentials: 'same-origin',
        body: options.body === undefined ? undefined : isForm ? options.body : JSON.stringify(options.body),
    });
    if (!res.ok) {
        if (res.status === 401 && path !== '/api/auth/me' && path !== '/api/auth/login')
            sessionExpired();
        const problem = await res.json().catch(() => null);
        const reference = problem?.reference ?? res.headers.get('X-Request-Id');
        throw new ApiError(res.status, problem?.detail ?? res.statusText ?? 'Request failed', problem?.errors ?? {}, reference);
    }
    if (method !== 'GET')
        writeListeners.forEach((h) => h());
    if (res.status === 204)
        return undefined;
    return (await res.json());
}
/** A readable message for any error thrown by {@link api}, including per-field validation errors. */
export function errorText(e) {
    if (e instanceof ApiError) {
        const fields = Object.entries(e.fieldErrors).map(([f, m]) => `${f}: ${m}`);
        if (fields.length)
            return fields.join('; ');
        // Server faults: make sure the reference is on screen (the server's own 500 message already names it).
        if (e.status >= 500 && e.reference && !e.message.includes(e.reference))
            return `${e.message} (reference ${e.reference})`;
        return e.message;
    }
    return 'Something went wrong';
}
