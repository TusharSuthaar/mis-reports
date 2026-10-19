import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { App } from '../../App';
import type { Employee, Me, OrgChart, Role } from '../../api/types';
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
  { id: 1, empCode: 'HO-01', name: 'RM ALPHA', type: 'RM', status: 'ACTIVE', joinedOn: null, exitedOn: null, branchId: 10, branchName: 'HO SALES' },
];
const chart: OrgChart = {
  date: '2026-08-01',
  regions: [{ id: 1, name: 'HO Sales', rd: null, branches: [{ id: 10, name: 'HO SALES', people: [{ id: 1, empCode: 'E1', name: 'RM ALPHA', type: 'RM' }] }] }],
  branchesWithoutRegion: [],
  peopleWithoutBranch: [],
};
const terms = { id: 1, platformId: 3, gstInclusive: false, tdsPercent: 2, ourShare: 100, validFrom: '2026-01-01', validTo: null, reason: null, changedBy: 's', changedAt: '' };

describe('aif screen', () => {
  it('adds a commitment with two drawdowns and a deal sharing %, revenue worked out on the commitment', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    let sent: Record<string, unknown> | null = null;
    const client = { id: 7, name: 'ANITA RAO', pan: 'PQRST****K', iwellCode: 'EM234', rmId: 1, rmName: 'RM ALPHA', branchName: 'HO SALES' };
    fakeApi({
      'GET /api/auth/me': ok(me('HO')),
      'GET /api/aif': ok([]),
      'GET /api/employees': ok(rms),
      'GET /api/partners': ok([{ id: 9, type: 'REFERRAL', name: 'SONU JAIN', pan: null, gstin: null, arnOrPospCode: null, bankRef: null, status: 'ACTIVE' }]),
      'GET /api/platforms': ok([{ id: 3, name: 'WEALTH COMPANY', now: terms }]),
      'GET /api/platforms/3/terms/on': ok(terms),
      'GET /api/aif/suggestions': ok({ fundNames: ['BHARAT VALUE FUND'], markups: [{ platformId: 3, fundName: 'BHARAT VALUE FUND', markupPercent: 1.3 }] }),
      'GET /api/org': ok(chart),
      'GET /api/product-taxes/AIF/on': ok({ id: 1, product: 'AIF', gstPercent: 18, tdsPercent: 0, tdsByDefault: false, validFrom: '2022-01-01', validTo: null, reason: null, changedBy: 's', changedAt: '' }),
      'GET /api/clients': ok({ items: [client], page: 0, size: 20, total: 1 }),
      'GET /api/shares': ok({}),
      'POST /api/aif': (init) => {
        sent = JSON.parse(String(init?.body));
        return { status: 201, body: { id: 1 } };
      },
    });
    renderAt('/aif');
    await userEvent.click(await screen.findByRole('button', { name: 'Add AIF' }));
    const panel = await screen.findByRole('dialog', { name: 'Add AIF' });
    const field = (name: RegExp | string) => within(panel).getByLabelText(name);

    await userEvent.type(field('Client PAN or client code'), 'EM234{Enter}');
    expect(await within(panel).findByText(/Client type: EM234/)).toBeInTheDocument();
    await userEvent.selectOptions(field('Referral / sub-broker'), '9');
    await userEvent.type(field('Revenue sharing % (this deal)'), '50');
    await userEvent.selectOptions(field('Partner (platform)'), '3');
    await userEvent.type(field('AIF (fund name)'), 'BHARAT VALUE FUND');
    expect((field('Mark-up %') as HTMLInputElement).value).toBe('1.3');
    await userEvent.type(field('Commitment (₹)'), '10000000');
    await userEvent.type(field('1st instalment (₹)'), '2000000');
    await userEvent.click(within(panel).getByRole('button', { name: 'Add instalment' }));
    await userEvent.type(field('2nd instalment (₹)'), '2000000');
    expect(within(panel).getByText('40,00,000.00')).toBeInTheDocument();          // called so far
    expect(within(panel).getByText('1,30,000.00')).toBeInTheDocument();           // gross
    expect(within(panel).getByText('1,53,400.00')).toBeInTheDocument();           // invoice
    expect(within(panel).getByText(/Partner gets 50% of gross = 65,000.00/)).toBeInTheDocument();

    await userEvent.click(within(panel).getByRole('button', { name: 'Add AIF' }));
    await waitFor(() => expect(sent).not.toBeNull());
    expect(sent).toMatchObject({
      clientId: 7,
      partnerId: 9,
      sharingPercent: 50,
      platformId: 3,
      commitment: 10000000,
      markupPercent: 1.3,
      tdsApplied: true,
      tdsPercent: 2,
      instalments: [
        { amount: 2000000, paidOn: null },
        { amount: 2000000, paidOn: null },
      ],
    });
  });
});
