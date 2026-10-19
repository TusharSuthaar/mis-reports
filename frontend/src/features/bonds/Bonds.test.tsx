import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { App } from '../../App';
import type { Employee, Me, OrgChart, Role } from '../../api/types';
import { AuthProvider } from '../../app/auth';
import { fakeApi } from '../../test/fakeApi';
import { bondRevenue } from './BondsPage';

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
];
const chart: OrgChart = {
  date: '2026-08-01',
  regions: [{ id: 1, name: 'Bihar & EUP', rd: { id: 5, empCode: 'E5', name: 'Rina Das' }, branches: [{ id: 10, name: 'PATNA', people: [{ id: 1, empCode: 'E1', name: 'RM ALPHA', type: 'RM' }] }] }],
  branchesWithoutRegion: [],
  peopleWithoutBranch: [],
};
const terms = { id: 1, platformId: 3, gstInclusive: true, tdsPercent: 5, ourShare: 67, validFrom: '2026-01-01', validTo: null, reason: null, changedBy: 's', changedAt: '' };

describe('bond helpers', () => {
  it('works out the brokerage like the server (R-76), matching the bond register', () => {
    expect(bondRevenue(1000000, 0.4, 67, true, 18, true, 5)).toEqual({ brokerage: 2680, gross: 2271.19, gst: 408.81, tds: 113.56, net: 2157.63, invoice: 2680 });
    expect(bondRevenue(590000, 0.15, 100, false, 18, true, 5)).toEqual({ brokerage: 885, gross: 885, gst: 159.3, tds: 44.25, net: 840.75, invoice: 1044.3 });
  });
});

describe('bonds screen', () => {
  it('adds a bond for a client found by PAN, with the platform terms applied live', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    let sent: Record<string, unknown> | null = null;
    const client = { id: 7, name: 'ANITA RAO', pan: 'PQRST****K', iwellCode: 'EM234', rmId: 1, rmName: 'RM ALPHA', branchName: 'PATNA' };
    fakeApi({
      'GET /api/auth/me': ok(me('HO')),
      'GET /api/bonds': ok([]),
      'GET /api/employees': ok(rms),
      'GET /api/partners': ok([]),
      'GET /api/platforms': ok([{ id: 3, name: 'TEST PLATFORM', now: terms }]),
      'GET /api/platforms/3/terms/on': ok(terms),
      'GET /api/bonds/suggestions': ok({ bondNames: ['STATE BEVERAGES'], isins: [{ bondName: 'STATE BEVERAGES', isin: 'INE0M2307057' }], markups: [{ platformId: 3, bondName: 'STATE BEVERAGES', isin: 'INE0M2307057', markupPercent: 0.4 }] }),
      'GET /api/org': ok(chart),
      'GET /api/product-taxes/BOND/on': ok({ id: 1, product: 'BOND', gstPercent: 18, tdsPercent: 0, tdsByDefault: false, validFrom: '2022-01-01', validTo: null, reason: null, changedBy: 's', changedAt: '' }),
      'GET /api/clients': ok({ items: [client], page: 0, size: 20, total: 1 }),
      'GET /api/shares': ok({}),
      'POST /api/bonds': (init) => {
        sent = JSON.parse(String(init?.body));
        return { status: 201, body: { id: 1 } };
      },
    });
    renderAt('/bonds');
    await userEvent.click(await screen.findByRole('button', { name: 'Add bond' }));
    const panel = await screen.findByRole('dialog', { name: 'Add bond' });
    const field = (name: RegExp | string) => within(panel).getByLabelText(name);

    await userEvent.type(field('Client PAN or client code'), 'PQRST6789K{Enter}');
    expect(await within(panel).findByText(/Client type: EM234/)).toBeInTheDocument();
    expect((field('RM') as HTMLInputElement).value).toBe('RM ALPHA');
    await userEvent.selectOptions(field('Partner (platform)'), '3');
    await userEvent.type(field('Bond name'), 'STATE BEVERAGES');
    // Name known → ISIN; platform + ISIN known → the mark-up last used.
    expect((field('ISIN') as HTMLInputElement).value).toBe('INE0M2307057');
    expect((field('Mark-up %') as HTMLInputElement).value).toBe('0.4');
    await userEvent.type(field('Investment date'), '2026-08-13');
    await userEvent.type(field('Quantity'), '1');
    await userEvent.type(field('Purchase rate per unit (₹)'), '1000000');
    // Qty × rate → investment amount; + accrued → consideration.
    expect((field('Investment amount (₹)') as HTMLInputElement).value).toBe('10,00,000');
    await userEvent.type(field('Accrued interest (₹)'), '14225.66');
    expect((field('Consideration (₹)') as HTMLInputElement).value).toBe('10,14,225.66');
    expect(within(panel).getByLabelText('TDS')).toBeChecked();
    expect(within(panel).getByText('2,271.19')).toBeInTheDocument();
    expect(within(panel).getAllByText('2,680.00').length).toBeGreaterThan(0);

    // The register says otherwise: keep its figures as they are.
    await userEvent.click(within(panel).getByLabelText(/Use the register's figures/));
    await userEvent.type(field('Gross revenue (excl. GST)'), '5932.20');
    await userEvent.type(field('Register GST'), '1067.80');
    await userEvent.type(field('Register invoice amount'), '7000');
    expect(within(panel).getAllByText('5,932.20')).toHaveLength(2);   // gross and net (no TDS)
    expect(within(panel).getAllByText('7,000.00').length).toBeGreaterThan(0);

    await userEvent.click(within(panel).getByRole('button', { name: 'Add bond' }));
    await waitFor(() => expect(sent).not.toBeNull());
    expect(sent).toMatchObject({ figuresAsGiven: true, givenGross: 5932.2, givenGst: 1067.8, givenInvoice: 7000, givenTds: 0 });
    expect(sent).toMatchObject({ clientId: 7, rmId: 1, platformId: 3, isin: 'INE0M2307057', investmentAmount: 1000000, accruedInterest: 14225.66, markupPercent: 0.4, tdsApplied: true, tdsPercent: 5 });
  });

  it('shows platforms and their terms; Finance cannot change them', async () => {
    fakeApi({
      'GET /api/auth/me': ok(me('FINANCE')),
      'GET /api/platforms': ok([{ id: 3, name: 'TEST PLATFORM', now: terms }, { id: 4, name: 'NEW ONE', now: null }]),
      'GET /api/platforms/3/terms': ok([terms]),
    });
    renderAt('/setup/platforms');
    expect(await screen.findByText('Includes GST')).toBeInTheDocument();
    expect(screen.getByText('no terms')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add platform' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'TEST PLATFORM' }));
    const panel = await screen.findByRole('dialog', { name: 'TEST PLATFORM' });
    expect(within(panel).queryByRole('heading', { name: /terms/ })).not.toBeInTheDocument();
  });
});
