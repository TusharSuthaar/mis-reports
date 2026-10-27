import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { App } from '../../App';
import { AuthProvider } from '../../app/auth';
import { fakeApi } from '../../test/fakeApi';

const ok = (body: unknown) => () => ({ status: 200, body });

/** The server's rules per kind of insurance (R-96), as GET /api/insurance/categories sends them. */
const CATEGORIES = [
  { category: 'LIFE', label: 'Life', subTypeLabel: 'Plan type', subTypes: ['Term', 'Savings – Non-PAR', 'ULIP'], businessTypes: ['NEW', 'RENEWAL', 'FYCI'],
    sumLabel: 'Sum assured', sumRequired: true, pptLabel: 'Premium paying term (years)', pptChoices: [], termRequired: true, premium: 'TOTAL', gstPercent: 0 },
  { category: 'HEALTH', label: 'Health', subTypeLabel: 'Cover type', subTypes: ['Individual', 'Floater'], businessTypes: ['NEW', 'RENEWAL', 'PORT', 'ENDORSEMENT'],
    sumLabel: 'Sum insured', sumRequired: true, pptLabel: 'Policy period (years)', pptChoices: ['1', '2', '3'], termRequired: false, premium: 'TOTAL', gstPercent: 0 },
  { category: 'MOTOR', label: 'Motor', subTypeLabel: 'Vehicle', subTypes: ['Private car', 'Two-wheeler'], businessTypes: ['NEW', 'RENEWAL', 'ENDORSEMENT'],
    sumLabel: 'IDV', sumRequired: false, pptLabel: 'Policy period (OD + TP years)', pptChoices: ['1', '1+3', '1+5'], termRequired: false, premium: 'OD_TP', gstPercent: 18 },
  { category: 'GENERAL', label: 'General', subTypeLabel: 'Policy type', subTypes: ['Fire – Corporate', 'Marine'], businessTypes: ['NEW', 'RENEWAL', 'ENDORSEMENT'],
    sumLabel: 'Sum insured', sumRequired: true, pptLabel: 'Policy period (years)', pptChoices: ['1', '2', '3', '5'], termRequired: false, premium: 'TOTAL_LESS_GST', gstPercent: 18 },
];

