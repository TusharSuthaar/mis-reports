import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { App } from '../../App';
import type { Employee, Me, OrgChart, Role } from '../../api/types';
import { AuthProvider } from '../../app/auth';
import { fakeApi } from '../../test/fakeApi';
import { addMonths, fdRevenue, maturityValue, monthsBetween } from './FdPage';
import { indianDigits } from '../../format';

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

describe('fd helpers', () => {
  it('works out the commission like the server (R-70)', () => {
    expect(fdRevenue(1000000, 1, true, 2, 18)).toEqual({ gross: 10000, tds: 200, gst: 1800, net: 9800, invoice: 11800 });
    expect(fdRevenue(123457, 0.75, false, 10, 18)).toEqual({ gross: 925.93, tds: 0, gst: 166.67, net: 925.93, invoice: 1092.6 });
  });

  it('works out the maturity value like the server (R-72), matching the FD register', () => {
    expect(maturityValue(500000, 6.55, 12, 'CUMULATIVE', 'YEARLY')).toBe(532750);
    expect(maturityValue(500000, 7, 33, 'CUMULATIVE', 'YEARLY')).toBe(602503.63);
    expect(maturityValue(300000, 6.8, 12, 'CUMULATIVE', 'YEARLY')).toBe(320400);
    expect(maturityValue(100000, 8, 12, 'CUMULATIVE', 'QUARTERLY')).toBe(108243.22);
    expect(maturityValue(100000, 8, 7, 'CUMULATIVE', 'QUARTERLY')).toBe(104733.6);
    expect(maturityValue(100000, 8, 6, 'MONTHLY', 'YEARLY')).toBe(104000);
    expect(maturityValue(100000, 8, 0, 'CUMULATIVE', 'YEARLY')).toBeNull();
  });

  it('links deposit date, months and maturity date, and groups amounts the Indian way', () => {
    expect(addMonths('2022-11-17', 33)).toBe('2025-08-17');
    expect(addMonths('2026-01-31', 1)).toBe('2026-02-28');
    expect(monthsBetween('2022-12-09', '2023-12-19')).toBe(12);
    expect(monthsBetween('2026-08-01', '2026-08-01')).toBe(0);
    expect(indianDigits('1000000')).toBe('10,00,000');
    expect(indianDigits('2,50,000.5')).toBe('2,50,000.5');
  });
});

