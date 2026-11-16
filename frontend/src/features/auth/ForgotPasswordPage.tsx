import { useState, type FormEvent } from 'react';
import { Link as RouterLink } from 'react-router';
import { useMutation } from '@tanstack/react-query';
import { Alert, Box, Button, Link, Stack, TextField, Typography } from '@mui/material';
import { errorMessage } from '../../services/api/client';
import { authApi } from '../../services/api/endpoints';
import { AuthCard } from '../../components/common';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const send = useMutation({ mutationFn: () => authApi.forgotPassword(email.trim()) });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (email.trim()) send.mutate();
  };

  if (send.isSuccess) {
    return (
      <AuthCard title="Check your email">
        <Typography sx={{ mb: 2 }}>
          If an account exists for <strong>{email.trim()}</strong>, we have sent a link to reset the password. The link
          is valid for 30 minutes.
        </Typography>
        <Link component={RouterLink} to="/login">
          Back to log in
        </Link>
      </AuthCard>
    );
  }

  return (
    <AuthCard title="Forgot password" subtitle="Enter your email and we will send you a reset link.">
      <Box component="form" onSubmit={onSubmit} noValidate>
        <Stack spacing={2}>
          {send.isError && <Alert severity="error">{errorMessage(send.error)}</Alert>}
          <TextField
            label="Email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoFocus
          />
          <Button type="submit" variant="contained" size="large" disabled={send.isPending || !email.trim()}>
            {send.isPending ? 'Sending…' : 'Send reset link'}
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
