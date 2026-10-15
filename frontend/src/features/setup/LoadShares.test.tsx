import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { App } from '../../App';
import type { ClientRow, Job, Me, Page, Share, ShareLoadPreview, ShareLoadResult } from '../../api/types';
import { AuthProvider } from '../../app/auth';
import { fakeApi } from '../../test/fakeApi';

const admin: Me = { id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN' };
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

const preview: ShareLoadPreview = {
  token: 'share-loads/x.xlsx',
  fileName: 'Master_MIS.xlsx',
  error: null,
  rows: 5673,
  rowsWithPartner: 1080,
  matchedClients: 2420,
  toSet: 651,
  toEnd: 0,
  unchanged: 1769,
  zeroPct: 65,
  bothRoles: 0,
  matchedByPanOnly: 0,
  conflicts: 0,
  newPartners: [{ name: 'ALPHA WEALTH', type: 'SUB_BROKER', clients: 170 }],
  unmatchedCount: 1,
  unmatched: [{ row: 9, clientName: 'NEW CLIENT', maskedPan: 'ABCDE****F', reason: 'No client with this PAN yet' }],
  problems: [],
  from: '2026-08-01',
  newClients: 3021,
  newClientsWithoutPan: 112,
  rmChangeCount: 1,
  rmUnchanged: 2400,
  clientsWithoutRm: 1,
  rmChanges: [{ clientId: 4, client: 'MOVED CLIENT', pan: 'PAN on file', fromRm: 'RM GAMMA_HO', toRm: 'RM GAMMA_MUM' }],
  unknownRms: [{ spelling: 'SOMEONE NEW', note: 'no RM with this name', clients: 2 }],
  partnerRates: { sheetPartners: 108, percentChanges: 1188, followsPartner: 1097, clientExceptions: 17, pending: ['NEW REFERRER'], approved: [] },
};

describe('upload Master MIS', () => {
  it('shows new clients, RM changes and shares, and applies them from the chosen date', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    let sent: Record<string, unknown> | null = null;
    let previewDate: string | null = null;
    const result: ShareLoadResult = {
      fileName: 'x', clientsCreated: 3021, rmChanged: 1, sharesSet: 651, sharesEnded: 0, unchanged: 1769, partnersCreated: 1, zeroPct: 65, unmatched: 1,
      productPercentsSet: 1188, clientExceptions: 17,
    };
    const job: Job<ShareLoadResult> = { id: 3, type: 'share-load', title: 'Uploaded the Master MIS', status: 'DONE', result, error: null, reference: 'TESTREF1' };
    fakeApi({
      'GET /api/auth/me': ok(admin),
      'POST /api/share-load/preview': (init) => {
        previewDate = (init?.body as FormData).get('from') as string;
        return { status: 200, body: preview };
      },
      'POST /api/share-load': (init) => {
        sent = JSON.parse(String(init?.body));
        return { status: 202, body: { jobId: 3 } };
      },
      'GET /api/jobs/3': ok(job),
    });
    renderAt('/setup/load-shares');

    await userEvent.upload(await screen.findByLabelText('Client master'), new File(['x'], 'Master_MIS.xlsx'));
    await userEvent.click(screen.getByRole('button', { name: 'Check file' }));

    const summary = await screen.findByRole('table', { name: 'Summary' });
    expect(within(summary).getByText('Client % of 0 (this client gets nothing)').nextSibling).toHaveTextContent('65');
    expect(within(summary).getByText('Partner product %s to set or change').closest('tr')).toHaveTextContent('1188');
    expect(within(summary).getByText('Client links with their own % (differs from the partner’s)').nextSibling).toHaveTextContent('17');
    expect(screen.getByText('NEW REFERRER')).toBeInTheDocument();
    expect(screen.getByText('1 partners will be created')).toBeInTheDocument();
    expect(screen.getByText('No client with this PAN yet')).toBeInTheDocument();
    expect(previewDate).toBe('2026-08-01');
    expect(within(summary).getByText('New clients to add').closest('tr')).toHaveTextContent('3021');
    expect(screen.getByText('MOVED CLIENT')).toBeInTheDocument();
    expect(screen.getByText(/SOMEONE NEW/)).toBeInTheDocument();

    await userEvent.click(
      screen.getByRole('button', { name: 'Apply from 2026-08-01: 3021 new clients, 1 RM changes, 651 share changes, 1188 partner %s' }),
    );
    expect(await screen.findByText(/3021 clients added, 1 RMs changed, 651 shares set/)).toBeInTheDocument();
    expect(sent).toEqual({ token: 'share-loads/x.xlsx', fileName: 'Master_MIS.xlsx', from: '2026-08-01' });
  });
});