describe('fd screen', () => {
  it('adds an FD for a new client with the commission shown live', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    let sent: Record<string, unknown> | null = null;
    fakeApi({
      'GET /api/auth/me': ok(me('HO')),
      'GET /api/fd': ok([]),
      'GET /api/employees': ok(rms),
      'GET /api/partners': ok([]),
      'GET /api/org': ok(chart),
      'GET /api/product-taxes/FD/on': ok({ id: 1, product: 'FD', gstPercent: 18, tdsPercent: 2, tdsByDefault: true, validFrom: '2026-08-01', validTo: null, reason: null, changedBy: 's', changedAt: '' }),
      'GET /api/clients': ok({ items: [], page: 0, size: 20, total: 0 }),
      'GET /api/fd/suggestions': ok({ fdNames: ['TEST FINANCE'], platforms: ['J M Finance'], margins: [{ fdName: 'TEST FINANCE', platform: 'J M Finance', periodMonths: 36, marginPercent: 1 }] }),
      'POST /api/fd': (init) => {
        sent = JSON.parse(String(init?.body));
        return { status: 201, body: { id: 1 } };
      },
    });
    renderAt('/fd');
    await userEvent.click(await screen.findByRole('button', { name: 'Add FD' }));
    const panel = await screen.findByRole('dialog', { name: 'Add FD' });
    const field = (name: RegExp | string) => within(panel).getByLabelText(name);

    // PAN first: nobody has it, so the new-client fields open with the PAN carried over.
    await userEvent.type(field('Client PAN or client code'), 'ZZZZZ9999Z');
    await userEvent.click(within(panel).getByRole('button', { name: 'Find' }));
    await userEvent.type(await within(panel).findByLabelText('Client name'), 'NEW FD CLIENT');
    expect((field('PAN') as HTMLInputElement).value).toBe('ZZZZZ9999Z');
    expect(field('Address')).toBeInTheDocument();
    await userEvent.selectOptions(field('RM'), '1');
    expect(await within(panel).findByText(/Region: Bihar & EUP · RD: Rina Das/)).toBeInTheDocument();
    await userEvent.type(field('FD company (FD name)'), 'TEST FINANCE');
    await userEvent.type(field('Partner (platform)'), 'J M Finance');
    await userEvent.selectOptions(field('Interest frequency'), 'CUMULATIVE');
    await userEvent.type(field('Deposit date'), '2026-08-01');
    await userEvent.type(field('Deposit period (months)'), '36');
    // Deposit date + months → maturity date.
    expect((field('Maturity date') as HTMLInputElement).value).toBe('2029-08-01');
    await userEvent.type(field('Interest rate %'), '8.25');
    await userEvent.type(field('Investment amount (₹)'), '1000000');
    expect((field('Investment amount (₹)') as HTMLInputElement).value).toBe('10,00,000');
    // Same company + platform + period as an earlier FD → its margin is offered.
    expect((field('Margin %') as HTMLInputElement).value).toBe('1');
    // TDS starts ticked at 2% because FD's rate says so.
    expect(within(panel).getByLabelText('TDS')).toBeChecked();
    expect(within(panel).getByText('9,800.00')).toBeInTheDocument();
    expect(within(panel).getByText('11,800.00')).toBeInTheDocument();
    expect((field('Maturity value (₹)') as HTMLInputElement).value).toBe('12,68,480.27');
    expect(field('Compounding')).toHaveValue('YEARLY');

    await userEvent.click(within(panel).getByLabelText('Updated on Epsilon portal'));
    await userEvent.click(within(panel).getByRole('button', { name: 'Add FD' }));
    await waitFor(() => expect(sent).not.toBeNull());
    expect(sent).toMatchObject({ newClientName: 'NEW FD CLIENT', newClientPan: 'ZZZZZ9999Z', rmId: 1, amount: 1000000, marginPercent: 1, tdsApplied: true, tdsPercent: 2, compounding: 'YEARLY', periodMonths: 36, platform: 'J M Finance', onPortal: true });
    expect(sent).not.toHaveProperty('onInvestwell');
    expect(String(sent!.month)).toMatch(/-01$/);
  });

  it('lists all months by default and sends the filters to the server and the Excel download', async () => {
    const fetchMock = fakeApi({
      'GET /api/auth/me': ok(me('HO')),
      'GET /api/fd': ok([]),
      'GET /api/fd/suggestions': ok({ fdNames: [], platforms: ['J M Finance'], margins: [] }),
      'GET /api/employees': ok(rms),
      'GET /api/partners': ok([]),
    });
    renderAt('/fd');
    expect(await screen.findByText('No FDs match.')).toBeInTheDocument();
    const firstList = fetchMock.mock.calls.map((c) => String(c[0])).find((u) => u.includes('/api/fd') && !u.includes('suggestions'));
    expect(firstList).toMatch(/\/api\/fd$/);   // no month: every month

    await userEvent.selectOptions(screen.getByLabelText('RM'), '1');
    await userEvent.selectOptions(screen.getByLabelText('Platform'), 'J M Finance');
    await userEvent.type(screen.getByLabelText('From month'), '2026-08');
    await waitFor(() => {
      const last = fetchMock.mock.calls.map((c) => String(c[0])).filter((u) => u.includes('/api/fd?')).pop() ?? '';
      expect(last).toContain('rmId=1');
      expect(last).toContain('platform=J+M+Finance');
      expect(last).toContain('from=2026-08');
    });
    expect(screen.getByRole('link', { name: 'Download Excel' }).getAttribute('href')).toContain('/api/fd/export.xlsx?');
    expect(screen.getByRole('link', { name: 'Download Excel' }).getAttribute('href')).toContain('rmId=1');
    await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }));
    expect(screen.getByRole('link', { name: 'Download Excel' }).getAttribute('href')).toBe('/api/fd/export.xlsx');
  });

  it('finds an existing client by code and takes the RM from the client', async () => {
    const client = { id: 7, name: 'ANITA RAO', pan: 'PQRST****K', iwellCode: 'FC01234', rmId: 1, rmName: 'RM ALPHA', branchName: 'PATNA' };
    const nameOnly = { ...client, id: 8, name: 'FC01234 TRUST', iwellCode: 'FC09999' };
    fakeApi({
      'GET /api/auth/me': ok(me('HO')),
      'GET /api/fd': ok([]),
      'GET /api/employees': ok(rms),
      'GET /api/partners': ok([]),
      'GET /api/org': ok(chart),
      'GET /api/product-taxes/FD/on': ok({ id: 1, product: 'FD', gstPercent: 18, tdsPercent: 0, tdsByDefault: false, validFrom: '2026-08-01', validTo: null, reason: null, changedBy: 's', changedAt: '' }),
      'GET /api/clients': ok({ items: [client, nameOnly], page: 0, size: 20, total: 2 }),
      'GET /api/shares': ok({}),
    });
    renderAt('/fd');
    await userEvent.click(await screen.findByRole('button', { name: 'Add FD' }));
    const panel = await screen.findByRole('dialog', { name: 'Add FD' });
    await userEvent.type(within(panel).getByLabelText('Client PAN or client code'), 'fc01234{Enter}');
    // Only the client with that code counts, not a name that happens to contain it.
    expect(await within(panel).findByText(/ANITA RAO/)).toBeInTheDocument();
    expect(within(panel).queryByText(/FC01234 TRUST/)).not.toBeInTheDocument();
    expect((within(panel).getByLabelText('RM') as HTMLInputElement).value).toBe('RM ALPHA');
    expect(within(panel).getByLabelText('RM')).toHaveAttribute('readonly');
    expect(within(panel).getByText(/change it on the Clients screen/)).toBeInTheDocument();
    expect(within(panel).getByLabelText('Address')).toBeInTheDocument();
  });

  it('lists products and their tax rates for Finance without edit', async () => {
    fakeApi({
      'GET /api/auth/me': ok(me('FINANCE')),
      'GET /api/product-taxes': ok([
        { product: 'MF', name: 'MF', now: { id: 1, product: 'MF', gstPercent: 18, tdsPercent: 0, tdsByDefault: false, validFrom: '2026-08-01', validTo: null, reason: null, changedBy: 's', changedAt: '' } },
        { product: 'BOND', name: 'Bonds', now: null },
      ]),
      'GET /api/product-taxes/MF': ok([]),
    });
    renderAt('/setup/product-taxes');
    expect(await screen.findByText('not set')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'MF' }));
    const panel = await screen.findByRole('dialog', { name: 'MF' });
    expect(within(panel).queryByRole('heading', { name: 'Change rates' })).not.toBeInTheDocument();
  });
});
