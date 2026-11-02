import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { useAuth } from '../../app/auth';

export function LoginPage() {
  const { user, loading, expired, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const from = safeReturnPath((location.state as { from?: string } | null)?.from);
  if (!loading && user) return <Navigate to={from} replace />;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(username, password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err instanceof ApiError && err.status === 401 ? 'Invalid username or password' : 'Sign-in failed. Try again.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login">
      <form className="card" onSubmit={onSubmit}>
        <h1>Epsilon MIS</h1>
        {expired && (
          <p className="muted" role="status">
            You were signed out. Please sign in again.
          </p>
        )}
        <label>
          Username
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" autoFocus required />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <button type="submit" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  );
}

/**
 * Where to go after signing in: only a path of this app. "//host", "/\host" and full URLs would leave the site
 * (an open redirect), so they go to the dashboard instead.
 */
export function safeReturnPath(from: string | undefined): string {
  if (!from || !from.startsWith('/') || from.startsWith('//') || from.includes('\\') || /[\u0000-\u001f]/.test(from)) return '/';
  return from;
}
