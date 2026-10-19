import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { Job } from '../api/types';
import { JobProgressBar, percentOf } from './JobProgressBar';

const job = (progress: Job['progress']): Job => ({
  id: 1, type: 'share-load', title: 'Loaded', status: 'RUNNING', result: null, error: null, reference: null, startedAt: null, progress,
});

describe('job progress bar', () => {
  it('works out the percent across all steps', () => {
    expect(percentOf({ step: 1, steps: 4, label: 'Reading', done: 0, total: 0 })).toBe(0);
    expect(percentOf({ step: 2, steps: 4, label: 'Clients', done: 50, total: 100 })).toBe(38);
    expect(percentOf({ step: 4, steps: 4, label: 'Links', done: 10, total: 10 })).toBe(100);
    expect(percentOf(null)).toBeNull();
  });

  it('shows the step, the items done and the percent', () => {
    render(<JobProgressBar job={job({ step: 2, steps: 4, label: 'Adding clients and setting their RM', done: 1200, total: 2400 })} />);
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '38');
    expect(screen.getByText(/Step 2 of 4 · Adding clients and setting their RM · 1,200 of 2,400/)).toBeInTheDocument();
  });

  it('moves without a percent until the job reports', () => {
    render(<JobProgressBar job={job(null)} label="Loading the Master MIS…" />);
    expect(screen.getByRole('progressbar')).not.toHaveAttribute('aria-valuenow');
    expect(screen.getByText(/Loading the Master MIS…/)).toBeInTheDocument();
  });
});
