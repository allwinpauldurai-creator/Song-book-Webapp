import { useState, type FormEvent } from 'react';
import { Link as RouterLink } from 'react-router';
import { useMutation } from '@tanstack/react-query';
import { Alert, Box, Button, Link, Stack, TextField, Typography } from '@mui/material';
import MarkEmailReadOutlinedIcon from '@mui/icons-material/MarkEmailReadOutlined';
import { ApiError, errorMessage } from '../../services/api/client';
import { authApi } from '../../services/api/endpoints';
import { RedirectIfAuthenticated } from '../../routes/guards';
import { AuthCard } from '../../components/common';
import PasswordField, { passwordProblem } from './PasswordField';

export default function RegisterPage() {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [touched, setTouched] = useState(false);

  const register = useMutation({ mutationFn: () => authApi.register(fullName.trim(), email.trim(), password) });

  const pwProblem = passwordProblem(password, email);
  const mismatch = confirm.length > 0 && confirm !== password;
  const serverFields = register.error instanceof ApiError ? register.error.fieldMap() : {};

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (!fullName.trim() || !email.trim() || pwProblem || confirm !== password) return;
    register.mutate();
  };

  if (register.isSuccess) {
    return (
      <AuthCard title="Check your email">
        <Stack sx={{ alignItems: 'flex-start' }} spacing={2}>
          <MarkEmailReadOutlinedIcon color="primary" sx={{ fontSize: 48 }} />
          <Typography>
            If <strong>{email.trim()}</strong> can be registered, we have sent it a verification link. Open the link to
            activate your account. It is valid for 24 hours.
          </Typography>
          <Typography variant="body2" color="text.secondary">
            No email? Check your spam folder, or{' '}
            <Link component={RouterLink} to="/login">
              log in
            </Link>{' '}
            to request a new link.
          </Typography>
        </Stack>
      </AuthCard>
    );
  }

  return (
    <RedirectIfAuthenticated to="/me">
      <AuthCard title="Create an account" subtitle="Register to read and download song books.">
        <Box component="form" onSubmit={onSubmit} noValidate>
          <Stack spacing={2}>
            {register.isError && !Object.keys(serverFields).length && <Alert severity="error">{errorMessage(register.error)}</Alert>}
            <TextField
              label="Full name"
              autoComplete="name"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              required
              autoFocus
              error={(touched && !fullName.trim()) || !!serverFields.fullName}
              helperText={serverFields.fullName ?? (touched && !fullName.trim() ? 'Enter your name.' : ' ')}
              slotProps={{ htmlInput: { maxLength: 150 } }}
            />
            <TextField
              label="Email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              error={(touched && !email.trim()) || !!serverFields.email}
              helperText={serverFields.email ?? (touched && !email.trim() ? 'Enter your email.' : ' ')}
              slotProps={{ htmlInput: { maxLength: 254 } }}
            />
            <PasswordField
              label="Password"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              error={(touched || password.length > 0) && !!pwProblem || !!serverFields.password}
              helperText={serverFields.password ?? ((touched || password.length > 0) && pwProblem ? pwProblem : 'At least 10 characters.')}
            />
            <PasswordField
              label="Confirm password"
              autoComplete="new-password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              required
              error={mismatch || (touched && !confirm)}
              helperText={mismatch ? 'Passwords do not match.' : ' '}
            />
            <Button type="submit" variant="contained" size="large" disabled={register.isPending}>
              {register.isPending ? 'Creating account…' : 'Create account'}
            </Button>
            <Typography sx={{ textAlign: 'center' }} variant="body2">
              Already a member?{' '}
              <Link component={RouterLink} to="/login">
                Log in
              </Link>
            </Typography>
          </Stack>
        </Box>
      </AuthCard>
    </RedirectIfAuthenticated>
  );
}
