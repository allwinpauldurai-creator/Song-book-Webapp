import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { onSessionExpired, refreshAccessToken, setAccessToken } from '../services/api/client';
import { authApi, usersApi } from '../services/api/endpoints';
import type { Me } from '../types/api';

type Status = 'loading' | 'authenticated' | 'anonymous';

interface AuthState {
  status: Status;
  user: Me | null;
  isAdmin: boolean;
  login: (mobile: string, password: string) => Promise<Me>;
  logout: () => Promise<void>;
  /** Re-reads /users/me, e.g. after the profile or membership changed. */
  reloadUser: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

/**
 * Restores the session once per page load: the refresh cookie (if any) is exchanged for an access
 * token, then the user is loaded. Memoised at module level so React StrictMode's double effect run
 * cannot trigger two refresh rotations.
 */
let restorePromise: Promise<Me | null> | null = null;
function restoreSession(): Promise<Me | null> {
  restorePromise ??= (async () => {
    const token = await refreshAccessToken();
    if (!token) return null;
    try {
      return await usersApi.me();
    } catch {
      setAccessToken(null);
      return null;
    }
  })();
  return restorePromise;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<Status>('loading');
  const [user, setUser] = useState<Me | null>(null);

  const applyUser = useCallback((me: Me | null) => {
    setUser(me);
    setStatus(me ? 'authenticated' : 'anonymous');
  }, []);

  useEffect(() => {
    let cancelled = false;
    restoreSession().then((me) => {
      if (!cancelled) applyUser(me);
    });
    return () => {
      cancelled = true;
    };
  }, [applyUser]);

  useEffect(
    () =>
      onSessionExpired(() => {
        setAccessToken(null);
        applyUser(null);
      }),
    [applyUser],
  );

  const login = useCallback(
    async (mobile: string, password: string) => {
      const res = await authApi.login(mobile, password);
      setAccessToken(res.accessToken);
      applyUser(res.user);
      return res.user;
    },
    [applyUser],
  );

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setAccessToken(null);
      restorePromise = null;
      applyUser(null);
      queryClient.clear();
    }
  }, [applyUser, queryClient]);

  const reloadUser = useCallback(async () => {
    applyUser(await usersApi.me());
  }, [applyUser]);

  const value = useMemo<AuthState>(
    () => ({
      status,
      user,
      isAdmin: !!user?.roles.includes('ADMIN'),
      login,
      logout,
      reloadUser,
    }),
    [status, user, login, logout, reloadUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
