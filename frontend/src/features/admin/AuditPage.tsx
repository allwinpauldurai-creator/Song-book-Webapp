import { Fragment, useState } from 'react';
import { Link as RouterLink } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import {
  Box,
  Button,
  Collapse,
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
  TextField,
  Typography,
} from '@mui/material';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import { adminApi, adminKeys } from '../../services/api/adminApi';
import { AUDIT_ACTIONS, type AuditLogEntry } from '../../types/admin';
import { EmptyState } from '../../components/common';
import { AdminError, PageHeader, Pager } from './common';
import { useUrlFilters } from './useUrlFilters';

const ENTITY_TYPES = ['USER', 'MEMBERSHIP', 'CATEGORY', 'BOOK', 'MODIFICATION_REQUEST'];
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** yyyy-mm-dd (local) -> ISO instant at local start of day; `endOfDay` gives the next midnight. */
function dayToInstant(day: string, endOfDay = false): string | undefined {
  if (!day) return undefined;
  const [y, m, d] = day.split('-').map(Number);
  return new Date(y!, m! - 1, d! + (endOfDay ? 1 : 0)).toISOString();
}

function entityLink(e: AuditLogEntry) {
  if (!e.entityId) return null;
  const short = e.entityId.slice(0, 8);
  const to = e.entityType === 'USER' ? `/admin/users/${e.entityId}` : e.entityType === 'BOOK' ? `/admin/books/${e.entityId}` : null;
  return to ? <Link component={RouterLink} to={to}>{short}…</Link> : <span title={e.entityId}>{short}…</span>;
}

function Row({ e }: { e: AuditLogEntry }) {
  const [open, setOpen] = useState(false);
  const hasDetails = !!e.details && Object.keys(e.details).length > 0;
  return (
    <Fragment>
      <TableRow hover>
        <TableCell sx={{ whiteSpace: 'nowrap' }}>{new Date(e.createdAt).toLocaleString()}</TableCell>
        <TableCell sx={{ fontFamily: 'monospace', fontSize: 13 }}>{e.action}</TableCell>
        <TableCell>
          {e.actorUserId ? <Link component={RouterLink} to={`/admin/users/${e.actorUserId}`} title={e.actorUserId}>{e.actorUserId.slice(0, 8)}…</Link> : '—'}
        </TableCell>
        <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>
          {e.entityType ?? '—'} {entityLink(e)}
        </TableCell>
        <TableCell sx={{ display: { xs: 'none', lg: 'table-cell' } }}>{e.ipAddress ?? '—'}</TableCell>
        <TableCell padding="checkbox">
          {hasDetails && (
            <IconButton size="small" aria-label={open ? 'Hide details' : 'Show details'} onClick={() => setOpen((o) => !o)}>
              {open ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
            </IconButton>
          )}
        </TableCell>
      </TableRow>
      {hasDetails && (
        <TableRow>
          <TableCell colSpan={6} sx={{ py: 0, borderBottom: open ? undefined : 'none' }}>
            <Collapse in={open} unmountOnExit>
              <Box component="pre" sx={{ m: 0, py: 1.5, fontSize: 13, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                {JSON.stringify(e.details, null, 2)}
              </Box>
            </Collapse>
          </TableCell>
        </TableRow>
      )}
    </Fragment>
  );
}

export default function AuditPage() {
  const { values, page, size, set } = useUrlFilters(['action', 'entityType', 'entityId', 'actorId', 'from', 'to'] as const, 50);
  const [entityId, setEntityId] = useState(values.entityId);
  const [actorId, setActorId] = useState(values.actorId);

  const validIds = (!values.entityId || UUID_RE.test(values.entityId)) && (!values.actorId || UUID_RE.test(values.actorId));
  const q = {
    action: values.action || undefined,
    entityType: values.entityType || undefined,
    entityId: values.entityId || undefined,
    actorId: values.actorId || undefined,
    from: dayToInstant(values.from),
    to: dayToInstant(values.to, true),
    page,
    size,
  };
  const list = useQuery({ queryKey: adminKeys.audit(q), queryFn: () => adminApi.audit(q), placeholderData: keepPreviousData, enabled: validIds });

  return (
    <>
      <PageHeader title="Audit log" subtitle="Append-only record of administrative and security events. Newest first." />
      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', lg: 'repeat(3, 1fr)' } }}>
          <FormControl size="small">
            <InputLabel id="a-action">Action</InputLabel>
            <Select labelId="a-action" label="Action" value={values.action} onChange={(e) => set({ action: e.target.value })}>
              <MenuItem value="">Any</MenuItem>
              {AUDIT_ACTIONS.map((a) => <MenuItem key={a} value={a}>{a}</MenuItem>)}
            </Select>
          </FormControl>
          <FormControl size="small">
            <InputLabel id="a-type">Entity type</InputLabel>
            <Select labelId="a-type" label="Entity type" value={values.entityType} onChange={(e) => set({ entityType: e.target.value })}>
              <MenuItem value="">Any</MenuItem>
              {ENTITY_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
            </Select>
          </FormControl>
          <TextField size="small" label="Entity ID" value={entityId} onChange={(e) => setEntityId(e.target.value.trim())}
            onBlur={() => set({ entityId })} onKeyDown={(e) => e.key === 'Enter' && set({ entityId })}
            error={!!entityId && !UUID_RE.test(entityId)} helperText={entityId && !UUID_RE.test(entityId) ? 'Must be a full UUID' : ' '} />
          <TextField size="small" label="Actor user ID" value={actorId} onChange={(e) => setActorId(e.target.value.trim())}
            onBlur={() => set({ actorId })} onKeyDown={(e) => e.key === 'Enter' && set({ actorId })}
            error={!!actorId && !UUID_RE.test(actorId)} helperText={actorId && !UUID_RE.test(actorId) ? 'Must be a full UUID' : ' '} />
          <TextField size="small" type="date" label="From" value={values.from} onChange={(e) => set({ from: e.target.value })} slotProps={{ inputLabel: { shrink: true } }} />
          <TextField size="small" type="date" label="To (inclusive)" value={values.to} onChange={(e) => set({ to: e.target.value })} slotProps={{ inputLabel: { shrink: true } }} />
        </Box>
        <Stack direction="row" sx={{ justifyContent: 'flex-end', mt: 1 }}>
          <Button size="small" onClick={() => { setEntityId(''); setActorId(''); set({ action: null, entityType: null, entityId: null, actorId: null, from: null, to: null }); }}>
            Clear filters
          </Button>
        </Stack>
      </Paper>

      {!validIds ? (
        <Typography color="text.secondary">Enter a full ID to filter by entity or actor.</Typography>
      ) : list.isError ? (
        <AdminError error={list.error} onRetry={() => list.refetch()} />
      ) : (
        <Paper variant="outlined">
          <TableContainer>
            <Table size="small" sx={{ opacity: list.isPlaceholderData ? 0.6 : 1 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Time</TableCell>
                  <TableCell>Action</TableCell>
                  <TableCell>Actor</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Entity</TableCell>
                  <TableCell sx={{ display: { xs: 'none', lg: 'table-cell' } }}>IP</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {list.isPending ? <TableRow><TableCell colSpan={6}>Loading…</TableCell></TableRow> : list.data.content.map((e) => <Row key={e.id} e={e} />)}
              </TableBody>
            </Table>
          </TableContainer>
          {list.data && list.data.content.length === 0 && <EmptyState title="No audit entries match these filters" />}
          {list.data && <Pager data={list.data} onPage={(p) => set({ page: p })} onSize={(s) => set({ size: s })} />}
        </Paper>
      )}
    </>
  );
}
