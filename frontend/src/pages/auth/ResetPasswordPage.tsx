import { useState, type FormEvent } from 'react';
import { Link as RouterLink } from 'react-router';
import { useMutation } from '@tanstack/react-query';
import { Alert, Box, Button, Link, Stack, Typography } from '@mui/material';
import { ApiError, errorMessage } from '../../api/client';
import { authApi } from '../../api/endpoints';
import { useAuth } from '../../auth/AuthContext';
import { AuthCard } from '../../components/common';
import PasswordField, { passwordProblem } from '../../components/PasswordField';
import { useTokenFromUrl } from './useTokenFromUrl';

export default function ResetPasswordPage() {
  const token = useTokenFromUrl();
  const { status, logout } = useAuth();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');

  const reset = useMutation({
    mutationFn: () => authApi.resetPassword(token!, password),
    // The backend revokes every session after a reset, so drop any local one too.
    onSuccess: () => {
      if (status === 'authenticated') logout().catch(() => undefined);
    },
  });

  const problem = password ? passwordProblem(password) : null;
  const mismatch = confirm.length > 0 && confirm !== password;
  const invalidToken = reset.error instanceof ApiError && reset.error.code === 'INVALID_TOKEN';
  const serverFields = reset.error instanceof ApiError ? reset.error.fieldMap() : {};

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!problem && password && confirm === password) reset.mutate();
  };

  if (!token) {
    return (
      <AuthCard title="Reset password">
        <Alert severity="error" sx={{ mb: 2 }}>
          This reset link is incomplete. Open the link from your email again.
        </Alert>
        <Link component={RouterLink} to="/forgot-password">
          Request a new link
        </Link>
      </AuthCard>
    );
  }

  if (reset.isSuccess) {
    return (
      <AuthCard title="Password changed">
        <Typography sx={{ mb: 2 }}>Your password has been changed and you have been signed out everywhere.</Typography>
        <Button component={RouterLink} to="/login" variant="contained">
          Log in
        </Button>
      </AuthCard>
    );
  }

  return (
    <AuthCard title="Choose a new password">
      <Box component="form" onSubmit={onSubmit} noValidate>
        <Stack spacing={2}>
          {invalidToken ? (
            <Alert
              severity="error"
              action={
                <Button component={RouterLink} to="/forgot-password" color="inherit" size="small">
                  New link
                </Button>
              }
            >
              This link is invalid or has expired. Reset links are valid for 30 minutes and can be used once.
            </Alert>
          ) : (
            reset.isError && !serverFields.newPassword && <Alert severity="error">{errorMessage(reset.error)}</Alert>
          )}
          <PasswordField
            label="New password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoFocus
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
          <Button
            type="submit"
            variant="contained"
            size="large"
            disabled={reset.isPending || !password || !!problem || confirm !== password}
          >
            {reset.isPending ? 'Saving…' : 'Change password'}
          </Button>
        </Stack>
      </Box>
    </AuthCard>
  );
}