function open(posted: unknown[] = [], postStatus = 201) {
  fakeApi({
    'GET /api/auth/me': ok({ id: 1, username: 'ho', displayName: 'A HO', role: 'HO' }),
    'GET /api/employees': ok([]),
    'GET /api/partners': ok([{ id: 7, type: 'POSP', name: 'POSP ONE', status: 'ACTIVE' }]),
    'GET /api/insurance': ok([]),
    'GET /api/insurance/choices': ok({ insurers: ['TATA AIG'], products: ['AUTO SECURE'], subTypes: [] }),
    'GET /api/insurance/categories': ok(CATEGORIES),
    'GET /api/insurance/defaults': ok({ category: null, subType: null, expectedPercent: null, pospShare: null }),
    'GET /api/clients': ok({ items: [{ id: 5, name: 'CLIENT ONE', pan: 'ABCDE1234F', iwellCode: 'FC1', rmId: 3, rmName: 'RM ALPHA', branchName: 'PUNE' }], total: 1 }),
    'POST /api/insurance': (init) => {
      posted.push(JSON.parse(String(init?.body)));
      return postStatus === 201 ? { status: 201, body: { id: 1 } } : { status: postStatus, body: { detail: 'Enter the sum insured' } };
    },
  });
  render(
    <MemoryRouter initialEntries={['/products?tab=INS&new=1']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}

const field = (dialog: HTMLElement, label: RegExp) => within(dialog).getByLabelText(label) as HTMLInputElement | HTMLSelectElement;
const details = (dialog: HTMLElement) => within(dialog).queryByRole('group', { name: /details$/ });
const businessTypes = (dialog: HTMLElement) =>
  within(dialog).getByText('Business type').nextElementSibling!.querySelectorAll('button').length
    ? [...within(dialog).getByText('Business type').nextElementSibling!.querySelectorAll('button')].map((b) => b.textContent)
    : [];
const options = (el: HTMLElement) => [...(el as HTMLSelectElement).options].map((o) => o.text);

describe('insurance form: each kind of insurance shows its own fields (R-96)', () => {
  it('shows no type section until a type is picked, then only that type’s options and required fields', async () => {
    open();
    const dialog = await screen.findByRole('dialog', { name: 'Add insurance policy' });
    expect(details(dialog)).toBeNull();
    expect(within(dialog).queryByText('Business type')).toBeNull();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Life' }));
    expect(await within(dialog).findByRole('group', { name: '3 · Life details' })).toBeInTheDocument();
    expect(businessTypes(dialog)).toEqual(['New', 'Renewal', 'FYCI']);
    expect(options(field(dialog, /^Plan type/))).toEqual(['Choose…', 'Term', 'Savings – Non-PAR', 'ULIP', 'Other…']);
    expect(field(dialog, /^Sum assured/)).toBeRequired();
    expect(field(dialog, /^Premium paying term/)).toBeRequired();
    expect(field(dialog, /^Policy term/)).toBeRequired();
    expect(within(dialog).queryByLabelText(/^OD premium/)).toBeNull();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Health' }));
    expect(await within(dialog).findByRole('group', { name: '3 · Health details' })).toBeInTheDocument();
    expect(businessTypes(dialog)).toEqual(['New', 'Renewal', 'Port', 'Endorsement']);
    expect(options(field(dialog, /^Cover type/))).toEqual(['Choose…', 'Individual', 'Floater', 'Other…']);
    expect(field(dialog, /^Sum insured/)).toBeRequired();
    expect(options(field(dialog, /^Policy period/))).toEqual(['1', '2', '3']);
    expect(field(dialog, /^Policy period/)).toHaveValue('1');            // first period chosen
    expect(within(dialog).queryByLabelText(/^Policy term/)).toBeNull();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Motor' }));
    expect(await within(dialog).findByRole('group', { name: '3 · Motor details' })).toBeInTheDocument();
    expect(businessTypes(dialog)).toEqual(['New', 'Renewal', 'Endorsement']);
    expect(options(field(dialog, /^Vehicle/))).toEqual(['Choose…', 'Private car', 'Two-wheeler', 'Other…']);
    expect(field(dialog, /^IDV/)).not.toBeRequired();
    expect(options(field(dialog, /^Policy period/))).toEqual(['1', '1+3', '1+5']);
    expect(field(dialog, /^OD premium/)).toBeRequired();
    expect(field(dialog, /^TP premium/)).toBeRequired();

    await userEvent.click(within(dialog).getByRole('button', { name: 'General' }));
    expect(await within(dialog).findByRole('group', { name: '3 · General details' })).toBeInTheDocument();
    expect(options(field(dialog, /^Policy type/))).toEqual(['Choose…', 'Fire – Corporate', 'Marine', 'Other…']);
    expect(field(dialog, /^Sum insured/)).toBeRequired();
    expect(options(field(dialog, /^Policy period/))).toEqual(['1', '2', '3', '5']);
    expect(within(dialog).queryByLabelText(/^OD premium/)).toBeNull();
  });

  it('drops a business type the new type does not have, and its sub type', async () => {
    open();
    const dialog = await screen.findByRole('dialog', { name: 'Add insurance policy' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Life' }));
    await userEvent.click(await within(dialog).findByRole('button', { name: 'FYCI' }));
    await userEvent.selectOptions(field(dialog, /^Plan type/), 'Term');
    expect(within(dialog).getByRole('button', { name: 'FYCI' })).toHaveClass('on');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Health' }));
    await within(dialog).findByRole('group', { name: '3 · Health details' });
    expect(within(dialog).queryByRole('button', { name: 'FYCI' })).toBeNull();
    expect(within(dialog).getAllByRole('button').filter((b) => b.classList.contains('on')).map((b) => b.textContent)).toEqual(['Health']);
    expect(field(dialog, /^Cover type/)).toHaveValue('');
  });

  it('fills the net premium the way each type works it out, until it is typed over', async () => {
    open();
    const dialog = await screen.findByRole('dialog', { name: 'Add insurance policy' });
    const net = () => field(dialog, /^Net premium/);
    const total = () => field(dialog, /^Total premium/);

    await userEvent.click(within(dialog).getByRole('button', { name: 'Life' }));
    await userEvent.type(total(), '10000');
    expect(net()).toHaveValue('10,000');                                  // Life: no GST
    expect(within(dialog).getByText(/Net = total \(no GST on life\)/)).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole('button', { name: 'General' }));
    await userEvent.clear(total());
    await userEvent.type(total(), '11800');
    expect(net()).toHaveValue('10,000');                                  // General: total less 18% GST

    await userEvent.click(within(dialog).getByRole('button', { name: 'Motor' }));
    await userEvent.type(field(dialog, /^OD premium/), '3000');
    await userEvent.type(field(dialog, /^TP premium/), '2000');
    expect(net()).toHaveValue('5,000');                                   // Motor: OD + TP
    await userEvent.selectOptions(field(dialog, /^Cover/), 'TP');
    expect(within(dialog).queryByLabelText(/^OD premium/)).toBeNull();   // third party only: no OD
    expect(net()).toHaveValue('2,000');
    await userEvent.selectOptions(field(dialog, /^Cover/), 'OD');
    expect(within(dialog).queryByLabelText(/^TP premium/)).toBeNull();
    expect(net()).toHaveValue('3,000');

    await userEvent.clear(net());
    await userEvent.type(net(), '2900');
    await userEvent.type(field(dialog, /^OD premium/), '0');              // typed over: stays as typed
    expect(net()).toHaveValue('2,900');
  });

  it('takes a sub type that is not in the list', async () => {
    open();
    const dialog = await screen.findByRole('dialog', { name: 'Add insurance policy' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'General' }));
    await userEvent.selectOptions(field(dialog, /^Policy type/), 'Other…');
    const other = field(dialog, /^Policy type \(other\)/);
    expect(other).toBeRequired();
    await userEvent.type(other, 'Burglary');
    expect(other).toHaveValue('Burglary');
    expect(field(dialog, /^Policy type(?! \(other\))/)).toHaveValue('__other__');
  });

  it('saves a Motor third-party policy for the client found by PAN, with only that cover’s premium', async () => {
    const posted: Record<string, unknown>[] = [];
    open(posted);
    const dialog = await screen.findByRole('dialog', { name: 'Add insurance policy' });
    await userEvent.type(field(dialog, /^PAN, client code or name/), 'ABCDE1234F');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Find' }));
    expect(await within(dialog).findByText(/CLIENT ONE/)).toHaveTextContent('RM RM ALPHA · PUNE');
    expect(within(dialog).queryByLabelText(/^RM/)).toBeNull();            // the client's RM: not asked

    await userEvent.click(within(dialog).getByRole('button', { name: 'Motor' }));
    await userEvent.click(await within(dialog).findByRole('button', { name: 'Renewal' }));
    await userEvent.type(field(dialog, /^Insurance company/), 'TATA AIG');
    await userEvent.type(field(dialog, /^Product name/), 'AUTO SECURE');
    await userEvent.type(field(dialog, /^Policy no/), 'M-1');
    await userEvent.selectOptions(field(dialog, /^Vehicle/), 'Two-wheeler');
    await userEvent.selectOptions(field(dialog, /^Cover/), 'TP');
    await userEvent.selectOptions(field(dialog, /^Policy period/), '1+5');
    await userEvent.type(field(dialog, /^TP premium/), '1500');
    await userEvent.type(field(dialog, /^Total premium/), '1770');
    await userEvent.type(field(dialog, /^Expected commission %/), '2.5');
    await userEvent.type(field(dialog, /^POSP/), 'POSP ONE');
    await userEvent.type(field(dialog, /^% agreed/), '50');
    expect(within(dialog).getByText('Expected commission').nextElementSibling).toHaveTextContent('37.50');   // 1,500 × 2.5%
    expect(within(dialog).getByText('POSP commission').nextElementSibling).toHaveTextContent('18.75');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add policy' }));

    expect(posted).toHaveLength(1);
    expect(posted[0]).toMatchObject({
      clientId: 5, rmId: null, category: 'MOTOR', businessType: 'RENEWAL', subType: 'Two-wheeler', ppt: '1+5',
      odPremium: null, tpPremium: 1500, totalPremium: 1770, netPremium: 1500, expectedPercent: 2.5, pospId: 7, newPospName: null, pospShare: 50, policyTerm: null,
    });
  });

  it('shows the server’s reason when it refuses a policy', async () => {
    open([], 400);
    const dialog = await screen.findByRole('dialog', { name: 'Add insurance policy' });
    await userEvent.type(field(dialog, /^PAN, client code or name/), 'ABCDE1234F');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Find' }));
    await within(dialog).findByText(/CLIENT ONE/);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Health' }));
    await userEvent.click(await within(dialog).findByRole('button', { name: 'New' }));
    await userEvent.type(field(dialog, /^Insurance company/), 'CARE');
    await userEvent.type(field(dialog, /^Product name/), 'SUPREME');
    await userEvent.type(field(dialog, /^Policy no/), 'H-1');
    await userEvent.selectOptions(field(dialog, /^Cover type/), 'Floater');
    await userEvent.type(field(dialog, /^Sum insured/), '500000');
    await userEvent.type(field(dialog, /^Total premium/), '20000');
    await userEvent.type(field(dialog, /^Expected commission %/), '15');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add policy' }));
    expect(await within(dialog).findByText('Enter the sum insured')).toBeInTheDocument();
  });
});
