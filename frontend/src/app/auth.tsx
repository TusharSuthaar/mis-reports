import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { api, ApiError, onSessionExpired } from '../api/client';
import type { Me, Role } from '../api/types';

interface AuthState {
  user: Me | null;
  loading: boolean;
  /** True while the server cannot be reached (e.g. restarting after an update): the app waits and checks again. */
  waiting: boolean;
  /** True when the user was signed out by the server (e.g. the login expired), not by pressing Sign out. */
  expired: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);
  const [waiting, setWaiting] = useState(false);
  const [expired, setExpired] = useState(false);

  useEffect(() => {
    onSessionExpired(() => {
      setUser(null);
      setExpired(true);
    });
  }, []);

  useEffect(() => {
    // Only "not signed in" (401) means the sign-in page. While the server cannot be reached (restarting after an
    // update) the app waits and checks again, so a valid session is never sent to the sign-in page.
    let cancelled = false;
    const check = (attempt: number) => {
      api<Me>('/api/auth/me')
        .then((me) => {
          if (cancelled) return;
          setUser(me);
          setWaiting(false);
          setLoading(false);
        })
        .catch((e) => {
          if (cancelled) return;
          if (e instanceof ApiError && e.status === 401) {
            setUser(null);
            setWaiting(false);
            setLoading(false);
            return;
          }
          setWaiting(true);
          window.setTimeout(() => check(attempt + 1), Math.min(5000, 1000 * (attempt + 1)));
        });
    };
    check(0);
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    setUser(await api<Me>('/api/auth/login', { method: 'POST', body: { username, password } }));
    setExpired(false);
  }, []);

  const logout = useCallback(async () => {
    await api<void>('/api/auth/logout', { method: 'POST' });
    setUser(null);
  }, []);

  return <AuthContext.Provider value={{ user, loading, waiting, expired, login, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}

/** Sends signed-out users to /login, and shows "not allowed" when the role is wrong. */
export function RequireAuth({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { user, loading, waiting } = useAuth();
  const location = useLocation();
  if (loading) return <p className="muted">{waiting ? 'Waiting for the server… (it may be restarting after an update; this page will continue on its own)' : 'Loading…'}</p>;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (roles && !roles.includes(user.role)) {
    return (
      <section>
        <h1>Not allowed</h1>
        <p>Your role ({user.role}) cannot open this page.</p>
      </section>
    );
  }
  return <>{children}</>;
}
