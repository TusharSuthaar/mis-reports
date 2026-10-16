import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { App } from '../../App';
import type { ClientRow, Employee, Job, LoadPreview, LoadResult, Me, Page, Role } from '../../api/types';
import { AuthProvider } from '../../app/auth';
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

const rms: Employee[] = [
  { id: 1, empCode: 'PAT-02', name: 'RM ALPHA', type: 'RM', status: 'ACTIVE', joinedOn: null, exitedOn: null, branchId: 10, branchName: 'PATNA' },
  { id: 2, empCode: 'SAS-01', name: 'RM ALPHA_SASARAM', type: 'RM', status: 'ACTIVE', joinedOn: null, exitedOn: null, branchId: 11, branchName: 'SASARAM' },
];

const page: Page<ClientRow> = {
  items: [
    { id: 100, name: 'ANITA RAO', pan: 'PQRST****K', iwellCode: '320066313', rmId: 1, rmName: 'RM ALPHA', branchName: 'PATNA' },
    { id: 101, name: 'WAITING CLIENT', pan: null, iwellCode: '999', rmId: null, rmName: null, branchName: null },
  ],
  page: 0,
  size: 50,
  total: 2,
};

describe('clients', () => {
  it('lists clients, flags those without an RM, and offers a simple change RM form', async () => {
    fakeApi({
      'GET /api/auth/me': ok(me('HO')),
      'GET /api/employees': ok(rms),
      'GET /api/clients': ok(page),
      'GET /api/clients/100': ok({ client: page.items[0], iwellCode2: 'x', familyHeadCode: null, familyHeadName: null }),
      'GET /api/clients/100/rm': ok([]),
    });
    renderAt('/clients');

    expect(await screen.findByText('320066313')).toBeInTheDocument();
    expect(screen.getByText('needs RM')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'ANITA RAO' }));
    const panel = await screen.findByRole('dialog', { name: 'ANITA RAO' });
    expect(await within(panel).findByText('RM ALPHA — PATNA')).toBeInTheDocument();
    expect(within(panel).getByRole('heading', { name: 'Change RM' })).toBeInTheDocument();
    expect(within(panel).getAllByLabelText('From')).toHaveLength(3); // change RM, sub-broker, referral partner
    // the current RM is not offered again
    expect(within(panel).queryByRole('option', { name: 'RM ALPHA — PATNA' })).not.toBeInTheDocument();
  });

  it('finds a client by PAN and corrects the PAN', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    let searched: string | null = null;
    let sent: unknown = null;
    fakeApi({
      'GET /api/auth/me': ok(me('HO')),
      'GET /api/employees': ok(rms),
      'GET /api/clients': ok(page),
      'GET /api/clients/100': ok({ client: page.items[0], iwellCode2: null, familyHeadCode: null, familyHeadName: null }),
      'GET /api/clients/100/rm': ok([]),
      'POST /api/clients/100/pan': (init) => {
        sent = JSON.parse(String(init?.body));
        return { status: 204, body: undefined };
      },
    });
    renderAt('/clients?q=pqrst6789k');
    searched = ((await screen.findByPlaceholderText('Name, client code or full PAN')) as HTMLInputElement).value;
    expect(searched).toBe('pqrst6789k');

    await userEvent.click(await screen.findByRole('button', { name: 'ANITA RAO' }));
    const panel = await screen.findByRole('dialog', { name: 'ANITA RAO' });
    await userEvent.click(within(panel).getByRole('button', { name: 'Edit PAN' }));
    await userEvent.type(within(panel).getByLabelText('New PAN'), 'zyxwv9876a');
    await userEvent.type(within(panel).getByLabelText('Reason for the PAN change'), 'typo');
    await userEvent.click(within(panel).getByRole('button', { name: 'Save PAN' }));
    expect(sent).toEqual({ pan: 'ZYXWV9876A', reason: 'typo', takeFromOther: false });
  });

  it('sets one RM for all selected clients', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    let sent: unknown = null;
    fakeApi({
      'GET /api/auth/me': ok(me('ADMIN')),
      'GET /api/employees': ok(rms),
      'GET /api/clients': ok(page),
      'POST /api/clients/rm': (init) => {
        sent = JSON.parse(String(init?.body));
        return { status: 200, body: { changed: 1 } };
      },
    });
    renderAt('/clients');

    await userEvent.click(await screen.findByLabelText('Select WAITING CLIENT'));
    expect(screen.getByText('1 selected')).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('RM'), '2');
    await userEvent.click(screen.getByRole('button', { name: 'Set RM' }));

    expect(await screen.findByText('320066313')).toBeInTheDocument();
    expect(sent).toMatchObject({ clientIds: [101], rmId: 2 });
  });

  it('has no selection or change form for FINANCE', async () => {
    fakeApi({ 'GET /api/auth/me': ok(me('FINANCE')), 'GET /api/employees': ok(rms), 'GET /api/clients': ok(page) });
    renderAt('/clients');
    expect(await screen.findByText('320066313')).toBeInTheDocument();
    expect(screen.queryByLabelText('Select WAITING CLIENT')).not.toBeInTheDocument();
  });
});

