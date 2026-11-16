import type { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { Box, Card, CardActionArea, CardContent, Skeleton, Stack, Typography } from '@mui/material';
import { adminApi, adminKeys } from '../../services/api/adminApi';
import { formatDate } from '../../utils/format';
import { AdminError, BOOK_STATUSES, PageHeader, titleCase } from './common';

function Stat({ label, value, hint, to }: { label: string; value: ReactNode; hint?: ReactNode; to: string }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardActionArea component={RouterLink} to={to} sx={{ height: '100%' }}>
        <CardContent>
          <Typography variant="overline" color="text.secondary">
            {label}
          </Typography>
          <Typography sx={{ fontSize: '2rem', fontWeight: 700, lineHeight: 1.2, fontVariantNumeric: 'tabular-nums' }}>{value}</Typography>
          {hint && (
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              {hint}
            </Typography>
          )}
        </CardContent>
      </CardActionArea>
    </Card>
  );
}

const grid = { display: 'grid', gap: 2, gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(4, minmax(0, 1fr))' } } as const;

export default function DashboardPage() {
  const stats = useQuery({ queryKey: adminKeys.stats, queryFn: adminApi.stats, staleTime: 0 });

  return (
    <>
      <PageHeader title="Admin dashboard" subtitle={stats.data && `Counts as of ${formatDate(stats.data.asOf)}`} />
      {stats.isError ? (
        <AdminError error={stats.error} onRetry={() => stats.refetch()} />
      ) : stats.isPending ? (
        <Box sx={grid}>
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} variant="rounded" height={120} />
          ))}
        </Box>
      ) : (
        <Stack spacing={3}>
          <Box sx={grid}>
            <Stat
              label="Users"
              value={stats.data.users.total}
              hint={`${stats.data.users.active} active · ${stats.data.users.unverified} unverified`}
              to="/admin/users"
            />
            <Stat label="Valid memberships" value={stats.data.membershipsValidToday} hint="Active and in date range today" to="/admin/memberships?status=ACTIVE" />
            <Stat
              label="Published books"
              value={stats.data.booksByStatus.PUBLISHED}
              hint={`${Object.values(stats.data.booksByStatus).reduce((a, b) => a + b, 0)} books in total`}
              to="/admin/books?status=PUBLISHED"
            />
            <Stat label="Pending requests" value={stats.data.pendingRequests} hint="Waiting for review" to="/admin/requests?status=PENDING" />
          </Box>

          <Box>
            <Typography variant="h3" component="h2" sx={{ mb: 1.5 }}>
              Books by status
            </Typography>
            <Box sx={grid}>
              {BOOK_STATUSES.map((s) => (
                <Stat key={s} label={titleCase(s)} value={stats.data.booksByStatus[s]} to={`/admin/books?status=${s}`} />
              ))}
            </Box>
          </Box>

          <Box>
            <Typography variant="h3" component="h2" sx={{ mb: 1.5 }}>
              Accounts
            </Typography>
            <Box sx={grid}>
              <Stat label="Active" value={stats.data.users.active} to="/admin/users?status=ACTIVE" />
              <Stat label="Disabled" value={stats.data.users.disabled} to="/admin/users?status=DISABLED" />
              <Stat label="Locked" value={stats.data.users.locked} to="/admin/users?status=LOCKED" />
              <Stat label="Active admins" value={stats.data.users.activeAdmins} to="/admin/users?role=ADMIN" />
            </Box>
          </Box>
        </Stack>
      )}
    </>
  );
}
