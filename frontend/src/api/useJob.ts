import { useEffect, useRef, useState } from 'react';
import { api, errorText } from './client';
import type { Job } from './types';

const POLL_MS = 1000;

/** Polls /api/jobs/{id} until the job is DONE or FAILED. Pass null to stop watching. */
export function useJob<R>(jobId: number | null) {
  const [job, setJob] = useState<Job<R> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const timer = useRef<number | undefined>(undefined);

  useEffect(() => {
    setJob(null);
    setError(null);
    if (jobId === null) return;
    let cancelled = false;
    const poll = async () => {
      try {
        const j = await api<Job<R>>(`/api/jobs/${jobId}`);
        if (cancelled) return;
        setJob(j);
        if (j.status === 'QUEUED' || j.status === 'RUNNING') timer.current = window.setTimeout(poll, POLL_MS);
      } catch (e) {
        if (!cancelled) setError(errorText(e));
      }
    };
    poll();
    return () => {
      cancelled = true;
      window.clearTimeout(timer.current);
    };
  }, [jobId]);

  const running = jobId !== null && (job === null || job.status === 'QUEUED' || job.status === 'RUNNING') && !error;
  return { job, error, running };
}
