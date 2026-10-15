import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../../App';
import type { Employee, ImportResult, Job, Me, OrgChart, Partner, Role } from '../../api/types';
import { AuthProvider } from '../../app/auth';
import { orgRows } from './OrgPage';
import { fakeApi } from '../../test/fakeApi';

function me(role: Role): Me {
  return { id: 1, username: role.toLowerCase(), displayName: `A ${role}`, role };
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}

const ok = (body: unknown) => () => ({ status: 200, body });


const partner: Partner = {
  id: 7, type: 'SUB_BROKER', name: 'Zedd Xavier', pan: 'ABCDE****F', gstin: null, arnOrPospCode: 'ARN-1',
  bankRef: null, status: 'ACTIVE', currentRmLinks: 1,
};

describe('setup navigation', () => {
  it('shows Setup to FINANCE without admin screens or edit buttons', async () => {
    fakeApi({ 'GET /api/auth/me': ok(me('FINANCE')), 'GET /api/partners': ok([partner]) });
    renderAt('/setup/partners');

    expect(await screen.findByRole('heading', { name: 'Partners' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Org' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Users' })).not.toBeInTheDocument();
    expect(await screen.findByText('Zedd Xavier')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New partner' })).not.toBeInTheDocument();
  });

  it('merges a duplicate partner into the right spelling', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    let merged: unknown = null;
    const right: Partner = { ...partner, id: 19, type: 'REFERRAL', name: 'TEST PARTNER AGARWAL', currentRmLinks: 0 };
    const wrong: Partner = { ...partner, id: 59, type: 'REFERRAL', name: 'TEST PARTNER AGRAWAL', currentRmLinks: 0 };
    fakeApi({
      'GET /api/auth/me': ok(me('ADMIN')),
      'GET /api/partners': () => ({ status: 200, body: merged ? [right, { ...wrong, status: 'INACTIVE' }] : [right, wrong] }),
      'GET /api/shares/partner-counts': ok({ 59: 1 }),
      'GET /api/partners/59/rm-links': ok([]),
      'GET /api/employees': ok([]),
      'POST /api/partners/59/merge': (init?: RequestInit) => {
        merged = JSON.parse(String(init?.body));
        return { status: 200, body: { fromId: 59, intoId: 19, shares: 1, rmLinks: 0, aliases: 1 } };
      },
    });
    renderAt('/setup/partners');

    await userEvent.click(await screen.findByText('TEST PARTNER AGRAWAL'));
    await userEvent.selectOptions(await screen.findByLabelText('Merge into'), 'TEST PARTNER AGARWAL');
    await userEvent.click(screen.getByRole('button', { name: 'Merge' }));
    expect(merged).toMatchObject({ intoId: 19 });
    expect(await screen.findByRole('cell', { name: 'Inactive' })).toBeInTheDocument();
  });

  it('keeps Setup away from an RM', async () => {
    fakeApi({ 'GET /api/auth/me': ok(me('RM')) });
    renderAt('/setup/org');
    expect(await screen.findByRole('heading', { name: 'Not allowed' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Org' })).not.toBeInTheDocument();
  });
});

describe('org', () => {
  const chart: OrgChart = {
    date: '2026-10-08',
    regions: [
      {
        id: 1, name: 'Bihar & EUP', rd: { id: 5, empCode: 'E5', name: 'Rina Das' },
        branches: [
          { id: 10, name: 'PATNA', people: [{ id: 1, empCode: 'E1', name: 'RM BETA', type: 'RM' }, { id: 2, empCode: 'E2', name: 'STAFF', type: 'HOUSE' }] },
          { id: 11, name: 'SASARAM', people: [] },
        ],
      },
      { id: 2, name: 'Bhopal LLP', rd: null, branches: [] },
    ],
    branchesWithoutRegion: [],
    peopleWithoutBranch: [],
  };

  it('flattens the org into Region | RD | Branch | RM rows, keeping empty branches and regions', () => {
    const rows = orgRows(chart);
    expect(rows.map((r) => [r.region, r.rd, r.branch, r.rm])).toEqual([
      ['Bihar & EUP', 'Rina Das', 'PATNA', 'RM BETA'],
      ['Bihar & EUP', 'Rina Das', 'PATNA', 'STAFF'],
      ['Bihar & EUP', 'Rina Das', 'SASARAM', ''],
      ['Bhopal LLP', '', '', ''],
    ]);
    expect(rows[1].house).toBe(true);
  });

  it('shows one table and opens a simple "move from date" panel for an RM', async () => {
    const employees: Employee[] = [
      { id: 1, empCode: 'E1', name: 'RM BETA', type: 'RM', status: 'ACTIVE', joinedOn: null, exitedOn: null, branchId: 10, branchName: 'PATNA' },
    ];
    fakeApi({
      'GET /api/auth/me': ok(me('HO')),
      'GET /api/org': ok(chart),
      'GET /api/regions': ok([]),
      'GET /api/branches': ok([{ id: 10, name: 'PATNA', regionId: 1, regionName: 'Bihar & EUP' }, { id: 11, name: 'SASARAM', regionId: 1, regionName: 'Bihar & EUP' }]),
      'GET /api/employees': ok(employees),
      'GET /api/employees/1/branch': ok([]),
    });
    renderAt('/setup/org');

    expect(await screen.findByRole('button', { name: 'RM BETA' })).toBeInTheDocument();
    expect(screen.getByText('house')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add RM' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'RM BETA' }));
    const panel = await screen.findByRole('dialog', { name: 'RM BETA' });
    expect(within(panel).getByText('Branch now: PATNA')).toBeInTheDocument();
    expect(within(panel).getByRole('heading', { name: 'Move to another branch' })).toBeInTheDocument();
    expect(within(panel).queryByText(/reason/i)).not.toBeInTheDocument();
    expect(within(panel).queryByText(/exit/i)).not.toBeInTheDocument();
  });

  it('has no add buttons for FINANCE', async () => {
    fakeApi({
      'GET /api/auth/me': ok(me('FINANCE')),
      'GET /api/org': ok(chart),
      'GET /api/regions': ok([]),
      'GET /api/branches': ok([]),
      'GET /api/employees': ok([]),
    });
    renderAt('/setup/org');
    expect(await screen.findByRole('button', { name: 'RM BETA' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add RM' })).not.toBeInTheDocument();
  });
});

describe('import', () => {
  it('checks a file as a dry run and lists the problems', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    const result: ImportResult = {
      fileName: 'masters.xlsx', dryRun: true, applied: false,
      sheets: [{ sheet: 'Regions', created: 2, updated: 0, unchanged: 0 }],
      errors: [{ sheet: 'RD', row: 4, message: 'Only an RM can be an RD' }],
    };
    const job: Job<ImportResult> = { id: 9, type: 'master-import', title: 'Imported masters from a file', status: 'DONE', result, error: null, reference: 'TESTREF1' };
    let sentDryRun: string | null = null;
    fakeApi({
      'GET /api/auth/me': ok(me('ADMIN')),
      'POST /api/imports/masters': (init) => {
        sentDryRun = (init?.body as FormData).get('dryRun') as string;
        return { status: 202, body: { jobId: 9 } };
      },
      'GET /api/jobs/9': ok(job),
    });
    renderAt('/setup/import');

    const file = new File(['x'], 'masters.xlsx');
    await userEvent.upload(await screen.findByLabelText('Masters workbook'), file);
    await userEvent.click(screen.getByRole('button', { name: 'Check file' }));

    expect(await screen.findByRole('status')).toHaveTextContent('1 problem(s) found. Nothing was saved.');
    expect(sentDryRun).toBe('true');
    expect(screen.getByText('Only an RM can be an RD')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Would create' })).toBeInTheDocument();
  });

  it('is read-only for FINANCE', async () => {
    fakeApi({ 'GET /api/auth/me': ok(me('FINANCE')) });
    renderAt('/setup/import');
    expect(await screen.findByText('Only ADMIN and HO can import.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Download the template' })).toBeInTheDocument();
  });
});
