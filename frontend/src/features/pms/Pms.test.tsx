import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { App } from '../../App';
import type { Me } from '../../api/types';
import { AuthProvider } from '../../app/auth';
import { fakeApi } from '../../test/fakeApi';
import { pmsPayoutFigures } from './PmsPage';

const me: Me = { id: 1, username: 'ho', displayName: 'A HO', role: 'HO' };
const ok = (body: unknown) => () => ({ status: 200, body });

describe('pms', () => {
  it('works out an AMC payout like the server (R-85)', () => {
    expect(pmsPayoutFigures(5565, true, 2, 18)).toEqual({ tds: 111.3, gst: 1001.7, net: 5453.7, invoice: 6566.7 });
  });

  it('lists investments and switches to the AMC payouts', async () => {
    fakeApi({
      'GET /api/auth/me': ok(me),
      'GET /api/employees': ok([]),
      'GET /api/partners': ok([]),
      'GET /api/pms/suggestions': ok({ amcs: ['ICICI PRU'], strategies: ['ICICI PIPE'] }),
      'GET /api/pms': ok([
        { id: 1, month: '2026-06', rmId: 1, rmName: 'RM ALPHA', branchName: null, region: 'Delhi', rd: null, partnerId: null, partnerName: null,
          partnerType: null, clientId: 7, clientName: 'PMS ONE', pan: null, clientType: 'OFFLINE', mobile: null, email: null, address: null,
          amc: 'ICICI PRU', strategy: 'ICICI PIPE', amount: 5000000, investmentDate: '2026-06-17', onPortal: true, trailPercent: 1.25,
          payoutFrequency: 'QUARTERLY', qty: null, price: null, consideration: null, certificate: 'PENDING', investmentMail: false,
          holdingStatus: 'REDEEMED', redeemedOn: '2026-07-15', remarks: null, status: 'ACTIVE', createdBy: 's', createdAt: '', updatedBy: 's', updatedAt: '' },
      ]),
      'GET /api/pms/payouts': ok([
        { id: 3, amc: 'Motilal AMC', periodFrom: '2026-04', periodTo: '2026-06', amount: 5565, tdsApplied: true, tdsPercent: 2, tds: 111.3,
          gstPercent: 18, gst: 1001.7, net: 5453.7, invoice: 6566.7, invoiceRaised: true, receivedOn: '2026-07-21', remarks: null, status: 'ACTIVE',
          updatedBy: 's', updatedAt: '' },
      ]),
    });
    render(
      <MemoryRouter initialEntries={['/products?tab=PMS']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    );
    const row = (await screen.findByRole('button', { name: 'PMS ONE' })).closest('tr') as HTMLElement;
    expect(within(row).getByText(/Redeemed/)).toBeInTheDocument();
    expect(within(row).getByText('Quarterly')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'AMC payouts' }));
    const payout = (await screen.findByRole('button', { name: 'Motilal AMC' })).closest('tr') as HTMLElement;
    expect(within(payout).getByText('Apr-26 to Jun-26')).toBeInTheDocument();
    expect(within(payout).getByText('6,566.70')).toBeInTheDocument();
  });
});
