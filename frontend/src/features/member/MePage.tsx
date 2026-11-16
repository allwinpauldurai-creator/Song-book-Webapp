import { Link as RouterLink } from 'react-router';
import { useMutation } from '@tanstack/react-query';
import { Alert, Box, Button, Card, CardContent, Chip, Container, Stack, Typography } from '@mui/material';
import { authApi } from '../../services/api/endpoints';
import type { MembershipSummary } from '../../types/api';
import { useAuth } from '../../contexts/AuthContext';
import { formatDate } from '../../utils/format';

function MembershipCard({ membership, isAdmin }: { membership?: MembershipSummary | null; isAdmin: boolean }) {
  return (
    <Card>
      <CardContent>
        <Typography variant="overline" color="text.secondary">
          Membership
        </Typography>
        {membership ? (
          <>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', my: 1 }}>
              <Chip
                label={membership.valid ? 'Active' : membership.status.charAt(0) + membership.status.slice(1).toLowerCase()}
                color={membership.valid ? 'success' : 'warning'}
                size="small"
              />
            </Stack>
            <Typography variant="body2" color="text.secondary">
              From {formatDate(membership.startDate)} {membership.endDate ? `to ${formatDate(membership.endDate)}` : '· no end date'}
            </Typography>
          </>
        ) : (
          <Typography sx={{ mt: 1 }} color="text.secondary">
            {isAdmin ? 'Administrators can read every book without a membership.' : 'No membership yet.'}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

/** Minimal member dashboard; continue-reading and history arrive with the reader phase. */
export default function MePage() {
  const { user, isAdmin } = useAuth();
  const resend = useMutation({ mutationFn: () => authApi.resendVerification(user!.email) });
  if (!user) return null;

  return (
    <Container maxWidth="md" sx={{ py: 4, px: 2 }}>
      <Typography variant="h2" component="h1" sx={{ mb: 3 }}>
        Hello, {user.fullName.split(' ')[0]}
      </Typography>
      <Stack spacing={2}>
        {!user.emailVerified && (
          <Alert
            severity="warning"
            action={
              <Button color="inherit" size="small" disabled={resend.isPending || resend.isSuccess} onClick={() => resend.mutate()}>
                {resend.isSuccess ? 'Sent' : 'Resend email'}
              </Button>
            }
          >
            Verify your email address to start reading.
          </Alert>
        )}
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
          <MembershipCard membership={user.membership} isAdmin={isAdmin} />
          <Card>
            <CardContent>
              <Typography variant="overline" color="text.secondary">
                Account
              </Typography>
              <Typography sx={{ fontWeight: 600, mt: 1 }}>
                {user.fullName}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
                {user.email}
              </Typography>
              <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
                {user.roles.map((r) => (
                  <Chip key={r} label={r.charAt(0) + r.slice(1).toLowerCase()} size="small" variant="outlined" />
                ))}
              </Stack>
            </CardContent>
          </Card>
        </Box>
        <Box>
          <Button component={RouterLink} to="/books" variant="contained">
            Browse the catalogue
          </Button>
        </Box>
      </Stack>
    </Container>
  );
}
