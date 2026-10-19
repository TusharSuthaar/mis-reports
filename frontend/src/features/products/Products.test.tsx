import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { App } from '../../App';
import type { Me, Role } from '../../api/types';
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
const base = { rmName: 'RM ALPHA', status: 'ACTIVE', paymentStatus: 'PENDING' };

describe('product entries', () => {
  it('lists every product together, newest month first, with totals per product', async () => {
    fakeApi({
      'GET /api/auth/me': ok(me('HO')),
      'GET /api/employees': ok([]),
      'GET /api/partners': ok([]),
      'GET /api/fd': ok([{ ...base, id: 1, month: '2026-07', clientName: 'FD CLIENT', fdName: 'BAJAJ FINANCE', fdNo: '9', amount: 500000, gross: 3850, invoice: 4543 }]),
      'GET /api/bonds': ok([{ ...base, id: 2, month: '2026-08', clientName: 'BOND CLIENT', bondName: 'REC BOND', consideration: 590000, gross: 885, invoice: 1044.3 }]),
      'GET /api/aif': ok([{ ...base, id: 3, month: '2026-08', clientName: 'AIF CLIENT', fundName: 'VALUE FUND', commitment: 10000000, gross: 130000, invoice: 153400, paymentStatus: 'RECEIVED' }]),
    });
    renderAt('/products');
    expect(await screen.findByRole('heading', { name: 'Product entries' })).toBeInTheDocument();
    const clients = (await screen.findAllByRole('button', { name: /CLIENT$/ })).map((b) => b.textContent);
    expect(clients).toEqual(['AIF CLIENT', 'BOND CLIENT', 'FD CLIENT']);   // Aug before Jul
    expect(screen.getByText('Total (3 entries)')).toBeInTheDocument();
    expect(screen.getByText('1,34,735.00')).toBeInTheDocument();           // 3,850 + 885 + 1,30,000
    expect(screen.getByRole('tab', { name: 'All products' })).toHaveAttribute('aria-selected', 'true');
  });

  it('starts a new entry of the product picked, in its tab', async () => {
    fakeApi({
      'GET /api/auth/me': ok(me('HO')),
      'GET /api/employees': ok([]),
      'GET /api/partners': ok([]),
      'GET /api/fd': ok([]),
      'GET /api/bonds': ok([]),
      'GET /api/aif': ok([]),
      'GET /api/fd/suggestions': ok({ fdNames: [], platforms: [], margins: [] }),
    });
    renderAt('/products');
    await userEvent.click(await screen.findByRole('button', { name: /New entry/ }));
    await userEvent.click(within(screen.getByRole('menu')).getByRole('menuitem', { name: 'New FD' }));
    expect(await screen.findByRole('dialog', { name: 'Add FD' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'FD' })).toHaveAttribute('aria-selected', 'true');
  });
});

describe('dashboard', () => {
  it('shows the month by product, where the month close stands, and what is waiting', async () => {
    fakeApi({
      'GET /api/auth/me': ok(me('ADMIN')),
      'GET /api/months': ok([{ periodId: 2, month: '2026-08', status: 'PROCESSED', saved: { lines: 2394, net: 2533049.32 }, notInMaster: 0 }]),
      'GET /api/fd': ok([]),
      'GET /api/bonds': ok([{ gross: 5932.2 }]),
      'GET /api/aif': ok([{ gross: 130000 }]),
      'GET /api/fd/alerts': ok({ pendingPayments: 0 }),
      'GET /api/bonds/alerts': ok({ pendingPayments: 2 }),
      'GET /api/aif/alerts': ok({ pendingPayments: 0 }),
      'GET /api/clients/attention': ok({ sharedPan: 0, noRm: 6, noPan: 0 }),
    });
    renderAt('/');
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
    expect(await screen.findByText('Mutual funds')).toBeInTheDocument();
    expect(screen.getByText('2,394 clients')).toBeInTheDocument();
    expect(await screen.findByText(/bond invoices not fully paid/)).toBeInTheDocument();
    expect(screen.getByText(/clients without an RM/)).toBeInTheDocument();
    expect(screen.getByText('Processed').closest('.step')).toHaveClass('done');
    expect(screen.getByText('Locked').closest('.step')).not.toHaveClass('done');
  });
});
