import { useEffect, useState, type ReactNode } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Stack,
  TablePagination,
  TextField,
  Typography,
  type ChipProps,
} from '@mui/material';
import { ApiError, errorMessage } from '../../services/api/client';
import { adminApi, adminKeys } from '../../services/api/adminApi';
import type { MembershipStatus, PageResponse, RequestStatus } from '../../types/api';
import type { AccountStatus, BookStatus } from '../../types/admin';

// ---------------------------------------------------------------- errors

/** Friendlier wording for admin-specific error codes; falls back to the server's message. */
export function adminErrorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    switch (e.code) {
      case 'CONCURRENT_MODIFICATION':
        return 'Someone else changed this record since you opened it. Reload the page and try again.';
      case 'LAST_ADMIN':
        return 'This is the last active administrator. Make another user an admin first.';
      case 'FILE_TOO_LARGE':
        return 'The file is too large (PDF max 100 MB, cover max 5 MB).';
      default:
        return errorMessage(e);
    }
  }
  return errorMessage(e);
}

export function AdminError({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <Alert
      severity="error"
      action={
        onRetry && (
          <Button color="inherit" size="small" onClick={onRetry}>
            Retry
          </Button>
        )
      }
    >
      {adminErrorMessage(error)}
    </Alert>
  );
}

// ---------------------------------------------------------------- layout bits

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, mb: 3 }}>
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="h2" component="h1" sx={{ overflowWrap: 'anywhere' }}>
          {title}
        </Typography>
        {subtitle && <Typography color="text.secondary">{subtitle}</Typography>}
      </Box>
      {actions && <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>{actions}</Stack>}
    </Stack>
  );
}

/** TablePagination wired to a PageResponse (0-based pages on both sides). */
export function Pager<T>({
  data,
  onPage,
  onSize,
}: {
  data: PageResponse<T>;
  onPage: (page: number) => void;
  onSize?: (size: number) => void;
}) {
  return (
    <TablePagination
      component="div"
      count={data.totalElements}
      page={Math.min(data.page, Math.max(0, data.totalPages - 1))}
      rowsPerPage={data.size}
      onPageChange={(_, p) => onPage(p)}
      rowsPerPageOptions={onSize ? [10, 20, 50, 100] : [data.size]}
      onRowsPerPageChange={(e) => onSize?.(Number(e.target.value))}
    />
  );
}

// ---------------------------------------------------------------- status chips

const title = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();

const ACCOUNT_COLORS: Record<AccountStatus, ChipProps['color']> = { ACTIVE: 'success', DISABLED: 'default', LOCKED: 'error' };
const BOOK_COLORS: Record<BookStatus, ChipProps['color']> = { PUBLISHED: 'success', DRAFT: 'info', INACTIVE: 'warning', ARCHIVED: 'default' };
const MEMBERSHIP_COLORS: Record<MembershipStatus, ChipProps['color']> = { ACTIVE: 'success', SUSPENDED: 'warning', EXPIRED: 'default', CANCELLED: 'default' };
const REQUEST_COLORS: Record<RequestStatus, ChipProps['color']> = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'error' };

export const AccountChip = ({ status }: { status: AccountStatus }) => <Chip size="small" label={title(status)} color={ACCOUNT_COLORS[status]} />;
export const BookChip = ({ status }: { status: BookStatus }) => <Chip size="small" label={title(status)} color={BOOK_COLORS[status]} />;
export const MembershipChip = ({ status, valid }: { status: MembershipStatus; valid?: boolean }) => (
  <Chip
    size="small"
    label={valid === false && status === 'ACTIVE' ? 'Active (not in date range)' : title(status)}
    color={valid === false && status === 'ACTIVE' ? 'warning' : MEMBERSHIP_COLORS[status]}
  />
);
export const RequestChip = ({ status }: { status: RequestStatus }) => <Chip size="small" label={title(status)} color={REQUEST_COLORS[status]} />;

export const ACCOUNT_STATUSES: AccountStatus[] = ['ACTIVE', 'DISABLED', 'LOCKED'];
export const BOOK_STATUSES: BookStatus[] = ['DRAFT', 'PUBLISHED', 'INACTIVE', 'ARCHIVED'];
export const MEMBERSHIP_STATUSES: MembershipStatus[] = ['ACTIVE', 'SUSPENDED', 'EXPIRED', 'CANCELLED'];
export { title as titleCase };

// ---------------------------------------------------------------- pickers and dialogs

export interface UserOption {
  id: string;
  fullName: string;
  email: string;
}

/** Searches users by name or email (admin API). */
export function UserPicker({
  value,
  onChange,
  label = 'User',
  required,
  disabled,
}: {
  value: UserOption | null;
  onChange: (u: UserOption | null) => void;
  label?: string;
  required?: boolean;
  disabled?: boolean;
}) {
  const [input, setInput] = useState('');
  const [term, setTerm] = useState('');
  useEffect(() => {
    const t = setTimeout(() => setTerm(input.trim()), 300);
    return () => clearTimeout(t);
  }, [input]);
  const q = { q: term || undefined, size: 20, sort: 'fullName,asc' };
  const users = useQuery({ queryKey: adminKeys.users(q), queryFn: () => adminApi.users(q), placeholderData: keepPreviousData, enabled: !disabled });
  const options: UserOption[] = (users.data?.content ?? []).map(({ id, fullName, email }) => ({ id, fullName, email }));

  return (
    <Autocomplete
      value={value}
      disabled={disabled}
      onChange={(_, v) => onChange(v)}
      inputValue={input}
      onInputChange={(_, v) => setInput(v)}
      options={value && !options.some((o) => o.id === value.id) ? [value, ...options] : options}
      getOptionLabel={(o) => `${o.fullName} (${o.email})`}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      filterOptions={(x) => x}
      loading={users.isFetching}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          required={required}
          slotProps={{
            ...params.slotProps,
            input: {
              ...params.slotProps.input,
              endAdornment: (
                <>
                  {users.isFetching && <CircularProgress size={18} />}
                  {params.slotProps.input.endAdornment}
                </>
              ),
            },
          }}
        />
      )}
    />
  );
}

export function ConfirmDialog({
  open,
  title: heading,
  body,
  confirmLabel,
  danger,
  busy,
  error,
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: string;
  body: ReactNode;
  confirmLabel: string;
  danger?: boolean;
  busy?: boolean;
  error?: unknown;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Dialog open={open} onClose={busy ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{heading}</DialogTitle>
      <DialogContent>
        {error != null && (
          <Box sx={{ mb: 2 }}>
            <AdminError error={error} />
          </Box>
        )}
        <DialogContentText component="div">{body}</DialogContentText>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={busy}>
          Cancel
        </Button>
        <Button onClick={onConfirm} variant="contained" color={danger ? 'error' : 'primary'} disabled={busy}>
          {busy ? 'Working…' : confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/** "12.3 MB" style sizes. */
export function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

/** Today's date as yyyy-mm-dd in the browser's timezone (for date inputs). */
export function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
