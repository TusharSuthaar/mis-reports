import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api, errorText } from '../../api/client';
import { ROLES, type Role, type User } from '../../api/types';
import { useAuth } from '../../app/auth';
import { SidePanel } from '../../components/SidePanel';
import { formatDateTime } from '../../format';

export function UsersPage() {
  const [users, setUsers] = useState<User[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [panel, setPanel] = useState<{ mode: 'new' } | { mode: 'edit'; user: User } | null>(null);

  const load = useCallback(() => {
    api<User[]>('/api/users').then(setUsers).catch((e) => setLoadError(errorText(e)));
  }, []);
  useEffect(load, [load]);

  const close = useCallback(() => setPanel(null), []);
  const saved = useCallback(() => {
    setPanel(null);
    load();
  }, [load]);

  return (
    <section>
      <div className="page-head">
        <h1>Users</h1>
        <button type="button" onClick={() => setPanel({ mode: 'new' })}>
          New user
        </button>
      </div>
      {loadError && <p className="error">{loadError}</p>}
      {users && (
        <table>
          <thead>
            <tr>
              <th>Username</th>
              <th>Name</th>
              <th>Role</th>
              <th>Status</th>
              <th>Last changed</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id} className="clickable" onClick={() => setPanel({ mode: 'edit', user: u })}>
                <td>{u.username}</td>
                <td>{u.displayName}</td>
                <td>{u.role}</td>
                <td>{u.active ? 'Active' : <span className="muted">Inactive</span>}</td>
                <td>{formatDateTime(u.updatedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {panel?.mode === 'new' && (
        <SidePanel title="New user" onClose={close}>
          <NewUserForm onSaved={saved} />
        </SidePanel>
      )}
      {panel?.mode === 'edit' && (
        <SidePanel title={panel.user.username} onClose={close}>
          <EditUserForm user={panel.user} onSaved={saved} />
          <ResetPasswordForm user={panel.user} />
        </SidePanel>
      )}
    </section>
  );
}

function NewUserForm({ onSaved }: { onSaved: () => void }) {
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [role, setRole] = useState<Role>('RM');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await api('/api/users', { method: 'POST', body: { username, displayName, role, password } });
      onSaved();
    } catch (err) {
      setError(errorText(err));
    }
  }

  return (
    <form onSubmit={onSubmit}>
      <label>
        Username
        <input value={username} onChange={(e) => setUsername(e.target.value)} required />
      </label>
      <label>
        Name
        <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} required />
      </label>
      <label>
        Role
        <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
          {ROLES.map((r) => (
            <option key={r}>{r}</option>
          ))}
        </select>
      </label>
      <label>
        Initial password
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" required />
        <small className="muted">At least 10 characters, must not contain the username.</small>
      </label>
      {error && <p className="error">{error}</p>}
      <button type="submit">Create user</button>
    </form>
  );
}

function EditUserForm({ user, onSaved }: { user: User; onSaved: () => void }) {
  const { user: me } = useAuth();
  const isSelf = me?.id === user.id;
  const [displayName, setDisplayName] = useState(user.displayName);
  const [role, setRole] = useState<Role>(user.role);
  const [active, setActive] = useState(user.active);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await api(`/api/users/${user.id}`, { method: 'PATCH', body: { displayName, role, active } });
      onSaved();
    } catch (err) {
      setError(errorText(err));
    }
  }

  return (
    <form onSubmit={onSubmit}>
      <label>
        Name
        <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} required />
      </label>
      <label>
        Role
        <select value={role} onChange={(e) => setRole(e.target.value as Role)} disabled={isSelf}>
          {ROLES.map((r) => (
            <option key={r}>{r}</option>
          ))}
        </select>
      </label>
      <label className="check">
        <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} disabled={isSelf} />
        Active (can sign in)
      </label>
      {isSelf && <small className="muted">You cannot change your own role or deactivate yourself.</small>}
      {error && <p className="error">{error}</p>}
      <button type="submit">Save</button>
    </form>
  );
}

function ResetPasswordForm({ user }: { user: User }) {
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    try {
      await api(`/api/users/${user.id}/password`, { method: 'POST', body: { password } });
      setPassword('');
      setMessage({ ok: true, text: 'Password changed.' });
    } catch (err) {
      setMessage({ ok: false, text: errorText(err) });
    }
  }

  return (
    <form onSubmit={onSubmit} className="separated">
      <h3>Reset password</h3>
      <label>
        New password
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" required />
      </label>
      {message && <p className={message.ok ? 'ok' : 'error'}>{message.text}</p>}
      <button type="submit" className="secondary">
        Set password
      </button>
    </form>
  );
}
