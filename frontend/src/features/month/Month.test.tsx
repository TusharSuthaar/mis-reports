import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { App } from '../../App';
import type { FinesseMasterStatus, Job, Me, MonthRow, StageResult, UploadInfo, UploadReview } from '../../api/types';
import { AuthProvider } from '../../app/auth';
import { fakeApi } from '../../test/fakeApi';
import { monthLabel } from './MonthPage';

const admin: Me = { id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN' };
const ok = (body: unknown) => () => ({ status: 200, body });
const masterLoaded: FinesseMasterStatus = { codes: 3237, withPan: 3169, withGuardianPan: 69, updatedAt: '2026-10-12T08:00:00Z', updatedBy: 'admin' };

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}

const upload: UploadInfo = {
  id: 5, fileName: 'MFBrokerage.xlsx', status: 'STAGED', month: '2026-08', periodId: 2,
  fileTotals: { gross: 1180001.18, gst: 180000.18, net: 1000001 }, rows: 2484, newClients: 4, warnings: 1,
  uploadedBy: 'admin', uploadedAt: '2026-10-12T00:00:00Z', committedBy: null, committedAt: null,
};

const review: UploadReview = {
  upload, codes: 2484, clients: 2440, byPan: 2421, byGuardianPan: 59, byName: 5,
  notInMaster: [{ clientId: 9, codes: 'FC09999', name: 'CLIENT NOT IN MASTER', pan: 'ABCDE****F', reason: 'PAN not in Master MIS', net: 32.5 }],
  canSave: true, correction: false,
};

