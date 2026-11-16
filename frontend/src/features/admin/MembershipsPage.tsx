import { useState } from 'react';
import { Link as RouterLink } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import {
  Box,
  Button,
  FormControl,
  IconButton,
  InputLabel,
  Link,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import { adminApi, adminKeys } from '../../services/api/adminApi';
import type { AdminMembership } from '../../types/admin';
import { EmptyState } from '../../components/common';
import { formatDate } from '../../utils/format';
import { AdminError, MembershipChip, MEMBERSHIP_STATUSES, PageHeader, Pager, titleCase, UserPicker, type UserOption } from './common';
import MembershipDialog from './MembershipDialog';
import { useUrlFilters } from './useUrlFilters';

export default function MembershipsPage() {
  const { values, page, size, set } = useUrlFilters(['status', 'userId'] as const);
  const [userFilter, setUserFilter] = useState<UserOption | null>(null);
  const [dialog, setDialog] = useState<{ open: boolean; membership: AdminMembership | null }>({ open: false, membership: null });

  const q = { status: values.status as never, userId: values.userId || undefined, page, size, sort: 'startDate,desc' };
  const list = useQuery({ queryKey: adminKeys.memberships(q), queryFn: () => adminApi.memberships(q), placeholderData: keepPreviousData });

  return (
    <>
      <PageHeader
        title="Memberships"
        subtitle="Each user can have many records over time, but at most one active."
        actions={<Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialog({ open: true, membership: null })}>New membership</Button>}
      />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <Box sx={{ minWidth: 280 }}>
          <UserPicker
            label="Filter by user"
            value={userFilter}
            onChange={(u) => {
              setUserFilter(u);
              set({ userId: u?.id ?? null });
            }}
          />
        </Box>
        <FormControl sx={{ minWidth: 160 }}>
          <InputLabel id="m-status">Status</InputLabel>
          <Select labelId="m-status" label="Status" value={values.status} onChange={(e) => set({ status: e.target.value })}>
            <MenuItem value="">Any</MenuItem>
            {MEMBERSHIP_STATUSES.map((s) => <MenuItem key={s} value={s}>{titleCase(s)}</MenuItem>)}
          </Select>
        </FormControl>
      </Stack>

      {list.isError ? (
        <AdminError error={list.error} onRetry={() => list.refetch()} />
      ) : (
        <Paper variant="outlined">
          <TableContainer>
            <Table size="small" sx={{ opacity: list.isPlaceholderData ? 0.6 : 1 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Member</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>From</TableCell>
                  <TableCell>To</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Notes</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {list.isPending
                  ? <TableRow><TableCell colSpan={6}>Loading…</TableCell></TableRow>
                  : list.data.content.map((m) => (
                      <TableRow key={m.id} hover>
                        <TableCell>
                          <Link component={RouterLink} to={`/admin/users/${m.userId}`} sx={{ fontWeight: 600 }}>{m.userFullName}</Link>
                          <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{m.userEmail}</Typography>
                        </TableCell>
                        <TableCell><MembershipChip status={m.status} valid={m.validToday} /></TableCell>
                        <TableCell>{formatDate(m.startDate)}</TableCell>
                        <TableCell>{m.endDate ? formatDate(m.endDate) : 'No end date'}</TableCell>
                        <TableCell sx={{ display: { xs: 'none', md: 'table-cell' }, maxWidth: 260 }}>
                          <Typography variant="body2" noWrap title={m.notes ?? ''}>{m.notes}</Typography>
                        </TableCell>
                        <TableCell align="right">
                          <Tooltip title="Edit">
                            <IconButton size="small" aria-label="Edit membership" onClick={() => setDialog({ open: true, membership: m })}>
                              <EditIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        </TableCell>
                      </TableRow>
                    ))}
              </TableBody>
            </Table>
          </TableContainer>
          {list.data && list.data.content.length === 0 && <EmptyState title="No memberships match these filters" />}
          {list.data && <Pager data={list.data} onPage={(p) => set({ page: p })} onSize={(s) => set({ size: s })} />}
        </Paper>
      )}
      <MembershipDialog open={dialog.open} membership={dialog.membership} onClose={() => setDialog({ open: false, membership: null })} />
    </>
  );
}
