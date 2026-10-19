import { useEffect, useState } from 'react';
import type { Job, JobProgress } from '../api/types';

/** 0–100 across all steps, or null when the job has not said how far it is. */
export function percentOf(p: JobProgress | null | undefined): number | null {
  if (!p || p.steps < 1) return null;
  const inStep = p.total > 0 ? Math.min(p.done / p.total, 1) : 0;
  return Math.round(((Math.max(p.step, 1) - 1 + inStep) / p.steps) * 100);
}

function elapsed(since: string | null | undefined, now: number): string {
  if (!since) return '';
  const s = Math.max(0, Math.floor((now - new Date(since).getTime()) / 1000));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

/** Progress bar for a background job: step, items done and time taken. Without progress data it shows a moving bar. */
export function JobProgressBar({ job, label = 'Working…' }: { job: Job<unknown> | null; label?: string }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, []);
  const p = job?.progress ?? null;
  const percent = percentOf(p);
  const parts = [
    p && p.steps > 1 ? `Step ${p.step} of ${p.steps}` : null,
    p?.label ?? (job?.status === 'QUEUED' ? 'Waiting to start…' : label),
    p && p.total > 0 ? `${p.done.toLocaleString('en-IN')} of ${p.total.toLocaleString('en-IN')}` : null,
    elapsed(job?.startedAt, now) || null,
  ].filter(Boolean);
  return (
    <div className="job-progress">
      <div
        className={percent === null ? 'job-progress-track indeterminate' : 'job-progress-track'}
        role="progressbar"
        aria-label={p?.label ?? label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent ?? undefined}
      >
        <div className="job-progress-fill" style={percent === null ? undefined : { width: `${percent}%` }} />
      </div>
      <p className="muted">
        {percent !== null && <strong>{percent}% · </strong>}
        {parts.join(' · ')}
      </p>
    </div>
  );
}
