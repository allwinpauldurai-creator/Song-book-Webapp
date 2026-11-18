import { useState, type FormEvent } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router';
import { useMutation } from '@tanstack/react-query';
import { Alert, Box, Button, Link, Stack, Typography } from '@mui/material';
import { ApiError, errorMessage } from '../../services/api/client';
import { authApi } from '../../services/api/endpoints';
import { useAuth } from '../../contexts/AuthContext';
import { AuthCard } from '../../components/common';
import { formatMobile, normalizeMobile } from '../../utils/mobile';
import MobileField from './MobileField';
import CodeField, { isCompleteCode } from './CodeField';
import PasswordField, { passwordProblem } from './PasswordField';
import { useResendCountdown } from './useResendCountdown';

/** Two steps on one page: (1) mobile number -> SMS code, (2) code + new password. */
export default function ForgotPasswordPage() {
  const navigate = useNavigate();
  const { status, logout } = useAuth();
  const [mobile, setMobile] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [touched, setTouched] = useState(false);
  const [codeSent, setCodeSent] = useState(false);
  const e164 = normalizeMobile(mobile);
  const { secondsLeft, canResend, restart } = useResendCountdown();

  const send = useMutation({
    mutationFn: () => authApi.forgotPassword(e164!),
    onSuccess: () => {
      setCodeSent(true);
      restart();
    },
  });
  const reset = useMutation({
    mutationFn: () => authApi.resetPassword(e164!, code, password),
    // The backend revokes every session after a reset, so drop any local one too.
    onSuccess: async () => {
      if (status === 'authenticated') await logout().catch(() => undefined);
      navigate('/login', { state: { notice: 'Your password was changed. Log in with the new password.' } });
    },
  });

  const problem = password ? passwordProblem(password, e164) : null;
  const mismatch = confirm.length > 0 && confirm !== password;
  const wrongCode = reset.error instanceof ApiError && reset.error.code === 'INVALID_CODE';
  const serverFields = reset.error instanceof ApiError ? reset.error.fieldMap() : {};

  if (!codeSent) {
    const onSend = (e: FormEvent) => {
      e.preventDefault();
      setTouched(true);
      if (e164) send.mutate();
    };
    return (
      <AuthCard title="Forgot password" subtitle="Enter your mobile number and we will send you a code by SMS.">
        <Box component="form" onSubmit={onSend} noValidate>
          <Stack spacing={2}>
            {send.isError && <Alert severity="error">{errorMessage(send.error)}</Alert>}
            <MobileField value={mobile} onChange={setMobile} required autoFocus showError={touched} />
            <Button type="submit" variant="contained" size="large" disabled={send.isPending || !mobile.trim()}>
              {send.isPending ? 'Sending…' : 'Send code'}
            </Button>
            <Typography sx={{ textAlign: 'center' }} variant="body2">
              <Link component={RouterLink} to="/login">
                Back to log in
              </Link>
            </Typography>
          </Stack>
        </Box>
      </AuthCard>
    );
  }

  const onReset = (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (isCompleteCode(code) && password && !problem && confirm === password) reset.mutate();
  };

  return (
    <AuthCard title="Choose a new password">
      <Box component="form" onSubmit={onReset} noValidate>
        <Stack spacing={2}>
          <Typography>
            If an account exists for <strong>{formatMobile(e164)}</strong>, we have sent it a 6-digit code. It is valid
            for 10 minutes.
          </Typography>
          {send.isError && <Alert severity="error">{errorMessage(send.error)}</Alert>}
          {reset.isError && !Object.keys(serverFields).length && (
            <Alert severity="error">
              {wrongCode ? 'The code is wrong or has expired. Check the SMS or request a new code.' : errorMessage(reset.error)}
            </Alert>
          )}
          <CodeField
            value={code}
            onChange={setCode}
            required
            autoFocus
            error={touched && !isCompleteCode(code)}
            helperText={touched && !isCompleteCode(code) ? 'Enter the 6 digits from the SMS.' : ' '}
          />
          <PasswordField
            label="New password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            error={!!problem || !!serverFields.newPassword}
            helperText={serverFields.newPassword ?? problem ?? 'At least 10 characters.'}
          />
          <PasswordField
            label="Confirm new password"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            required
            error={mismatch}
            helperText={mismatch ? 'Passwords do not match.' : ' '}
          />
          <Button type="submit" variant="contained" size="large" disabled={reset.isPending}>
            {reset.isPending ? 'Saving…' : 'Set new password'}
          </Button>
          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
            <Button size="small" onClick={() => send.mutate()} disabled={!canResend || send.isPending}>
              {canResend ? 'Resend code' : `Resend code in ${secondsLeft}s`}
            </Button>
            <Link component={RouterLink} to="/login" variant="body2">
              Back to log in
            </Link>
          </Stack>
        </Stack>
      </Box>
    </AuthCard>
  );
}
