import { useEffect, useState, type FormEvent } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  FormGroup,
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
} from '@mui/material';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import { ApiError } from '../../services/api/client';
import { adminApi, adminKeys } from '../../services/api/adminApi';
import type { Role } from '../../types/api';
import { EmptyState } from '../../components/common';
import { formatDate } from '../../utils/format';
import PasswordField, { passwordProblem } from '../auth/PasswordField';
import { AccountChip, ACCOUNT_STATUSES, AdminError, adminErrorMessage, PageHeader, Pager, titleCase } from './common';
import { useUrlFilters } from './useUrlFilters';

function CreateUserDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [admin, setAdmin] = useState(false);
  const [verified, setVerified] = useState(true);

  useEffect(() => {
    if (open) {
      setFullName('');
      setEmail('');
      setPassword('');
      setAdmin(false);
      setVerified(true);
    }
  }, [open]);

  const create = useMutation({
    mutationFn: () =>
      adminApi.createUser({
        fullName: fullName.trim(),
        email: email.trim(),
        password,
        roles: admin ? ['MEMBER', 'ADMIN'] : ['MEMBER'],
        emailVerified: verified,
      }),
    onSuccess: (u) => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.all });
      onClose();
      navigate(`/admin/users/${u.id}`);
    },
  });
  const fields = create.error instanceof ApiError ? create.error.fieldMap() : {};
  const pwProblem = password ? passwordProblem(password, email) : null;
  const canSave = fullName.trim() && email.trim() && password && !pwProblem && !create.isPending;

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (canSave) create.mutate();
  };

  return (
    <Dialog open={open} onClose={create.isPending ? undefined : onClose} fullWidth maxWidth="sm" component="form" onSubmit={onSubmit}>
      <DialogTitle>New user</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {create.isError && !Object.keys(fields).length && <Alert severity="error">{adminErrorMessage(create.error)}</Alert>}
          <TextField label="Full name" value={fullName} onChange={(e) => setFullName(e.target.value)} required autoFocus
            error={!!fields.fullName} helperText={fields.fullName ?? ' '} slotProps={{ htmlInput: { maxLength: 150 } }} />
          <TextField label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required
            error={!!fields.email} helperText={fields.email ?? ' '} slotProps={{ htmlInput: { maxLength: 254 } }} />
          <PasswordField label="Initial password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} required
            error={!!pwProblem || !!fields.password} helperText={fields.password ?? pwProblem ?? 'Share it securely; the user can change it in their profile.'} />
          <FormGroup>
            <FormControlLabel control={<Checkbox checked={verified} onChange={(e) => setVerified(e.target.checked)} />} label="Email already verified (you vouch for the address)" />
            <FormControlLabel control={<Checkbox checked={admin} onChange={(e) => setAdmin(e.target.checked)} />} label="Administrator" />
          </FormGroup>
          {verified && <Alert severity="info">Admin-created users get no membership automatically. Add one on the user page.</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={create.isPending}>Cancel</Button>
        <Button type="submit" variant="contained" disabled={!canSave}>{create.isPending ? 'Creating…' : 'Create user'}</Button>
      </DialogActions>
    </Dialog>
  );
}

export default function UsersPage() {
  const { values, page, size, set } = useUrlFilters(['q', 'status', 'role'] as const);
  const [search, setSearch] = useState(values.q);
  const [creating, setCreating] = useState(false);
  useEffect(() => setSearch(values.q), [values.q]);
  useEffect(() => {
    if (search.trim() === values.q) return;
    const t = setTimeout(() => set({ q: search.trim() || null }), 350);
    return () => clearTimeout(t);
    // Intentionally keyed on `search` only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search]);

  const q = { q: values.q || undefined, status: values.status as never, role: values.role as never, page, size, sort: 'createdAt,desc' };
  const users = useQuery({ queryKey: adminKeys.users(q), queryFn: () => adminApi.users(q), placeholderData: keepPreviousData });

  return (
    <>
      <PageHeader
        title="Users"
        actions={<Button variant="contained" startIcon={<PersonAddIcon />} onClick={() => setCreating(true)}>New user</Button>}
      />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <TextField size="small" type="search" label="Search name or email" value={search} onChange={(e) => setSearch(e.target.value)} />
        <FormControl size="small" sx={{ minWidth: 150 }}>
          <InputLabel id="u-status">Status</InputLabel>
          <Select labelId="u-status" label="Status" value={values.status} onChange={(e) => set({ status: e.target.value })}>
            <MenuItem value="">Any</MenuItem>
            {ACCOUNT_STATUSES.map((s) => <MenuItem key={s} value={s}>{titleCase(s)}</MenuItem>)}
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: 150 }}>
          <InputLabel id="u-role">Role</InputLabel>
          <Select labelId="u-role" label="Role" value={values.role} onChange={(e) => set({ role: e.target.value })}>
            <MenuItem value="">Any</MenuItem>
            <MenuItem value="MEMBER">Member</MenuItem>
            <MenuItem value="ADMIN">Admin</MenuItem>
          </Select>
        </FormControl>
      </Stack>

      {users.isError ? (
        <AdminError error={users.error} onRetry={() => users.refetch()} />
      ) : (
        <Paper variant="outlined">
          <TableContainer>
            <Table size="small" sx={{ opacity: users.isPlaceholderData ? 0.6 : 1 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Email</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Roles</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Created</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {users.isPending
                  ? <TableRow><TableCell colSpan={5}>Loading…</TableCell></TableRow>
                  : users.data.content.map((u) => (
                      <TableRow key={u.id} hover>
                        <TableCell>
                          <Link component={RouterLink} to={`/admin/users/${u.id}`} sx={{ fontWeight: 600 }}>{u.fullName}</Link>
                        </TableCell>
                        <TableCell sx={{ overflowWrap: 'anywhere' }}>
                          {u.email}
                          {!u.emailVerified && <Chip size="small" label="Unverified" variant="outlined" color="warning" sx={{ ml: 1 }} />}
                        </TableCell>
                        <TableCell><AccountChip status={u.accountStatus} /></TableCell>
                        <TableCell>{u.roles.map((r: Role) => titleCase(r)).join(', ')}</TableCell>
                        <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{formatDate(u.createdAt)}</TableCell>
                      </TableRow>
                    ))}
              </TableBody>
            </Table>
          </TableContainer>
          {users.data && users.data.content.length === 0 && <EmptyState title="No users match these filters" />}
          {users.data && <Pager data={users.data} onPage={(p) => set({ page: p })} onSize={(s) => set({ size: s })} />}
        </Paper>
      )}
      <CreateUserDialog open={creating} onClose={() => setCreating(false)} />
    </>
  );
}