describe('clients with partners', () => {
  it('shows each client partner and % in the list', async () => {
    const page: Page<ClientRow> = {
      items: [{ id: 7, name: 'SHARED CLIENT', pan: null, iwellCode: '1', rmId: null, rmName: null, branchName: null }],
      page: 0,
      size: 50,
      total: 1,
    };
    const base = { validFrom: '2026-08-01', validTo: null, reason: null, changedBy: 'admin', changedAt: '2026-10-09T00:00:00Z' };
    const sb: Share = { ...base, id: 1, clientId: 7, partnerId: 2, partnerName: 'ALPHA WEALTH', partnerRole: 'SUB_BROKER', percent: 50, clientPercent: null, source: 'PARTNER', branch: null };
    const ref: Share = { ...base, id: 2, clientId: 7, partnerId: 3, partnerName: 'PARTNER TWO', partnerRole: 'REFERRAL', percent: 20, clientPercent: 20, source: 'CLIENT', branch: null };
    fakeApi({
      'GET /api/auth/me': ok(admin),
      'GET /api/employees': ok([]),
      'GET /api/clients': ok(page),
      'GET /api/shares': ok({ 7: [sb, ref] }),
    });
    renderAt('/clients');
    expect(await screen.findByText(/ALPHA WEALTH 50%/)).toBeInTheDocument();
    expect(screen.getByText(/PARTNER TWO 20%/)).toBeInTheDocument();
    expect(screen.getByText('(Sub-broker)')).toBeInTheDocument();
    expect(screen.getByText('(Referral partner)')).toBeInTheDocument();
  });
});

describe('partner rate cards', () => {
  it('shows each partner its MF % and, opened, its % for every product', async () => {
    const partner = { id: 5, type: 'REFERRAL', name: 'REFERRAL B', pan: null, gstin: null, arnOrPospCode: null, bankRef: null, status: 'PENDING', currentRmLinks: 0 };
    const card = [
      { id: 1, partnerId: 5, product: 'MF', percent: 0, validFrom: '2026-08-01', validTo: null, reason: null, changedBy: 'admin', changedAt: '2026-10-15T00:00:00Z' },
      { id: 2, partnerId: 5, product: 'EQUITY', percent: 50, validFrom: '2026-08-01', validTo: null, reason: null, changedBy: 'admin', changedAt: '2026-10-15T00:00:00Z' },
    ];
    fakeApi({
      'GET /api/auth/me': ok(admin),
      'GET /api/partners': ok([partner]),
      'GET /api/shares/partner-counts': ok({ 5: 12 }),
      'GET /api/partners/product-percents': ok({ 5: { MF: 0, EQUITY: 50 } }),
      'GET /api/partners/5/products': ok(card),
      'GET /api/partners/5/rm-links': ok([]),
      'GET /api/employees': ok([]),
    });
    renderAt('/setup/partners');

    const row = (await screen.findByText('REFERRAL B')).closest('tr')!;
    expect(row).toHaveTextContent('Pending approval');
    await userEvent.click(row);
    const rates = (await screen.findByText('Sharing % by product')).closest('div')!;
    expect(within(rates).getByRole('cell', { name: 'Equity' }).closest('tr')).toHaveTextContent('50%');
    expect(within(rates).getByRole('cell', { name: 'Bonds' }).closest('tr')).toHaveTextContent('not set');
  });
});
