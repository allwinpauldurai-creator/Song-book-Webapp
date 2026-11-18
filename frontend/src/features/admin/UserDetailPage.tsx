import { useEffect, useState, type FormEvent } from 'react';
import { Link as RouterLink, useParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Breadcrumbs,
  Button,
  Card,
  CardContent,
  Checkbox,
  FormControl,
  FormControlLabel,
  FormGroup,
  FormHelperText,
  FormLabel,
  IconButton,
  InputLabel,
  Link,
  MenuItem,
  Select,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import AddIcon from '@mui/icons-material/Add';
import { ApiError } from '../../services/api/client';
import { adminApi, adminKeys } from '../../services/api/adminApi';
import type { Role } from '../../types/api';
import type { AccountStatus, AdminMembership, AdminUserDetail } from '../../types/admin';
import { useAuth } from '../../contexts/AuthContext';
import { formatDate } from '../../utils/format';
import { formatMobile, normalizeMobile } from '../../utils/mobile';
import MobileField from '../auth/MobileField';
import { AccountChip, ACCOUNT_STATUSES, AdminError, adminErrorMessage, MembershipChip, PageHeader, titleCase } from './common';
import MembershipDialog from './MembershipDialog';

function AccountForm({ user }: { user: AdminUserDetail }) {
  const queryClient = useQueryClient();
  const { user: me, reloadUser } = useAuth();
  const [fullName, setFullName] = useState(user.fullName);
  const [mobile, setMobile] = useState(user.mobile ?? '');
  const [mobileVerified, setMobileVerified] = useState(user.mobileVerified);
  const [email, setEmail] = useState(user.email ?? '');
  const [status, setStatus] = useState<AccountStatus>(user.accountStatus);
  const [isAdmin, setIsAdmin] = useState(user.roles.includes('ADMIN'));
  const [isPublisher, setIsPublisher] = useState(user.roles.includes('PUBLISHER'));
  useEffect(() => {
    setFullName(user.fullName);
    setMobile(user.mobile ?? '');
    setMobileVerified(user.mobileVerified);
    setEmail(user.email ?? '');
    setStatus(user.accountStatus);
    setIsAdmin(user.roles.includes('ADMIN'));
    setIsPublisher(user.roles.includes('PUBLISHER'));
  }, [user]);
  const e164 = normalizeMobile(mobile);

  const save = useMutation({
    mutationFn: () => {
      const roles: Role[] = ['MEMBER'];
      if (isPublisher) roles.push('PUBLISHER');
      if (isAdmin) roles.push('ADMIN');
      return adminApi.updateUser(user.id, {
        fullName: fullName.trim(),
        mobile: e164!,
        mobileVerified,
        email: email.trim() || null,
        accountStatus: status,
        roles,
        version: user.version,
      });
    },
    onSuccess: (updated) => {
      queryClient.setQueryData(adminKeys.user(user.id), updated);
      void queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
      void queryClient.invalidateQueries({ queryKey: adminKeys.stats });
      if (updated.id === me?.id) void reloadUser().catch(() => undefined);
    },
  });
  const fields = save.error instanceof ApiError ? save.error.fieldMap() : {};
  const dirty =
    fullName.trim() !== user.fullName ||
    e164 !== (user.mobile ?? null) ||
    mobileVerified !== user.mobileVerified ||
    email.trim() !== (user.email ?? '') ||
    status !== user.accountStatus ||
    isAdmin !== user.roles.includes('ADMIN') ||
    isPublisher !== user.roles.includes('PUBLISHER');
  const self = user.id === me?.id;
  const valid = !!fullName.trim() && !!e164;

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (dirty && valid) save.mutate();
  };

  return (
    <Card>
      <CardContent>
        <Typography variant="h3" component="h2" sx={{ mb: 2 }}>Account</Typography>
        <Box component="form" onSubmit={onSubmit} noValidate>
          <Stack spacing={2}>
            {save.isError && !Object.keys(fields).length && <Alert severity="error">{adminErrorMessage(save.error)}</Alert>}
            {save.isSuccess && !dirty && <Alert severity="success">Saved.</Alert>}
            <TextField label="Full name" value={fullName} onChange={(e) => setFullName(e.target.value)} required
              error={!fullName.trim() || !!fields.fullName} helperText={fields.fullName ?? ' '} slotProps={{ htmlInput: { maxLength: 150 } }} />
            <MobileField value={mobile} onChange={setMobile} required showError serverError={fields.mobile} />
            <FormControlLabel
              control={<Checkbox checked={mobileVerified} onChange={(e) => setMobileVerified(e.target.checked)} />}
              label="Mobile verified (you vouch for it; otherwise the user confirms it by SMS)"
              sx={{ mt: -1 }}
            />
            <TextField label="Email (optional)" type="email" value={email} onChange={(e) => setEmail(e.target.value)}
              error={!!fields.email} helperText={fields.email ?? 'Contact only.'} slotProps={{ htmlInput: { maxLength: 254 } }} />
            <FormControl fullWidth>
              <InputLabel id="acct-status">Account status</InputLabel>
              <Select labelId="acct-status" label="Account status" value={status} onChange={(e) => setStatus(e.target.value as AccountStatus)}>
                {ACCOUNT_STATUSES.map((s) => <MenuItem key={s} value={s}>{titleCase(s)}</MenuItem>)}
              </Select>
            </FormControl>
            <FormControl component="fieldset">
              <FormLabel component="legend">Roles</FormLabel>
              <FormGroup row>
                <FormControlLabel control={<Checkbox checked disabled />} label="Member" />
                <FormControlLabel control={<Checkbox checked={isPublisher} onChange={(e) => setIsPublisher(e.target.checked)} />} label="Publisher" />
                <FormControlLabel control={<Checkbox checked={isAdmin} onChange={(e) => setIsAdmin(e.target.checked)} />} label="Administrator" />
              </FormGroup>
              <FormHelperText>Publishers can create books and edit the books they published. Administrators manage everything.</FormHelperText>
            </FormControl>
            {self && (status !== 'ACTIVE' || !isAdmin) && (
              <Alert severity="warning">You are editing your own account. Disabling it or removing your admin role takes effect immediately.</Alert>
            )}
            <Box>
              <Button type="submit" variant="contained" disabled={!dirty || !valid || save.isPending}>
                {save.isPending ? 'Saving…' : 'Save changes'}
              </Button>
            </Box>
          </Stack>
        </Box>
      </CardContent>
    </Card>
  );
}

