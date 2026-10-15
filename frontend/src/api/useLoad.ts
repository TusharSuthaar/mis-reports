import { useCallback, useEffect, useState } from 'react';
import { api, errorText } from './client';

/** GETs {@code path} (skipped while null) and exposes the data, an error message and a reload function. */
export function useLoad<T>(path: string | null) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(() => {
    if (!path) return;
    setError(null);
    api<T>(path)
      .then(setData)
      .catch((e) => setError(errorText(e)));
  }, [path]);

  useEffect(reload, [reload]);
  return { data, error, reload };
}
