import { useState } from 'react';
import { Link as RouterLink } from 'react-router';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Link,
  Paper,
  Skeleton,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import { adminApi, adminKeys } from '../../services/api/adminApi';
import type { ModificationRequest, RequestStatus } from '../../types/api';
import { EmptyState } from '../../components/common';
import { formatDate } from '../../utils/format';
import { AdminError, adminErrorMessage, PageHeader, Pager, RequestChip, titleCase } from './common';
import { useUrlFilters } from './useUrlFilters';

const TABS: (RequestStatus | '')[] = ['PENDING', 'APPROVED', 'REJECTED', ''];

function ReviewDialog({ request, decision, onClose }: { request: ModificationRequest | null; decision: 'APPROVED' | 'REJECTED'; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [note, setNote] = useState('');
  const review = useMutation({
    mutationFn: () => adminApi.reviewRequest(request!.id, { status: decision, reviewNote: note.trim() || null, version: request!.version }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'requests'] });
      void queryClient.invalidateQueries({ queryKey: adminKeys.stats });
      setNote('');
      onClose();
    },
  });
  return (
    <Dialog open={!!request} onClose={review.isPending ? undefined : onClose} fullWidth maxWidth="sm">
      <DialogTitle>{decision === 'APPROVED' ? 'Approve request' : 'Reject request'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {review.isError && <Alert severity="error">{adminErrorMessage(review.error)}</Alert>}
          <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-line' }}>{request?.description}</Typography>
          <TextField
            label="Note to the member (optional)"
            multiline
            minRows={3}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            helperText={`Shown on the member's "My requests" page. ${note.length}/1000`}
            slotProps={{ htmlInput: { maxLength: 1000 } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={review.isPending}>Cancel</Button>
        <Button variant="contained" color={decision === 'APPROVED' ? 'success' : 'error'} disabled={review.isPending} onClick={() => review.mutate()}>
          {review.isPending ? 'Saving…' : decision === 'APPROVED' ? 'Approve' : 'Reject'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default function RequestsReviewPage() {
  const { values, page, size, set } = useUrlFilters(['status'] as const, 10);
  // Default tab is the review queue (PENDING); "any" is stored as status=ALL.
  const status = (values.status === 'ALL' ? '' : values.status || 'PENDING') as RequestStatus | '';
  const [reviewing, setReviewing] = useState<{ request: ModificationRequest | null; decision: 'APPROVED' | 'REJECTED' }>({ request: null, decision: 'APPROVED' });

  const q = { status, page, size, sort: 'createdAt,desc' };
  const list = useQuery({ queryKey: adminKeys.requests(q), queryFn: () => adminApi.requests(q), placeholderData: keepPreviousData });

  return (
    <>
      <PageHeader title="Modification requests" subtitle="Corrections and additions suggested by members." />
      <Tabs value={TABS.indexOf(status)} onChange={(_, i) => set({ status: TABS[i] || 'ALL' })} sx={{ mb: 2 }} variant="scrollable" allowScrollButtonsMobile>
        {TABS.map((t) => <Tab key={t || 'all'} label={t ? titleCase(t) : 'All'} />)}
      </Tabs>
      {list.isError ? (
        <AdminError error={list.error} onRetry={() => list.refetch()} />
      ) : list.isPending ? (
        <Skeleton variant="rounded" height={160} />
      ) : list.data.content.length === 0 ? (
        <EmptyState title={status === 'PENDING' ? 'Nothing waiting for review' : 'No requests'} />
      ) : (
        <Paper variant="outlined" sx={{ p: { xs: 1, sm: 2 } }}>
          <Stack spacing={2} sx={{ opacity: list.isPlaceholderData ? 0.6 : 1 }}>
            {list.data.content.map((r) => (
              <Card key={r.id} variant="outlined">
                <CardContent>
                  <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap', mb: 1 }}>
                    <RequestChip status={r.status} />
                    <Chip size="small" variant="outlined" label={titleCase(r.requestType)} />
                    {r.bookId && r.bookTitle ? (
                      <Link component={RouterLink} to={`/admin/books/${r.bookId}`} sx={{ fontWeight: 600 }}>{r.bookTitle}</Link>
                    ) : (
                      <Typography variant="body2" color="text.secondary">General request</Typography>
                    )}
                    <Typography variant="body2" color="text.secondary" sx={{ ml: 'auto' }}>
                      <Link component={RouterLink} to={`/admin/users/${r.requestedBy}`}>{r.requesterName}</Link> · {formatDate(r.createdAt)}
                    </Typography>
                  </Stack>
                  <Typography sx={{ whiteSpace: 'pre-line', overflowWrap: 'anywhere' }}>{r.description}</Typography>
                  {r.reviewNote && <Alert severity="info" sx={{ mt: 1.5 }}><strong>Note:</strong> {r.reviewNote}</Alert>}
                  {r.status === 'PENDING' && (
                    <Stack direction="row" spacing={1} sx={{ mt: 2 }}>
                      <Button variant="contained" color="success" size="small" onClick={() => setReviewing({ request: r, decision: 'APPROVED' })}>Approve</Button>
                      <Button variant="outlined" color="error" size="small" onClick={() => setReviewing({ request: r, decision: 'REJECTED' })}>Reject</Button>
                    </Stack>
                  )}
                </CardContent>
              </Card>
            ))}
          </Stack>
          <Pager data={list.data} onPage={(p) => set({ page: p })} onSize={(s) => set({ size: s })} />
        </Paper>
      )}
      <ReviewDialog request={reviewing.request} decision={reviewing.decision} onClose={() => setReviewing((s) => ({ ...s, request: null }))} />
    </>
  );
}