function Memberships({ user }: { user: AdminUserDetail }) {
  const [dialog, setDialog] = useState<{ open: boolean; membership: AdminMembership | null }>({ open: false, membership: null });
  return (
    <Card>
      <CardContent>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
          <Typography variant="h3" component="h2">Memberships</Typography>
          <Button startIcon={<AddIcon />} onClick={() => setDialog({ open: true, membership: null })}>Add</Button>
        </Stack>
        {user.memberships.length === 0 ? (
          <Typography color="text.secondary">No membership records. This user cannot read books until one is added.</Typography>
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Status</TableCell>
                  <TableCell>From</TableCell>
                  <TableCell>To</TableCell>
                  <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>Notes</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {user.memberships.map((m) => (
                  <TableRow key={m.id}>
                    <TableCell><MembershipChip status={m.status} valid={m.validToday} /></TableCell>
                    <TableCell>{formatDate(m.startDate)}</TableCell>
                    <TableCell>{m.endDate ? formatDate(m.endDate) : 'No end date'}</TableCell>
                    <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' }, maxWidth: 240 }}>
                      <Typography variant="body2" noWrap title={m.notes ?? ''}>{m.notes}</Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Tooltip title="Edit">
                        <IconButton size="small" onClick={() => setDialog({ open: true, membership: m })} aria-label="Edit membership">
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </CardContent>
      <MembershipDialog
        open={dialog.open}
        membership={dialog.membership}
        fixedUser={{ id: user.id, fullName: user.fullName, mobile: user.mobile, email: user.email }}
        onClose={() => setDialog({ open: false, membership: null })}
      />
    </Card>
  );
}

export default function UserDetailPage() {
  const { id = '' } = useParams();
  const user = useQuery({ queryKey: adminKeys.user(id), queryFn: () => adminApi.user(id) });
  const u = user.data;

  return (
    <>
      <Breadcrumbs sx={{ mb: 2 }}>
        <Link component={RouterLink} to="/admin/users" underline="hover" color="inherit">Users</Link>
        <Typography color="text.primary" noWrap>{u?.fullName ?? '…'}</Typography>
      </Breadcrumbs>
      {user.isError ? (
        <AdminError error={user.error} onRetry={() => user.refetch()} />
      ) : !u ? (
        <Skeleton variant="rounded" height={300} />
      ) : (
        <>
          <PageHeader
            title={u.fullName}
            subtitle={
              <>
                {formatMobile(u.mobile) || 'No mobile number'} ·{' '}
                {u.mobileVerified ? `verified ${u.mobileVerifiedAt ? formatDate(u.mobileVerifiedAt) : ''}` : 'mobile not verified'}
                {u.email ? ` · ${u.email}` : ''} · joined {formatDate(u.createdAt)}
              </>
            }
            actions={<AccountChip status={u.accountStatus} />}
          />
          {u.lockedUntil && new Date(u.lockedUntil) > new Date() && (
            <Alert severity="warning" sx={{ mb: 2 }}>Temporarily locked after failed logins until {new Date(u.lockedUntil).toLocaleString()}.</Alert>
          )}
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'minmax(0, 2fr) minmax(0, 3fr)' }, alignItems: 'start' }}>
            <AccountForm user={u} />
            <Memberships user={u} />
          </Box>
        </>
      )}
    </>
  );
}