describe('load clients', () => {
  const preview: LoadPreview = {
    token: 'client-loads/x.xlsx',
    fileName: "MF Source_Aug'26.xlsx",
    rows: 3,
    totals: { gross: 1180000.118, gst: 180000.018, net: 1000000.1 },
    errors: [],
    warningCount: 0,
    warnings: [],
    newClients: 3,
    existingClients: 0,
    withoutPan: 1,
    rms: [
      { spelling: 'RM ALPHA', clients: 1, net: 100, status: 'MATCHED', rmId: 1, rmLabel: 'RM ALPHA — PATNA', candidates: [] },
      { spelling: 'Rm Alpha - Sasaram', clients: 1, net: 50, status: 'UNKNOWN', rmId: null, rmLabel: null, candidates: [] },
      { spelling: 'RM DELTA', clients: 1, net: 1, status: 'SEVERAL', rmId: null, rmLabel: null, candidates: [{ id: 7, label: 'RM DELTA — DELHI' }, { id: 8, label: 'RM DELTA — YAMUNA NAGAR' }] },
    ],
    subBrokers: [{ spelling: 'ZEDD QUILLON XAVIER', clients: 1, net: 10, status: 'UNKNOWN', partnerId: null, partnerName: null }],
    allRms: [{ id: 1, label: 'RM ALPHA — PATNA' }, { id: 2, label: 'RM ALPHA_SASARAM — SASARAM' }],
  };

  it('previews, lets the user resolve spellings, and loads with those decisions', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    let sent: Record<string, unknown> | null = null;
    const result: LoadResult = { fileName: 'x', clientsCreated: 3, clientsAlreadyThere: 0, rmAssigned: 2, needsRm: 1, partnersCreated: 1, spellingsRemembered: 2, familyHeadsLinked: 0 };
    const job: Job<LoadResult> = { id: 5, type: 'client-load', title: 'Loaded clients from a file', status: 'DONE', result, error: null, reference: 'TESTREF1' };
    fakeApi({
      'GET /api/auth/me': ok(me('ADMIN')),
      'POST /api/client-load/preview': ok(preview),
      'POST /api/client-load': (init) => {
        sent = JSON.parse(String(init?.body));
        return { status: 202, body: { jobId: 5 } };
      },
      'GET /api/jobs/5': ok(job),
    });
    renderAt('/setup/load-clients');

    await userEvent.upload(await screen.findByLabelText('Investwell MF file'), new File(['x'], 'aug.xlsx'));
    await userEvent.click(screen.getByRole('button', { name: 'Check file' }));

    expect(await screen.findByText(/net 10,00,000.10/)).toBeInTheDocument();
    const several = screen.getByLabelText('RM for RM DELTA');
    expect(within(several).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Leave without RM for now', 'RM DELTA — DELHI', 'RM DELTA — YAMUNA NAGAR',
    ]);
    expect(screen.getByText('2 clients will wait as "needs RM".')).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('RM for Rm Alpha - Sasaram'), '2');
    const partner = screen.getByLabelText('Partner for ZEDD QUILLON XAVIER');
    await userEvent.clear(partner);
    await userEvent.type(partner, 'ZEDD XAVIER');
    await userEvent.click(screen.getByRole('button', { name: 'Load 3 clients' }));

    expect(await screen.findByText(/Loaded 3 clients/)).toBeInTheDocument();
    expect(sent).toMatchObject({
      token: 'client-loads/x.xlsx',
      from: '2026-08-01',
      rmChoices: { 'Rm Alpha - Sasaram': 2 },
      partnerNames: { 'ZEDD QUILLON XAVIER': 'ZEDD XAVIER' },
    });
  });

  it('refuses a file that does not add up', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    fakeApi({
      'GET /api/auth/me': ok(me('ADMIN')),
      'POST /api/client-load/preview': ok({ ...preview, errors: [{ row: 0, message: 'Rows do not add up to the Total row' }] }),
    });
    renderAt('/setup/load-clients');
    await userEvent.upload(await screen.findByLabelText('Investwell MF file'), new File(['x'], 'aug.xlsx'));
    await userEvent.click(screen.getByRole('button', { name: 'Check file' }));
    expect(await screen.findByText('Rows do not add up to the Total row')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Load/ })).not.toBeInTheDocument();
  });
});
