import { useState, type FormEvent } from 'react';
import { Link as RouterLink } from 'react-router';
import { useMutation } from '@tanstack/react-query';
import { Alert, Box, Button, Link, Stack, TextField, Typography } from '@mui/material';
import { ApiError, errorMessage } from '../../services/api/client';
import { useAuth } from '../../contexts/AuthContext';
import { RedirectIfAuthenticated } from '../../routes/guards';
import { AuthCard } from '../../components/common';
import { normalizeMobile } from '../../utils/mobile';
import PasswordField, { passwordProblem } from './PasswordField';
import MobileField from './MobileField';

export default function RegisterPage() {
  const { register: registerAccount } = useAuth();
  const [fullName, setFullName] = useState('');
  const [mobile, setMobile] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [touched, setTouched] = useState(false);

  const e164 = normalizeMobile(mobile);
  // No SMS code: on success the member is signed in and RedirectIfAuthenticated moves on to /me.
  const register = useMutation({
    mutationFn: () =>
      registerAccount({ fullName: fullName.trim(), mobile: e164!, email: email.trim() || null, password }),
  });

  const pwProblem = passwordProblem(password, e164);
  const mismatch = confirm.length > 0 && confirm !== password;
  const serverFields = register.error instanceof ApiError ? register.error.fieldMap() : {};
  const alreadyRegistered = register.error instanceof ApiError && register.error.code === 'MOBILE_ALREADY_REGISTERED';

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (!fullName.trim() || !e164 || pwProblem || confirm !== password) return;
    register.mutate();
  };

  return (
    <RedirectIfAuthenticated to="/me">
      <AuthCard title="Create an account" subtitle="Register to read and download song books.">
        <Box component="form" onSubmit={onSubmit} noValidate>
          <Stack spacing={2}>
            {alreadyRegistered && (
              <Alert severity="info">
                This mobile number is already registered.{' '}
                <Link component={RouterLink} to="/login">
                  Log in
                </Link>{' '}
                or{' '}
                <Link component={RouterLink} to="/forgot-password">
                  reset your password
                </Link>
                .
              </Alert>
            )}
            {register.isError && !alreadyRegistered && !Object.keys(serverFields).length && (
              <Alert severity="error">{errorMessage(register.error)}</Alert>
            )}
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
            <MobileField value={mobile} onChange={setMobile} required showError={touched} serverError={serverFields.mobile} />
            <TextField
              label="Email (optional)"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              error={!!serverFields.email}
              helperText={serverFields.email ?? 'Only used to contact you. You sign in with your mobile number.'}
              slotProps={{ htmlInput: { maxLength: 254 } }}
            />
            <PasswordField
              label="Password"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              error={((touched || password.length > 0) && !!pwProblem) || !!serverFields.password}
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
