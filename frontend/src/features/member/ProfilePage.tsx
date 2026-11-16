import { useState, type FormEvent } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router';
import { useMutation } from '@tanstack/react-query';
import { Alert, Box, Breadcrumbs, Button, Card, CardContent, Container, Link, Stack, TextField, Typography } from '@mui/material';
import { ApiError, errorMessage } from '../../services/api/client';
import { usersApi } from '../../services/api/endpoints';
import { useAuth } from '../../contexts/AuthContext';
import PasswordField, { passwordProblem } from '../auth/PasswordField';

/** Sent to /login after a password change, which ends every session (docs/SECURITY.md §2). */
export const PASSWORD_CHANGED_NOTICE = 'Your password was changed. Please log in again with your new password.';

function NameForm() {
  const { user, reloadUser } = useAuth();
  const [fullName, setFullName] = useState(user?.fullName ?? '');
  const save = useMutation({
    mutationFn: () => usersApi.updateProfile(fullName.trim()),
    onSuccess: () => reloadUser(),
  });
  const unchanged = fullName.trim() === user?.fullName;
  const fieldError = save.error instanceof ApiError ? save.error.fieldMap().fullName : undefined;

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (fullName.trim() && !unchanged) save.mutate();
  };

  return (
    <Card>
      <CardContent>
        <Typography variant="h3" component="h2" sx={{ mb: 2 }}>
          Your details
        </Typography>
        <Box component="form" onSubmit={onSubmit} noValidate>
          <Stack spacing={2}>
            {save.isSuccess && unchanged && <Alert severity="success">Your name was updated.</Alert>}
            {save.isError && !fieldError && <Alert severity="error">{errorMessage(save.error)}</Alert>}
            <TextField
              label="Full name"
              autoComplete="name"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              required
              error={!fullName.trim() || !!fieldError}
              helperText={fieldError ?? (!fullName.trim() ? 'Enter your name.' : ' ')}
              slotProps={{ htmlInput: { maxLength: 150 } }}
            />
            <TextField
              label="Email"
              value={user?.email ?? ''}
              helperText="Email changes are not supported yet. Contact an administrator if you need one."
              // Read-only rather than disabled: stays focusable for screen readers and keeps readable contrast.
              slotProps={{ htmlInput: { readOnly: true } }}
            />
            <Box>
              <Button type="submit" variant="contained" disabled={save.isPending || unchanged || !fullName.trim()}>
                {save.isPending ? 'Saving…' : 'Save'}
              </Button>
            </Box>
          </Stack>
        </Box>
      </CardContent>
    </Card>
  );
}

function PasswordForm() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');

  const change = useMutation({
    mutationFn: () => usersApi.changePassword(current, next),
    onSuccess: async () => {
      // The backend revoked all sessions, including this one: sign out cleanly now.
      await logout().catch(() => undefined);
      navigate('/login', { replace: true, state: { notice: PASSWORD_CHANGED_NOTICE } });
    },
  });

  const problem = next ? passwordProblem(next, user?.email) : null;
  const mismatch = confirm.length > 0 && confirm !== next;
  const code = change.error instanceof ApiError ? change.error.code : null;
  const wrongCurrent = code === 'INVALID_CURRENT_PASSWORD';
  const serverNewPassword = code === 'WEAK_PASSWORD' ? errorMessage(change.error) : change.error instanceof ApiError ? change.error.fieldMap().newPassword : undefined;
  const canSubmit = current && next && !problem && confirm === next && !change.isPending;

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (canSubmit) change.mutate();
  };

  return (
    <Card>
      <CardContent>
        <Typography variant="h3" component="h2" sx={{ mb: 1 }}>
          Change password
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          You will be signed out on all devices and asked to log in again.
        </Typography>
        <Box component="form" onSubmit={onSubmit} noValidate>
          <Stack spacing={2}>
            {change.isError && !wrongCurrent && !serverNewPassword && <Alert severity="error">{errorMessage(change.error)}</Alert>}
            <PasswordField
              label="Current password"
              autoComplete="current-password"
              value={current}
              onChange={(e) => setCurrent(e.target.value)}
              required
              error={wrongCurrent}
              helperText={wrongCurrent ? 'Current password is incorrect.' : ' '}
            />
            <PasswordField
              label="New password"
              autoComplete="new-password"
              value={next}
              onChange={(e) => setNext(e.target.value)}
              required
              error={!!problem || !!serverNewPassword}
              helperText={serverNewPassword ?? problem ?? 'At least 10 characters.'}
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
            <Box>
              <Button type="submit" variant="contained" disabled={!canSubmit}>
                {change.isPending ? 'Changing…' : 'Change password'}
              </Button>
            </Box>
          </Stack>
        </Box>
      </CardContent>
    </Card>
  );
}

export default function ProfilePage() {
  return (
    <Container maxWidth="sm" sx={{ py: 4, px: 2 }}>
      <Breadcrumbs sx={{ mb: 2 }}>
        <Link component={RouterLink} to="/me" underline="hover" color="inherit">
          Dashboard
        </Link>
        <Typography color="text.primary">Profile</Typography>
      </Breadcrumbs>
      <Typography variant="h2" component="h1" sx={{ mb: 3 }}>
        Profile
      </Typography>
      <Stack spacing={3}>
        <NameForm />
        <PasswordForm />
      </Stack>
    </Container>
  );
}
