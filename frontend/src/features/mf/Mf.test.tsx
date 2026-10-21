import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { App } from '../../App';
import type { Me } from '../../api/types';
import { AuthProvider } from '../../app/auth';
import { fakeApi } from '../../test/fakeApi';

const me: Me = { id: 1, username: 'ho', displayName: 'A HO', role: 'HO' };
const ok = (body: unknown) => () => ({ status: 200, body });

describe('MF tab', () => {
  it('lists MF brokerage across months with the split, linking to the client (R-81)', async () => {
    fakeApi({
      'GET /api/auth/me': ok(me),
      'GET /api/employees': ok([]),
      'GET /api/partners': ok([]),
      'GET /api/mf': ok({
        rows: [
          { lineId: 1, month: '2026-08', status: 'PROCESSED', adjustsMonth: null, clientId: 7, clientName: 'ONE CLIENT', pan: 'ABCDE****F', rm: 'RM ALPHA',
            branch: 'PATNA', region: 'Bihar & EUP', gross: 118, gst: 18, net: 100, partners: [{ role: 'Sub-broker', name: 'SB ONE', percent: 0.5, amount: 50 }],
            partnersTotal: 50, epsilon: 50 },
          { lineId: 2, month: '2026-09', status: 'OPEN', adjustsMonth: null, clientId: 8, clientName: 'TWO CLIENT', pan: null, rm: 'RM ALPHA',
            branch: 'PATNA', region: null, gross: 59, gst: 9, net: 50, partners: [], partnersTotal: null, epsilon: null },
        ],
        totals: { rows: 2, gross: 177, gst: 27, net: 150, partners: 50, epsilon: 50 },
        truncated: false,
      }),
    });
    render(
      <MemoryRouter initialEntries={['/products?tab=MF']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    );
    const link = await screen.findByRole('link', { name: 'ONE CLIENT' });
    expect(link).toHaveAttribute('href', '/clients/7?tab=mf');
    const row = link.closest('tr') as HTMLElement;
    expect(within(row).getByText(/SB ONE/)).toBeInTheDocument();
    expect(within(row).getByText('Processed')).toBeInTheDocument();
    expect(within(screen.getByRole('link', { name: 'TWO CLIENT' }).closest('tr') as HTMLElement).getByText('not split yet')).toBeInTheDocument();
    expect(screen.queryByLabelText('Payment')).not.toBeInTheDocument();
  });
});
