import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { Box, CircularProgress } from '@mui/material';
import { useAuth } from '../contexts/AuthContext';
import { loginPath } from '../utils/redirect';
import ForbiddenPage from '../components/ForbiddenPage';

// Route guards are UX only: the API enforces every permission (docs/UI_FLOWS.md).

export function FullPageSpinner() {
  return (
    <Box sx={{ display: 'grid', placeItems: 'center', minHeight: '40vh' }}>
      <CircularProgress aria-label="Loading" />
    </Box>
  );
}

export function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'loading') return <FullPageSpinner />;
  if (status === 'anonymous') {
    return <Navigate to={loginPath(location.pathname + location.search)} replace />;
  }
  return <>{children}</>;
}

export function RequireAdmin({ children }: { children: ReactNode }) {
  const { status, isAdmin } = useAuth();
  return <RequireAuth>{status === 'authenticated' && !isAdmin ? <ForbiddenPage /> : children}</RequireAuth>;
}

/** The /admin area: admins see everything, publishers only book management (see AdminRoutes). */
export function RequireStaff({ children }: { children: ReactNode }) {
  const { status, isAdmin, isPublisher } = useAuth();
  return <RequireAuth>{status === 'authenticated' && !isAdmin && !isPublisher ? <ForbiddenPage /> : children}</RequireAuth>;
}

/** For /login, /register etc.: a signed-in user is sent on instead of seeing the form. */
export function RedirectIfAuthenticated({ children, to }: { children: ReactNode; to: string }) {
  const { status } = useAuth();
  if (status === 'loading') return <FullPageSpinner />;
  if (status === 'authenticated') return <Navigate to={to} replace />;
  return <>{children}</>;
}
