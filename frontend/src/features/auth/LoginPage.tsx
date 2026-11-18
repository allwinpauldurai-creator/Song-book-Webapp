import { useState, type FormEvent } from 'react';
import { Link as RouterLink, useLocation, useSearchParams } from 'react-router';
import { useMutation } from '@tanstack/react-query';
import { Alert, Box, Button, Link, Stack, Typography } from '@mui/material';
import { ApiError, errorMessage } from '../../services/api/client';
import { useAuth } from '../../contexts/AuthContext';
import { RedirectIfAuthenticated } from '../../routes/guards';
import { safeNext } from '../../utils/redirect';
import { normalizeMobile } from '../../utils/mobile';
import { AuthCard } from '../../components/common';
import PasswordField from './PasswordField';
import MobileField from './MobileField';

const MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: 'Mobile number or password is incorrect.',
  ACCOUNT_LOCKED: 'Too many failed attempts. Your account is locked for 15 minutes.',
  ACCOUNT_DISABLED: 'This account is disabled. Please contact an administrator.',
};

export default function LoginPage() {
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'), '/me');
  // e.g. "Your password was changed…", passed in navigation state (never in the URL).
  const notice = (useLocation().state as { notice?: string } | null)?.notice;
  const { login } = useAuth();
  const [mobile, setMobile] = useState('');
  const [password, setPassword] = useState('');
  const [touched, setTouched] = useState(false);
  const e164 = normalizeMobile(mobile);

  // On success the auth state flips to "authenticated" and RedirectIfAuthenticated navigates to `next`.
  const submit = useMutation({ mutationFn: () => login(e164!, password) });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (e164 && password) submit.mutate();
  };

  const err = submit.error;
  const code = err instanceof ApiError ? err.code : null;

  return (
    <RedirectIfAuthenticated to={next}>
      <AuthCard title="Log in" subtitle="Members can read and download every song book.">
        <Box component="form" onSubmit={onSubmit} noValidate>
          <Stack spacing={2}>
            {notice && !err && <Alert severity="success">{notice}</Alert>}
            {err && <Alert severity="error">{(code && MESSAGES[code]) || errorMessage(err)}</Alert>}
            <MobileField value={mobile} onChange={setMobile} required autoFocus showError={touched} />
            <PasswordField
              label="Password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            <Box sx={{ textAlign: 'right', mt: -1 }}>
              <Link component={RouterLink} to="/forgot-password" variant="body2">
                Forgot password?
              </Link>
            </Box>
            <Button type="submit" variant="contained" size="large" disabled={submit.isPending || !mobile || !password}>
              {submit.isPending ? 'Logging in…' : 'Log in'}
            </Button>
            <Typography sx={{ textAlign: 'center' }} variant="body2">
              New here?{' '}
              <Link component={RouterLink} to="/register">
                Create an account
              </Link>
            </Typography>
          </Stack>
        </Box>
      </AuthCard>
    </RedirectIfAuthenticated>
  );
}
