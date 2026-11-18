import { useState, type FormEvent } from 'react';
import { Link as RouterLink, useLocation } from 'react-router';
import { useMutation } from '@tanstack/react-query';
import { Alert, Box, Button, Link, Stack, Typography } from '@mui/material';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlineOutlined';
import SmsOutlinedIcon from '@mui/icons-material/SmsOutlined';
import { ApiError, errorMessage } from '../../services/api/client';
import { authApi } from '../../services/api/endpoints';
import { useAuth } from '../../contexts/AuthContext';
import { AuthCard } from '../../components/common';
import { formatMobile, normalizeMobile } from '../../utils/mobile';
import MobileField from './MobileField';
import CodeField, { isCompleteCode } from './CodeField';
import { useResendCountdown } from './useResendCountdown';

interface VerifyState {
  mobile?: string;
  /** A code was just sent (registration or "resend" on the login page): start the countdown. */
  sent?: boolean;
}

export default function VerifyMobilePage() {
  const state = (useLocation().state as VerifyState | null) ?? {};
  const { status, reloadUser } = useAuth();
  const [mobile, setMobile] = useState(state.mobile ?? '');
  const [code, setCode] = useState('');
  const [touched, setTouched] = useState(false);
  const { secondsLeft, canResend, restart } = useResendCountdown(state.sent ? 60 : 0);
  const knownNumber = !!state.mobile;
  const e164 = normalizeMobile(mobile);

  const verify = useMutation({
    mutationFn: () => authApi.verifyMobile(e164!, code),
    onSuccess: () => {
      if (status === 'authenticated') reloadUser().catch(() => undefined);
    },
  });
  const resend = useMutation({ mutationFn: () => authApi.resendVerification(e164!), onSuccess: restart });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (e164 && isCompleteCode(code)) verify.mutate();
  };

  if (verify.isSuccess) {
    return (
      <AuthCard title="Mobile number verified">
        <Stack sx={{ alignItems: 'flex-start' }} spacing={2}>
          <CheckCircleOutlineIcon color="success" sx={{ fontSize: 48 }} />
          <Typography>Your mobile number is verified. You can now log in with it.</Typography>
          <Button component={RouterLink} to={status === 'authenticated' ? '/me' : '/login'} variant="contained">
            {status === 'authenticated' ? 'Go to my dashboard' : 'Log in'}
          </Button>
        </Stack>
      </AuthCard>
    );
  }

  const wrongCode = verify.error instanceof ApiError && verify.error.code === 'INVALID_CODE';

  return (
    <AuthCard title="Enter the SMS code" subtitle="Confirm your mobile number to activate your account.">
      <Box component="form" onSubmit={onSubmit} noValidate>
        <Stack spacing={2}>
          {knownNumber && (
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
              <SmsOutlinedIcon color="primary" />
              <Typography>
                If <strong>{formatMobile(e164)}</strong> can be registered, we have sent it a 6-digit code. It is valid
                for 10 minutes.
              </Typography>
            </Stack>
          )}
          {verify.isError && (
            <Alert severity="error">
              {wrongCode ? 'The code is wrong or has expired. Check the SMS or request a new code.' : errorMessage(verify.error)}
            </Alert>
          )}
          {resend.isSuccess && !verify.isError && <Alert severity="success">A new code has been sent.</Alert>}
          {resend.isError && <Alert severity="error">{errorMessage(resend.error)}</Alert>}
          {!knownNumber && <MobileField value={mobile} onChange={setMobile} required showError={touched} />}
          <CodeField
            value={code}
            onChange={setCode}
            required
            autoFocus={knownNumber}
            error={touched && !isCompleteCode(code)}
            helperText={touched && !isCompleteCode(code) ? 'Enter the 6 digits from the SMS.' : ' '}
          />
          <Button type="submit" variant="contained" size="large" disabled={verify.isPending}>
            {verify.isPending ? 'Checking…' : 'Verify'}
          </Button>
          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
            <Button
              size="small"
              onClick={() => {
                setTouched(true);
                if (e164) resend.mutate();
              }}
              disabled={!canResend || resend.isPending || !e164}
            >
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
