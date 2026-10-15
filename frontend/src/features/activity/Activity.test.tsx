import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { ApiError, errorText } from '../../api/client';
import type { Activity, ActivitySummary, Page } from '../../api/types';
import { jobErrorText } from '../../api/useJob';
import { fakeApi } from '../../test/fakeApi';
import { activityQuery, ActivityPage, took } from './ActivityPage';

const FAILED_UPLOAD: Activity = {
  kind: 'JOB',
  id: 7,
  title: 'Uploaded a brokerage file',
  detail: 'Sheet "Client Wise Summary" not found',
  result: 'FAILED',
  by: 'asha',
  at: '2026-10-15T05:00:00Z',
  finishedAt: '2026-10-15T05:00:12Z',
  reference: '7K2MQX4P',
};

const LOCKED: Activity = {
  kind: 'EVENT',
  id: 3,
  title: 'Locked a month',
  detail: '2026-08',
  result: 'DONE',
  by: 'admin',
  at: '2026-10-14T10:00:00Z',
  finishedAt: null,
  reference: null,
};

describe('Activity screen', () => {
  it('lists what happened in plain words, with the reference to quote', async () => {
    const page: Page<Activity> = { items: [FAILED_UPLOAD, LOCKED], page: 0, size: 50, total: 2 };
    const summary: ActivitySummary = { runningJobs: 0, failedJobs: 1, errors: 0 };
    const fetch = fakeApi({
      'GET /api/activity': () => ({ status: 200, body: page }),
      'GET /api/activity/summary': () => ({ status: 200, body: summary }),
    });
    render(<ActivityPage />);

    expect(await screen.findByText('Uploaded a brokerage file')).toBeInTheDocument();
    expect(screen.getByText('Locked a month')).toBeInTheDocument();
    expect(screen.getByText('7K2MQX4P')).toBeInTheDocument();
    expect(screen.getByText('Failed')).toBeInTheDocument();
    expect(screen.getByText('12 s')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /Failed jobs, last 7 days/ }));
    const lastUrl = String(fetch.mock.calls.filter((c) => String(c[0]).startsWith('/api/activity?')).at(-1)?.[0]);
    expect(lastUrl).toContain('problemsOnly=true');
  });

  it('builds the query from the filters', () => {
    const q = new URLSearchParams(
      activityQuery({ kind: 'ERROR', problemsOnly: true, by: ' asha ', reference: '', from: '', to: '2026-10-08' }, 1),
    );
    expect(q.get('kind')).toBe('ERROR');
    expect(q.get('problemsOnly')).toBe('true');
    expect(q.get('by')).toBe('asha');
    expect(q.has('reference')).toBe(false);
    expect(new Date(q.get('to')!).getTime()).toBe(new Date('2026-10-09T00:00:00').getTime());
    expect(took({ at: '2026-10-15T05:00:00Z', finishedAt: '2026-10-15T05:03:00Z' })).toBe('3 min');
  });
});

describe('error references', () => {
  it('server faults always show their reference; ordinary refusals do not need it', () => {
    expect(errorText(new ApiError(500, 'Gateway broke', {}, 'ABCD2345'))).toBe('Gateway broke (reference ABCD2345)');
    expect(errorText(new ApiError(500, 'Please share reference ABCD2345', {}, 'ABCD2345'))).toBe(
      'Please share reference ABCD2345',
    );
    expect(errorText(new ApiError(409, 'Month is locked', {}, 'ABCD2345'))).toBe('Month is locked');
    expect(jobErrorText({ error: 'boom', reference: 'TESTREF7' })).toBe('boom (reference TESTREF7)');
  });
});
