import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { App } from '../../App';
import type { Employee, Me, OrgChart } from '../../api/types';
import { AuthProvider } from '../../app/auth';
import { fakeApi } from '../../test/fakeApi';
import { defaultRateDay, giftFigures } from './GiftPage';

const me: Me = { id: 1, username: 'ho', displayName: 'A HO', role: 'HO' };
const ok = (body: unknown) => () => ({ status: 200, body });

describe('gift city helpers', () => {
  it('works out the rupee amount and the brokerage figures like the server (R-82)', () => {
    expect(giftFigures(10000, 95.97, 0, 5000, true, 2, 18)).toEqual({ inr: 959700, gross: 5000, tds: 100, gst: 900, net: 4900, invoice: 5900 });
    // No rate: the typed rupee amount; no brokerage yet: nothing.
    expect(giftFigures(5000, 0, 480000, 0, false, 0, 18)).toEqual({ inr: 480000, gross: 0, tds: 0, gst: 0, net: 0, invoice: 0 });
  });

  it('dates a new entry’s rate today in the current month, else on the month’s last day (R-84)', () => {
    expect(defaultRateDay('2026-10', '2026-10-21')).toBe('2026-10-21');
    expect(defaultRateDay('2026-09', '2026-10-21')).toBe('2026-09-30');
    expect(defaultRateDay('2024-02', '2026-10-21')).toBe('2024-02-29');
  });
});

describe('gift city screen', () => {
  it('fetches the day’s exchange rate once a USD amount is typed, and the rupee amount follows', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    let sent: Record<string, unknown> | null = null;
    const rms: Employee[] = [
      { id: 1, empCode: 'E1', name: 'RM ALPHA', type: 'RM', status: 'ACTIVE', joinedOn: null, exitedOn: null, branchId: 10, branchName: 'PATNA' },
    ];
    const chart: OrgChart = { date: '2026-10-01', regions: [], branchesWithoutRegion: [], peopleWithoutBranch: [] };
    fakeApi({
      'GET /api/auth/me': ok(me),
      'GET /api/gift': ok([]),
      'GET /api/gift/funds': ok(['DSP GIFT CITY']),
      'GET /api/employees': ok(rms),
      'GET /api/partners': ok([]),
      'GET /api/org': ok(chart),
      'GET /api/product-taxes/GIFT/on': ok({ id: 1, product: 'GIFT', gstPercent: 18, tdsPercent: 0, tdsByDefault: false, validFrom: '2022-01-01', validTo: null, reason: null, changedBy: 's', changedAt: '' }),
      'GET /api/clients': ok({ items: [], page: 0, size: 20, total: 0 }),
      'POST /api/exchange-rates/USD/lookup': ok({ currency: 'USD', day: '2026-10-21', rate: 95.97, rateDate: '2026-10-20', source: 'ECB', note: 'ECB rate of 2026-10-20 (last working day)', reason: null, changedBy: 'ho', changedAt: '' }),
      'POST /api/gift': (init) => {
        sent = JSON.parse(String(init?.body));
        return { status: 201, body: { id: 1 } };
      },
    });
    render(
      <MemoryRouter initialEntries={['/products?tab=GIFT']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    );
    await userEvent.click(await screen.findByRole('button', { name: 'Add GIFT City entry' }));
    const panel = await screen.findByRole('dialog', { name: 'Add GIFT City entry' });
    const field = (name: RegExp | string) => within(panel).getByLabelText(name);
    await userEvent.type(field('Client PAN or client code'), 'ZZZZZ9999Z');
    await userEvent.click(within(panel).getByRole('button', { name: 'Find' }));
    await userEvent.type(await within(panel).findByLabelText('Client name'), 'NEW GIFT CLIENT');
    await userEvent.selectOptions(field('RM'), '1');
    await userEvent.type(field('Partner (fund)'), 'DSP GIFT CITY');
    await userEvent.type(field('AMT (USD)'), '10000');

    await waitFor(() => expect((field('Exchange rate (₹ per USD)') as HTMLInputElement).value).toBe('95.97'));
    expect(within(panel).getByText(/ECB rate of 2026-10-20/)).toBeInTheDocument();
    expect((field('AMT in Rs.') as HTMLInputElement).value).toBe('9,59,700.00');

    await userEvent.click(within(panel).getByRole('button', { name: 'Add GIFT City entry' }));
    await waitFor(() => expect(sent).not.toBeNull());
    expect(sent).toMatchObject({ amountUsd: 10000, fxRate: 95.97, fxSource: 'ECB rate of 2026-10-20', fund: 'DSP GIFT CITY' });
  });
});
