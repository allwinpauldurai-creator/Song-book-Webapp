import { Link as RouterLink, useNavigate } from 'react-router';
import { useMutation } from '@tanstack/react-query';
import { Alert, Button, type SxProps, type Theme } from '@mui/material';
import { authApi } from '../../services/api/endpoints';
import { errorMessage } from '../../services/api/client';
import { formatMobile } from '../../utils/mobile';
import type { Me } from '../../types/api';

/**
 * Shown to a signed-in user whose mobile number is not verified (e.g. an admin changed it without vouching
 * for it). Sends a code and opens the code screen. Accounts without any mobile are sent to the profile page.
 */
export default function VerifyMobileAlert({ user, reason, sx }: { user: Me; reason: string; sx?: SxProps<Theme> }) {
  const navigate = useNavigate();
  const send = useMutation({
    mutationFn: () => authApi.resendVerification(user.mobile!),
    onSuccess: () => navigate('/verify-mobile', { state: { mobile: user.mobile, sent: true } }),
  });

  if (!user.mobile) {
    return (
      <Alert
        severity="warning"
        sx={sx}
        action={
          <Button color="inherit" size="small" component={RouterLink} to="/me/profile">
            Add number
          </Button>
        }
      >
        Add your mobile number in your profile {reason}.
      </Alert>
    );
  }
  return (
    <Alert
      severity="warning"
      sx={sx}
      action={
        <Button color="inherit" size="small" disabled={send.isPending} onClick={() => send.mutate()}>
          Send code
        </Button>
      }
    >
      {send.isError ? errorMessage(send.error) : <>Confirm your mobile number <strong>{formatMobile(user.mobile)}</strong> {reason}.</>}
    </Alert>
  );
}
