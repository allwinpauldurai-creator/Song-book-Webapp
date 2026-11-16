import { useEffect, useRef } from 'react';
import { Link as RouterLink } from 'react-router';
import { useMutation } from '@tanstack/react-query';
import { Alert, Button, CircularProgress, Stack, Typography } from '@mui/material';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlineOutlined';
import { authApi } from '../../api/endpoints';
import { useAuth } from '../../auth/AuthContext';
import { AuthCard } from '../../components/common';
import { useTokenFromUrl } from './useTokenFromUrl';

export default function VerifyEmailPage() {
  const token = useTokenFromUrl();
  const { status, reloadUser } = useAuth();
  const verify = useMutation({
    mutationFn: (t: string) => authApi.verifyEmail(t),
    onSuccess: () => {
      // A signed-in user's emailVerified flag (and possibly membership) just changed.
      if (status === 'authenticated') reloadUser().catch(() => undefined);
    },
  });

  // The token is single-use: StrictMode runs effects twice in dev, so guard with a ref.
  const sent = useRef(false);
  useEffect(() => {
    if (token && !sent.current) {
      sent.current = true;
      verify.mutate(token);
    }
  }, [token, verify]);

  return (
    <AuthCard title="Verify email">
      {!token ? (
        <Alert severity="error">This verification link is incomplete. Open the link from your email again.</Alert>
      ) : verify.isSuccess ? (
        <Stack sx={{ alignItems: 'flex-start' }} spacing={2}>
          <CheckCircleOutlineIcon color="success" sx={{ fontSize: 48 }} />
          <Typography>Your email address is verified.</Typography>
          <Button component={RouterLink} to={status === 'authenticated' ? '/me' : '/login'} variant="contained">
            {status === 'authenticated' ? 'Go to my dashboard' : 'Log in'}
          </Button>
        </Stack>
      ) : verify.isError ? (
        <Stack sx={{ alignItems: 'flex-start' }} spacing={2}>
          <Alert severity="error">
            This link is invalid or has expired. Verification links are valid for 24 hours and can be used once.
          </Alert>
          <Typography variant="body2" color="text.secondary">
            Log in to request a new link.
          </Typography>
          <Button component={RouterLink} to="/login" variant="outlined">
            Log in
          </Button>
        </Stack>
      ) : (
        <Stack sx={{ alignItems: 'center' }} direction="row" spacing={2}>
          <CircularProgress size={24} />
          <Typography>Verifying your email…</Typography>
        </Stack>
      )}
    </AuthCard>
  );
}
