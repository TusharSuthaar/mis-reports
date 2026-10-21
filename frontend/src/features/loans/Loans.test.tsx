import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { App } from '../../App';
import type { Employee, LoanEntry, Me, OrgChart } from '../../api/types';
import { AuthProvider } from '../../app/auth';
import { fakeApi } from '../../test/fakeApi';
import { loanRevenue, monthPlus, paymentText } from './LoansPage';

const me: Me = { id: 1, username: 'ho', displayName: 'A HO', role: 'HO' };
const ok = (body: unknown) => () => ({ status: 200, body });

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}

const rms: Employee[] = [
  { id: 1, empCode: 'PAT-02', name: 'RM ALPHA', type: 'RM', status: 'ACTIVE', joinedOn: null, exitedOn: null, branchId: 10, branchName: 'PATNA' },
];
const chart: OrgChart = {
  date: '2026-08-01',
  regions: [{ id: 1, name: 'Bihar & EUP', rd: { id: 5, empCode: 'E5', name: 'Rina Das' }, branches: [{ id: 10, name: 'PATNA', people: [{ id: 1, empCode: 'E1', name: 'RM ALPHA', type: 'RM' }] }] }],
  branchesWithoutRegion: [],
  peopleWithoutBranch: [],
};
const tax = { id: 1, product: 'LOAN', gstPercent: 18, tdsPercent: 2, tdsByDefault: true, validFrom: '2024-10-01', validTo: null, reason: null, changedBy: 's', changedAt: '' };

describe('loan helpers', () => {
  it('works out the payout like the server (R-80), with the register’s figures', () => {
    expect(loanRevenue(360661, 1.5, 0, true, 2, 18)).toEqual({ gross: 5409.92, tds: 108.2, gst: 973.79, net: 5301.72, invoice: 6383.71 });
    expect(loanRevenue(1796000, 0.25, 0, true, 2, 18).gross).toBe(4490);
    expect(loanRevenue(4500000, 0, 13500, true, 5, 18)).toEqual({ gross: 13500, tds: 675, gst: 2430, net: 12825, invoice: 15930 });
    expect(monthPlus('2026-11', 3)).toBe('2027-02');
  });

  it('shows the payment status as the register writes it', () => {
    const e = { paymentNote: null, loanClosed: false, paymentStatus: 'RECEIVED' } as unknown as LoanEntry;
    expect(paymentText(e)).toBe('CREDITED');
    expect(paymentText({ ...e, paymentStatus: 'PENDING', loanClosed: true })).toBe('Loan Closed');
    expect(paymentText({ ...e, paymentNote: 'Invoice will be raised in Oct' })).toBe('Invoice will be raised in Oct');
  });
});

