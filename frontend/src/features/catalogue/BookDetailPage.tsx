import { Link as RouterLink, useParams } from 'react-router';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Alert, Box, Breadcrumbs, Button, Chip, Container, Link, Skeleton, Stack, Typography } from '@mui/material';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import DownloadIcon from '@mui/icons-material/Download';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import { ApiError } from '../../services/api/client';
import { authApi, catalogueApi, queryKeys } from '../../services/api/endpoints';
import { usePdfDownload } from '../reader/usePdfDownload';
import type { Me } from '../../types/api';
import { Cover, EmptyState, ErrorAlert } from '../../components/common';
import { useAuth } from '../../contexts/AuthContext';
import { loginPath } from '../../utils/redirect';
import { formatDate } from '../../utils/format';

/** Why a signed-in user cannot read yet, mirroring the backend eligibility rule (docs/SECURITY.md §4). */
function ineligibility(user: Me, isAdmin: boolean): 'unverified' | 'membership' | null {
  if (!user.emailVerified) return 'unverified';
  if (!isAdmin && !user.membership?.valid) return 'membership';
  return null;
}

function membershipText(user: Me): string {
  const m = user.membership;
  if (!m) return 'You do not have a membership yet.';
  if (m.status === 'SUSPENDED') return 'Your membership is suspended.';
  if (m.status === 'CANCELLED') return 'Your membership was cancelled.';
  return 'Your membership has expired.';
}

export default function BookDetailPage() {
  const { id = '' } = useParams();
  const { status, user, isAdmin } = useAuth();
  const book = useQuery({ queryKey: queryKeys.book(id), queryFn: () => catalogueApi.book(id), retry: (n, e) => !(e instanceof ApiError && e.status === 404) && n < 2 });

  const download = usePdfDownload(id);
  const resend = useMutation({ mutationFn: () => authApi.resendVerification(user!.email) });

  if (book.isError) {
    if (book.error instanceof ApiError && book.error.status === 404) {
      return (
        <Container maxWidth="md" sx={{ py: 6, px: 2 }}>
          <EmptyState title="Book not found">
            It may have been removed or is not available yet. <Link component={RouterLink} to="/books">Back to the catalogue</Link>
          </EmptyState>
        </Container>
      );
    }
    return (
      <Container maxWidth="md" sx={{ py: 6, px: 2 }}>
        <ErrorAlert error={book.error} onRetry={() => book.refetch()} />
      </Container>
    );
  }

  const b = book.data;
  const blocked = user ? ineligibility(user, isAdmin) : null;

  return (
    <Container maxWidth="lg" sx={{ py: 4, px: 2 }}>
      <Breadcrumbs sx={{ mb: 3 }}>
        <Link component={RouterLink} to="/books" underline="hover" color="inherit">
          Catalogue
        </Link>
        <Typography color="text.primary" noWrap sx={{ maxWidth: 240 }}>
          {b?.title ?? '…'}
        </Typography>
      </Breadcrumbs>

      <Box sx={{ display: 'grid', gap: { xs: 3, md: 5 }, gridTemplateColumns: { xs: '1fr', sm: '240px 1fr', md: '300px 1fr' } }}>
        <Box sx={{ maxWidth: { xs: 260, sm: 'none' }, mx: { xs: 'auto', sm: 0 }, width: '100%', borderRadius: 2, overflow: 'hidden', border: 1, borderColor: 'divider' }}>
          {b ? <Cover url={b.coverUrl} title={b.title} /> : <Skeleton variant="rectangular" sx={{ aspectRatio: '3 / 4', height: 'auto' }} />}
        </Box>

        <Box sx={{ minWidth: 0 }}>
          {!b ? (
            <>
              <Skeleton width="70%" height={48} />
              <Skeleton width="30%" />
              <Skeleton height={120} />
            </>
          ) : (
            <>
              <Typography variant="h1" sx={{ mb: 1, overflowWrap: 'anywhere' }}>
                {b.title}
              </Typography>
              <Stack direction="row" spacing={1} useFlexGap sx={{ mb: 2, flexWrap: 'wrap' }}>
                {b.category && <Chip label={b.category.name} color="secondary" size="small" />}
                {b.pageCount != null && <Chip label={`${b.pageCount} pages`} size="small" variant="outlined" />}
                {b.expiryDate && <Chip label={`Available until ${formatDate(b.expiryDate)}`} size="small" variant="outlined" />}
              </Stack>
              {b.description && (
                <Typography sx={{ whiteSpace: 'pre-line', mb: 3, maxWidth: '70ch' }}>{b.description}</Typography>
              )}

              {status === 'anonymous' && (
                <Stack spacing={1.5} sx={{ maxWidth: 480 }}>
                  <Button
                    component={RouterLink}
                    to={loginPath(`/read/${b.id}`)}
                    variant="contained"
                    size="large"
                    startIcon={<LockOutlinedIcon />}
                  >
                    Log in to read
                  </Button>
                  <Typography variant="body2" color="text.secondary">
                    Not a member? <Link component={RouterLink} to="/register">Create an account</Link>
                  </Typography>
                </Stack>
              )}

              {user && blocked === 'unverified' && (
                <Alert
                  severity="warning"
                  sx={{ maxWidth: 560 }}
                  action={
                    <Button color="inherit" size="small" disabled={resend.isPending || resend.isSuccess} onClick={() => resend.mutate()}>
                      {resend.isSuccess ? 'Sent' : 'Resend email'}
                    </Button>
                  }
                >
                  Verify your email address to read books. We sent a link to <strong>{user.email}</strong>.
                </Alert>
              )}

              {user && blocked === 'membership' && (
                <Alert severity="info" sx={{ maxWidth: 560 }}>
                  {membershipText(user)} Please contact an administrator to read this book.
                </Alert>
              )}

              {user && !blocked && (
                <Stack spacing={2} sx={{ maxWidth: 480 }}>
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                    <Button component={RouterLink} to={`/read/${b.id}`} variant="contained" size="large" startIcon={<MenuBookIcon />}>
                      Read
                    </Button>
                    <Button
                      variant="outlined"
                      size="large"
                      startIcon={<DownloadIcon />}
                      onClick={() => download.mutate()}
                      disabled={download.isPending}
                    >
                      {download.isPending ? 'Preparing…' : 'Download PDF'}
                    </Button>
                  </Stack>
                  {download.isError && <ErrorAlert error={download.error} />}
                </Stack>
              )}

              <Typography variant="body2" color="text.secondary" sx={{ mt: 4 }}>
                Last updated {formatDate(b.updatedAt)}
              </Typography>
            </>
          )}
        </Box>
      </Box>
    </Container>
  );
}