describe('this month', () => {
  it('formats months', () => {
    expect(monthLabel('2026-08')).toBe('August 2026');
  });

  it('uploads the Finesse brokerage file, highlights clients not in Master MIS, and saves the month', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    let sentMonth: string | null = null;
    let committed = false;
    const job: Job<StageResult> = {
      id: 4, type: 'income-upload', status: 'DONE', error: null,
      result: { uploadId: 5, errors: [], warnings: 1, notes: ['Brokerage for 2026-07 (2610.00) is in this file and is counted in 2026-08'] },
    };
    fakeApi({
      'GET /api/auth/me': ok(admin),
      'GET /api/months': ok([]),
      'GET /api/finesse/client-master': ok(masterLoaded),
      'POST /api/uploads': (init) => {
        sentMonth = (init?.body as FormData).get('month') as string;
        return { status: 202, body: { jobId: 4 } };
      },
      'GET /api/jobs/4': ok(job),
      'GET /api/uploads/5/review': ok(review),
      'POST /api/uploads/5/commit': () => {
        committed = true;
        return { status: 200, body: { uploadId: 5, month: '2026-08', lines: { lines: 2440, gross: 1, gst: 1, net: 1000001 }, replacedUploadId: null } };
      },
    });
    renderAt('/month');

    expect(await screen.findByText(/3,237 codes/)).toBeInTheDocument();
    const monthInput = screen.getByLabelText('Month');
    await userEvent.clear(monthInput);
    await userEvent.type(monthInput, '2026-08');
    await userEvent.upload(screen.getByLabelText('Finesse brokerage file'), new File(['x'], 'aug.xlsx'));
    await userEvent.click(screen.getByRole('button', { name: 'Upload brokerage' }));

    expect(await screen.findByText('2,421')).toBeInTheDocument();
    expect(sentMonth).toBe('2026-08');
    expect(screen.getByText('59')).toBeInTheDocument();
    expect(screen.getByText(/1 clients are not in Master MIS/)).toBeInTheDocument();
    expect(screen.getByText('CLIENT NOT IN MASTER')).toBeInTheDocument();
    expect(screen.getByText(/counted in 2026-08/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Download this list (Excel)' })).toHaveAttribute('href', '/api/uploads/5/not-in-master.xlsx');

    await userEvent.click(screen.getByRole('button', { name: 'Save August 2026' }));
    await screen.findByText('No month uploaded yet.');
    expect(committed).toBe(true);
  });

  it('uploads the Finesse client master', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    let loaded = false;
    fakeApi({
      'GET /api/auth/me': ok(admin),
      'GET /api/months': ok([]),
      'GET /api/finesse/client-master': () => ({ status: 200, body: loaded ? masterLoaded : { codes: 0, withPan: 0, withGuardianPan: 0, updatedAt: null, updatedBy: null } }),
      'POST /api/finesse/client-master': () => {
        loaded = true;
        return { status: 200, body: { fileName: 'c.xlsx', rows: 3237, added: 3237, updated: 0, unchanged: 0, withPan: 3169, withGuardianPan: 69, withoutAnyPan: 0, invalidPans: 0 } };
      },
    });
    renderAt('/month');
    expect(await screen.findByText('Not loaded yet. Upload it before the brokerage file.')).toBeInTheDocument();
    await userEvent.upload(screen.getByLabelText('Finesse client master file'), new File(['x'], 'ClientMasterReport.xlsx'));
    await userEvent.click(screen.getByRole('button', { name: 'Upload client master' }));
    expect(await screen.findByText(/3,237 codes read: 3237 new/)).toBeInTheDocument();
    expect(await screen.findByText(/3,169 with PAN/)).toBeInTheDocument();
  });

  it('blocks Process while clients are not in Master MIS', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    const row: MonthRow = {
      periodId: 2, month: '2026-08', status: 'OPEN', saved: { lines: 2440, gross: 1180001.18, gst: 180000.18, net: 1000001 },
      savedUpload: { ...upload, status: 'COMMITTED' }, pendingUpload: null, processedBy: null, processedAt: null, lockedBy: null, lockedAt: null,
      notInMaster: 4,
    };
    fakeApi({
      'GET /api/auth/me': ok(admin),
      'GET /api/months': ok([row]),
      'GET /api/finesse/client-master': ok(masterLoaded),
      'GET /api/uploads/5/review': ok({ ...review, canSave: false }),
    });
    renderAt('/month');
    expect(await screen.findByText('4 not in Master MIS')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Process' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Fix 4 clients, then process' }));
    expect(await screen.findByText('CLIENT NOT IN MASTER')).toBeInTheDocument();
  });

  it('lets FINANCE lock a processed month and shows the split', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    let locked = false;
    const finance: Me = { id: 2, username: 'fin', displayName: 'Fin', role: 'FINANCE' };
    const row = (status: MonthRow['status']): MonthRow => ({
      periodId: 2, month: '2026-08', status, saved: { lines: 2439, gross: 1180000.118, gst: 180000.018, net: 1000000.1 },
      savedUpload: { ...upload, status: 'COMMITTED', month: '2026-08' }, pendingUpload: null,
      processedBy: 'admin', processedAt: null, lockedBy: status === 'LOCKED' ? 'fin' : null, lockedAt: null, notInMaster: 0,
    });
    fakeApi({
      'GET /api/auth/me': ok(finance),
      'GET /api/months': () => ({ status: 200, body: [row(locked ? 'LOCKED' : 'PROCESSED')] }),
      'GET /api/finesse/client-master': ok(masterLoaded),
      'POST /api/months/2/lock': () => {
        locked = true;
        return { status: 200, body: {} };
      },
      'GET /api/months/2/summary': ok({
        periodId: 2, month: '2026-08', status: 'PROCESSED', lines: 2439, adjustments: 0, net: 1000000.1,
        subBrokers: 700000, referrals: 50000, epsilon: 250000.1,
        byPartner: [{ partnerId: 1, partnerName: 'ALPHA WEALTH', role: 'SUB_BROKER', clients: 170, amount: 99000 }],
        byBranch: [],
      }),
    });
    renderAt('/month');

    expect(await screen.findByText('Processed')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Process again' })).not.toBeInTheDocument(); // FINANCE does not process
    expect(screen.queryByLabelText('Finesse brokerage file')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'END file (draft)' })).toHaveAttribute('href', '/api/months/2/end-file');
    await userEvent.click(screen.getByRole('button', { name: 'Show' }));
    expect(await screen.findByText('2,50,000.10')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Lock' }));
    expect(await screen.findByText('Locked')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Lock' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'END file' })).toHaveAttribute('href', '/api/months/2/end-file');
  });

  it('shows why a file was not accepted', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    const job: Job<StageResult> = {
      id: 4, type: 'income-upload', status: 'DONE', error: null,
      result: { uploadId: null, errors: [{ row: 0, message: 'Rows add up to 100.00 but the Total row says 1.00' }], warnings: 0, notes: [] },
    };
    fakeApi({
      'GET /api/auth/me': ok(admin),
      'GET /api/months': ok([]),
      'GET /api/finesse/client-master': ok(masterLoaded),
      'POST /api/uploads': () => ({ status: 202, body: { jobId: 4 } }),
      'GET /api/jobs/4': ok(job),
    });
    renderAt('/month');
    await userEvent.upload(await screen.findByLabelText('Finesse brokerage file'), new File(['x'], 'bad.xlsx'));
    await userEvent.click(screen.getByRole('button', { name: 'Upload brokerage' }));
    expect(await screen.findByText('Rows add up to 100.00 but the Total row says 1.00')).toBeInTheDocument();
  });
});
