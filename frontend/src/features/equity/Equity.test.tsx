import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { App } from '../../App';
import type { Me } from '../../api/types';
import { AuthProvider } from '../../app/auth';
import { fakeApi } from '../../test/fakeApi';

const me: Me = { id: 1, username: 'ho', displayName: 'A HO', role: 'HO' };
const ok = (body: unknown) => () => ({ status: 200, body });

describe('equity tab', () => {
  it('shows each client-month from the broker report and flags PANs not in Clients (R-89)', async () => {
    fakeApi({
      'GET /api/auth/me': ok(me),
      'GET /api/employees': ok([]),
      'GET /api/partners': ok([]),
      'GET /api/equity/uploads': ok([{ id: 1, month: '2026-08', fileName: "Equity Source_Aug'26.XLSx", broker: 'SMC GLOBAL SECURITIES LTD', trades: 3,
        clients: 2, grossTotal: 160, shareTotal: 126, status: 'ACTIVE', uploadedBy: 'ho', uploadedAt: '2026-10-21T10:00:00Z' }]),
      'GET /api/equity': ok([
        { month: '2026-08', clientId: 7, clientName: 'EQ ONE', pan: 'ABCDE****F', partyCode: 'P1', rmName: 'RM ALPHA', branchName: 'PATNA', trades: 2,
          exchanges: 'B+N', gross: 150, share: 120, sharePercent: 80 },
        { month: '2026-08', clientId: null, clientName: 'SOMEONE', pan: 'ZZZZZ****Z', partyCode: 'P2', rmName: null, branchName: null, trades: 1,
          exchanges: 'N', gross: 10, share: 6, sharePercent: 60 },
      ]),
    });
    render(
      <MemoryRouter initialEntries={['/products?tab=EQUITY']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    );
    const link = await screen.findByRole('link', { name: 'EQ ONE' });
    expect(link).toHaveAttribute('href', '/clients/7');
    expect(within(link.closest('tr') as HTMLElement).getByText('BSE + NSE')).toBeInTheDocument();
    expect(screen.getByText('not in Clients')).toBeInTheDocument();
    expect(screen.getByText(/1 PAN is not in Clients yet/)).toBeInTheDocument();
    expect(screen.getByText('In use')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Upload' })).toBeDisabled();
  });
});
