import { useState, type FormEvent } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router';
import { useMutation } from '@tanstack/react-query';
import { Alert, Box, Button, Link, Stack, TextField, Typography } from '@mui/material';
import { ApiError, errorMessage } from '../../api/client';
import { authApi } from '../../api/endpoints';
import { useAuth } from '../../auth/AuthContext';
import { RedirectIfAuthenticated } from '../../auth/guards';
import { safeNext } from '../../auth/redirect';
import { AuthCard } from '../../components/common';
import PasswordField from '../../components/PasswordField';

const MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: 'Email or password is incorrect.',
  ACCOUNT_LOCKED: 'Too many failed attempts. Your account is locked for 15 minutes.',
  ACCOUNT_DISABLED: 'This account is disabled. Please contact an administrator.',
  EMAIL_NOT_VERIFIED: 'Please verify your email address first.',
};

export default function LoginPage() {
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'), '/me');
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  // On success the auth state flips to "authenticated" and RedirectIfAuthenticated navigates to `next`.
  const submit = useMutation({ mutationFn: () => login(email.trim(), password) });
  const resend = useMutation({ mutationFn: () => authApi.resendVerification(email.trim()) });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    submit.mutate();
  };

  const err = submit.error;
  const code = err instanceof ApiError ? err.code : null;

  return (
    <RedirectIfAuthenticated to={next}>
      <AuthCard title="Log in" subtitle="Members can read and download every song book.">
        <Box component="form" onSubmit={onSubmit} noValidate>
          <Stack spacing={2}>
            {err && (
              <Alert
                severity="error"
                action={
                  code === 'EMAIL_NOT_VERIFIED' && (
                    <Button color="inherit" size="small" disabled={resend.isPending || resend.isSuccess} onClick={() => resend.mutate()}>
                      {resend.isSuccess ? 'Sent' : 'Resend link'}
                    </Button>
                  )
                }
              >
                {(code && MESSAGES[code]) || errorMessage(err)}
              </Alert>
            )}
            <TextField
              label="Email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoFocus
            />
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
            <Button type="submit" variant="contained" size="large" disabled={submit.isPending || !email || !password}>
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
