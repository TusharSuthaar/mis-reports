import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { App } from './App';
import type { Me } from './api/types';
import { AuthProvider } from './app/auth';
import { fakeApi } from './test/fakeApi';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}

const rm: Me = { id: 2, username: 'ravi', displayName: 'Ravi', role: 'RM' };
const admin: Me = { id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN' };

describe('login', () => {
  it('sends signed-out users to the login page', async () => {
    fakeApi({ 'GET /api/auth/me': () => ({ status: 401 }) });
    renderAt('/users');
    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('signs in with a CSRF header and lands on the page that was asked for', async () => {
    document.cookie = 'XSRF-TOKEN=tok123';
    const fetchMock = fakeApi({
      'GET /api/auth/me': () => ({ status: 401 }),
      'POST /api/auth/login': () => ({ status: 200, body: admin }),
      'GET /api/users': () => ({ status: 200, body: [] }),
    });
    renderAt('/users');

    await userEvent.type(await screen.findByLabelText('Username'), 'admin');
    await userEvent.type(screen.getByLabelText('Password'), 'Some-Password-1');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'Users' })).toBeInTheDocument();
    const loginCall = fetchMock.mock.calls.find(([url]) => String(url) === '/api/auth/login')!;
    const init = loginCall[1]!;
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('tok123');
    expect(JSON.parse(String(init.body))).toEqual({ username: 'admin', password: 'Some-Password-1' });
  });

  it('shows a generic message on a wrong password', async () => {
    document.cookie = 'XSRF-TOKEN=tok123';
    fakeApi({
      'GET /api/auth/me': () => ({ status: 401 }),
      'POST /api/auth/login': () => ({ status: 401, body: { detail: 'Invalid username or password' } }),
    });
    renderAt('/');

    await userEvent.type(await screen.findByLabelText('Username'), 'admin');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password');
  });
});

describe('expired login', () => {
  it('goes back to sign-in with a message when the server says the login ended', async () => {
    fakeApi({
      'GET /api/auth/me': () => ({ status: 200, body: admin }),
      'GET /api/audit': () => ({ status: 401 }),
    });
    renderAt('/audit');
    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('You were signed out');
  });
});

describe('role-based navigation', () => {
  it('hides admin screens from an RM and blocks direct links', async () => {
    fakeApi({ 'GET /api/auth/me': () => ({ status: 200, body: rm }) });
    renderAt('/users');

    expect(await screen.findByRole('heading', { name: 'Not allowed' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Dashboard' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Users & audit' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Audit log' })).not.toBeInTheDocument();
  });

  it('shows admin screens to an ADMIN', async () => {
    fakeApi({
      'GET /api/auth/me': () => ({ status: 200, body: admin }),
      'GET /api/audit': () => ({ status: 200, body: { items: [], page: 0, size: 50, total: 0 } }),
    });
    renderAt('/audit');

    expect(await screen.findByRole('heading', { name: 'Audit log' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Users & audit' })).toBeInTheDocument();
    expect(await screen.findByText('No audit entries match.')).toBeInTheDocument();
  });
});
