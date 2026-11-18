import { useState, type FormEvent } from 'react';
import { Link as RouterLink, useLocation, useNavigate, useSearchParams } from 'react-router';
import { useMutation } from '@tanstack/react-query';
import { Alert, Box, Button, Link, Stack, Typography } from '@mui/material';
import { ApiError, errorMessage } from '../../services/api/client';
import { authApi } from '../../services/api/endpoints';
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
  MOBILE_NOT_VERIFIED: 'Please confirm your mobile number with the SMS code first.',
};

export default function LoginPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
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
  const resend = useMutation({
    mutationFn: () => authApi.resendVerification(e164!),
    onSuccess: () => navigate('/verify-mobile', { state: { mobile: e164, sent: true } }),
  });

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
            {err && (
              <Alert
                severity="error"
                action={
                  code === 'MOBILE_NOT_VERIFIED' && (
                    <Button color="inherit" size="small" disabled={resend.isPending} onClick={() => resend.mutate()}>
                      Send code
                    </Button>
                  )
                }
              >
                {(code && MESSAGES[code]) || errorMessage(err)}
              </Alert>
            )}
            {resend.isError && <Alert severity="error">{errorMessage(resend.error)}</Alert>}
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
