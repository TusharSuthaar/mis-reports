import { useEffect, useRef, useState } from 'react';
import { api, errorText } from './client';
const POLL_MS = 1000;
/** Why a job failed, with the reference to quote (it is on every log line of the job). */
export function jobErrorText(job) {
    const error = job.error ?? 'Unknown error';
    return job.reference ? `${error} (reference ${job.reference})` : error;
}
/** Polls /api/jobs/{id} until the job is DONE or FAILED. Pass null to stop watching. */
export function useJob(jobId) {
    const [job, setJob] = useState(null);
    const [error, setError] = useState(null);
    const timer = useRef(undefined);
    useEffect(() => {
        setJob(null);
        setError(null);
        if (jobId === null)
            return;
        let cancelled = false;
        const poll = async () => {
            try {
                const j = await api(`/api/jobs/${jobId}`);
                if (cancelled)
                    return;
                setJob(j);
                if (j.status === 'QUEUED' || j.status === 'RUNNING')
                    timer.current = window.setTimeout(poll, POLL_MS);
            }
            catch (e) {
                if (!cancelled)
                    setError(errorText(e));
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
