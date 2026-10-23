import { useCallback, useEffect, useRef, useState } from 'react';
import { api, errorText, onWrite } from './client';

/**
 * Recent GET answers by path. A screen opened again shows its last figures at once and refreshes them in the background;
 * any save (POST / PUT / PATCH / DELETE) empties it, so nothing shown after a change is from before it.
 */
const cache = new Map<string, { data: unknown; at: number }>();
/** Requests on their way, so several parts of a screen asking for the same list share one call. */
const inFlight = new Map<string, Promise<unknown>>();
/** An answer younger than this is used without asking again (e.g. the RM list loaded by several parts of one screen). */
const FRESH_MS = 10_000;

export function clearLoadCache() {
  cache.clear();
  inFlight.clear();
}
onWrite(clearLoadCache);

function fetchShared<T>(path: string): Promise<T> {
  let p = inFlight.get(path) as Promise<T> | undefined;
  if (!p) {
    p = api<T>(path)
      .then((data) => {
        cache.set(path, { data, at: Date.now() });
        return data;
      })
      .finally(() => inFlight.delete(path));
    inFlight.set(path, p);
  }
  return p;
}

/**
 * GETs {@code path} (skipped while null) and exposes the data, an error message, whether a newer answer is on its way,
 * and a reload function. Only the answer for the current path is ever shown: when filters change quickly, a slow answer
 * for an earlier filter arriving last is dropped instead of overwriting the newer one.
 */
export function useLoad<T>(path: string | null) {
  const hit = path ? cache.get(path) : undefined;
  const [data, setData] = useState<T | null>((hit?.data as T) ?? null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const latest = useRef(0);

  const load = useCallback(
    (force: boolean) => {
      if (!path) return;
      const ticket = ++latest.current;
      const cached = cache.get(path);
      if (cached) setData(cached.data as T);
      if (!force && cached && Date.now() - cached.at < FRESH_MS) {
        setLoading(false);
        return;
      }
      if (force) cache.delete(path);
      setError(null);
      setLoading(true);
      fetchShared<T>(path)
        .then((d) => {
          if (ticket === latest.current) setData(d);
        })
        .catch((e) => {
          if (ticket === latest.current) setError(errorText(e));
        })
        .finally(() => {
          if (ticket === latest.current) setLoading(false);
        });
    },
    [path],
  );

  const reload = useCallback(() => load(true), [load]);
  useEffect(() => {
    load(false);
    const tickets = latest;
    // A screen closed or its filter changed: an answer still on its way is no longer wanted.
    return () => {
      tickets.current++;
    };
  }, [load]);
  return { data, error, loading, reload };
}
