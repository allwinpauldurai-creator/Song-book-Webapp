import { useState, type FormEvent } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router';
import { useMutation } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Breadcrumbs,
  Button,
  Card,
  CardContent,
  Chip,
  Container,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Link,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { ApiError, errorMessage } from '../../services/api/client';
import { usersApi } from '../../services/api/endpoints';
import { useAuth } from '../../contexts/AuthContext';
import { formatMobile, normalizeMobile } from '../../utils/mobile';
import PasswordField, { passwordProblem } from '../auth/PasswordField';
import MobileField from '../auth/MobileField';
import CodeField, { isCompleteCode } from '../auth/CodeField';
import { useResendCountdown } from '../auth/useResendCountdown';

/** Sent to /login after a password change, which ends every session (docs/SECURITY.md §2). */
export const PASSWORD_CHANGED_NOTICE = 'Your password was changed. Please log in again with your new password.';

function DetailsForm() {
  const { user, reloadUser } = useAuth();
  const [fullName, setFullName] = useState(user?.fullName ?? '');
  const [email, setEmail] = useState(user?.email ?? '');
  const save = useMutation({
    mutationFn: () => usersApi.updateProfile(fullName.trim(), email.trim() || null),
    onSuccess: () => reloadUser(),
  });
  const unchanged = fullName.trim() === user?.fullName && email.trim() === (user?.email ?? '');
  const fields = save.error instanceof ApiError ? save.error.fieldMap() : {};
  const emailInUse = save.error instanceof ApiError && save.error.code === 'EMAIL_IN_USE';

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
            {save.isSuccess && unchanged && <Alert severity="success">Your details were updated.</Alert>}
            {save.isError && !Object.keys(fields).length && !emailInUse && <Alert severity="error">{errorMessage(save.error)}</Alert>}
            <TextField
              label="Full name"
              autoComplete="name"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              required
              error={!fullName.trim() || !!fields.fullName}
              helperText={fields.fullName ?? (!fullName.trim() ? 'Enter your name.' : ' ')}
              slotProps={{ htmlInput: { maxLength: 150 } }}
            />
            <TextField
              label="Email (optional)"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              error={!!fields.email || emailInUse}
              helperText={
                fields.email ?? (emailInUse ? 'Another account already uses this email address.' : 'Only used to contact you.')
              }
              slotProps={{ htmlInput: { maxLength: 254 } }}
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

const MOBILE_ERRORS: Record<string, string> = {
  INVALID_CURRENT_PASSWORD: 'Current password is incorrect.',
  MOBILE_IN_USE: 'Another account already uses this mobile number.',
  SAME_MOBILE: 'This is already your mobile number.',
  INVALID_CODE: 'The code is wrong or has expired. Request a new code.',
  INVALID_MOBILE: 'Enter a valid mobile number.',
};

/** Two steps: new number + current password -> SMS code to the new number -> confirm. */
function ChangeMobileDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { reloadUser } = useAuth();
  const [mobile, setMobile] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [touched, setTouched] = useState(false);
  const [codeSent, setCodeSent] = useState(false);
  const { secondsLeft, canResend, restart } = useResendCountdown();
  const e164 = normalizeMobile(mobile);

  const request = useMutation({
    mutationFn: () => usersApi.requestMobileChange(e164!, password),
    onSuccess: () => {
      setCodeSent(true);
      restart();
    },
  });
  const confirm = useMutation({
    mutationFn: () => usersApi.confirmMobileChange(code),
    onSuccess: async () => {
      await reloadUser().catch(() => undefined);
      close();
    },
  });

  function close() {
    setMobile('');
    setPassword('');
    setCode('');
    setTouched(false);
    setCodeSent(false);
    request.reset();
    confirm.reset();
    onClose();
  }

  const error = (codeSent ? confirm.error : null) ?? request.error;
  const errorCode = error instanceof ApiError ? error.code : null;

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (!codeSent) {
      if (e164 && password) request.mutate();
    } else if (isCompleteCode(code)) {
      confirm.mutate();
    }
  };

  return (
    <Dialog open={open} onClose={close} fullWidth maxWidth="xs">
      <Box component="form" onSubmit={onSubmit} noValidate>
        <DialogTitle>Change mobile number</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {error && <Alert severity="error">{(errorCode && MOBILE_ERRORS[errorCode]) || errorMessage(error)}</Alert>}
            {!codeSent ? (
              <>
                <MobileField label="New mobile number" value={mobile} onChange={setMobile} required autoFocus showError={touched} />
                <PasswordField
                  label="Current password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  error={touched && !password}
                  helperText="Confirms it is you."
                />
              </>
            ) : (
              <>
                <Typography>
                  We sent a 6-digit code to <strong>{formatMobile(e164)}</strong>. Your current number keeps working until
                  you confirm.
                </Typography>
                <CodeField
                  value={code}
                  onChange={setCode}
                  required
                  autoFocus
                  error={touched && !isCompleteCode(code)}
                  helperText={touched && !isCompleteCode(code) ? 'Enter the 6 digits from the SMS.' : ' '}
                />
                <Box>
                  <Button size="small" onClick={() => request.mutate()} disabled={!canResend || request.isPending}>
                    {canResend ? 'Resend code' : `Resend code in ${secondsLeft}s`}
                  </Button>
                </Box>
              </>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={close}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={request.isPending || confirm.isPending}>
            {!codeSent ? (request.isPending ? 'Sending…' : 'Send code') : confirm.isPending ? 'Checking…' : 'Confirm'}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}

function MobileCard() {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  return (
    <Card>
      <CardContent>
        <Typography variant="h3" component="h2" sx={{ mb: 1 }}>
          Mobile number
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          You sign in with this number, and SMS codes are sent to it.
        </Typography>
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
          <Typography sx={{ fontWeight: 600 }}>{user?.mobile ? formatMobile(user.mobile) : 'No mobile number'}</Typography>
          {user?.mobileVerified && <Chip size="small" variant="outlined" color="success" label="Verified" />}
          <Box sx={{ flexGrow: 1 }} />
          <Button variant="outlined" onClick={() => setOpen(true)}>
            {user?.mobile ? 'Change' : 'Add'}
          </Button>
        </Stack>
      </CardContent>
      <ChangeMobileDialog open={open} onClose={() => setOpen(false)} />
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

  const problem = next ? passwordProblem(next, user?.mobile) : null;
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
        <DetailsForm />
        <MobileCard />
        <PasswordForm />
      </Stack>
    </Container>
  );
}
