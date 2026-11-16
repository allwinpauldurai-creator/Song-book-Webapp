import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { useEffect } from 'react';

/**
 * Reads a one-time `?token=` from the URL once, then removes it from the address bar so it does
 * not linger in browser history or get copied into screenshots and bug reports.
 */
export function useTokenFromUrl(): string | null {
  const location = useLocation();
  const navigate = useNavigate();
  const [token] = useState(() => new URLSearchParams(location.search).get('token'));

  useEffect(() => {
    if (new URLSearchParams(location.search).has('token')) {
      navigate(location.pathname, { replace: true });
    }
  }, [location.pathname, location.search, navigate]);

  return token;
}