describe('loans screen', () => {
  it('adds a loan payout for a new client with the revenue shown live, in the register’s fields', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    let sent: Record<string, unknown> | null = null;
    fakeApi({
      'GET /api/auth/me': ok(me),
      'GET /api/loans': ok([]),
      'GET /api/employees': ok(rms),
      'GET /api/partners': ok([]),
      'GET /api/org': ok(chart),
      'GET /api/product-taxes/LOAN/on': ok(tax),
      'GET /api/clients': ok({ items: [], page: 0, size: 20, total: 0 }),
      'GET /api/loans/suggestions': ok({ lenders: ['TATA'], loanTypes: ['Personal Loan'], payouts: [{ lender: 'TATA', loanType: 'Personal Loan', payoutKind: 'DISBURSAL', payoutOn: 'SANCTIONED', payoutPercent: 1.5 }] }),
      'POST /api/loans': (init) => {
        sent = JSON.parse(String(init?.body));
        return { status: 201, body: { id: 1 } };
      },
    });
    renderAt('/products?tab=LOAN');
    await userEvent.click(await screen.findByRole('button', { name: 'Add loan payout' }));
    const panel = await screen.findByRole('dialog', { name: 'Add loan payout' });
    const field = (name: RegExp | string) => within(panel).getByLabelText(name);

    await userEvent.type(field('Client PAN or client code'), 'ZZZZZ9999Z');
    await userEvent.click(within(panel).getByRole('button', { name: 'Find' }));
    await userEvent.type(await within(panel).findByLabelText('Client name'), 'NEW LOAN CLIENT');
    await userEvent.selectOptions(field('RM'), '1');
    expect(await within(panel).findByText(/Region: Bihar & EUP · RD: Rina Das/)).toBeInTheDocument();
    await userEvent.type(field('Partner (lender)'), 'TATA');
    await userEvent.type(field('Loan type'), 'Personal Loan');
    await userEvent.type(field('Invt date (disbursal)'), '2026-07-31');
    await userEvent.type(field('Loan amt / sanctioned amt (₹)'), '360661');
    expect((field('Loan amt / sanctioned amt (₹)') as HTMLInputElement).value).toBe('3,60,661');
    // Same lender + type as an earlier payout → its payout % is offered; TDS starts at the Loans rate (2%).
    expect((field('Revenue sharing % (payout %)') as HTMLInputElement).value).toBe('1.5');
    expect(within(panel).getByLabelText('TDS')).toBeChecked();
    expect(within(panel).getByText('5,409.92')).toBeInTheDocument();
    expect(within(panel).getByText('6,383.71')).toBeInTheDocument();
    await userEvent.type(field('Payment note (as in the register)'), 'Invoice will be raised in Oct');

    await userEvent.click(within(panel).getByRole('button', { name: 'Add loan payout' }));
    await waitFor(() => expect(sent).not.toBeNull());
    expect(sent).toMatchObject({
      newClientName: 'NEW LOAN CLIENT',
      newClientPan: 'ZZZZZ9999Z',
      rmId: 1,
      lender: 'TATA',
      loanType: 'Personal Loan',
      disbursalDate: '2026-07-31',
      sanctionedAmount: 360661,
      payoutKind: 'DISBURSAL',
      payoutOn: 'SANCTIONED',
      payoutPercent: 1.5,
      tdsApplied: true,
      tdsPercent: 2,
      paymentNote: 'Invoice will be raised in Oct',
    });
  });

  it('shows closed loans in their own Closed section, as in the register', async () => {
    const base = {
      month: '2026-07', rmId: 1, rmName: 'RM ALPHA', branchName: 'PATNA', region: 'Bihar & EUP', rd: null, partnerId: null, partnerName: null,
      partnerType: null, clientId: 1, pan: null, clientType: 'OFFLINE', mobile: null, email: null, address: null, lender: 'TATA',
      disbursalDate: '2026-07-07', utilisedAmount: null, ratePercent: 9.75, payoutKind: 'DISBURSAL', payoutOn: 'SANCTIONED', payoutPercent: null,
      fixedAmount: null, gross: 0, tdsApplied: true, tdsPercent: 2, tds: 0, gstPercent: 18, gst: 0, net: 0, invoice: 0, figuresAsGiven: false,
      invoiceRaised: false, paymentStatus: 'PENDING', paymentNote: null, receivedOn: null, remarks: null, status: 'ACTIVE',
      createdBy: 's', createdAt: '', updatedBy: 's', updatedAt: '',
    };
    fakeApi({
      'GET /api/auth/me': ok(me),
      'GET /api/loans': ok([
        { ...base, id: 1, clientName: 'OPEN CLIENT', loanType: 'LAS', sanctionedAmount: 1200000, loanClosed: false },
        { ...base, id: 2, clientName: 'CLOSED CLIENT', loanType: 'LAS', sanctionedAmount: 3400000, loanClosed: true },
      ]),
      'GET /api/loans/suggestions': ok({ lenders: ['TATA'], loanTypes: [], payouts: [] }),
      'GET /api/employees': ok(rms),
      'GET /api/partners': ok([]),
    });
    renderAt('/products?tab=LOAN');
    const heading = await screen.findByRole('heading', { name: /Closed/ });
    const closed = heading.closest('.loan-closed') as HTMLElement;
    expect(within(closed).getByText('CLOSED CLIENT')).toBeInTheDocument();
    expect(within(closed).queryByText('OPEN CLIENT')).not.toBeInTheDocument();
    expect(within(closed).getByText('Loan Closed')).toBeInTheDocument();
    expect(screen.getByText('OPEN CLIENT').closest('.loan-closed')).toBeNull();
  });
});
