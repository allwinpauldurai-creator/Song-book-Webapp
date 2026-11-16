import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
} from '@mui/material';
import { ApiError } from '../../services/api/client';
import { adminApi, adminKeys } from '../../services/api/adminApi';
import type { MembershipStatus } from '../../types/api';
import type { AdminMembership } from '../../types/admin';
import { adminErrorMessage, MEMBERSHIP_STATUSES, titleCase, todayIso, UserPicker, type UserOption } from './common';

/**
 * Create or edit a membership record. Creating an ACTIVE membership cancels the user's previous
 * ACTIVE one (backend MembershipService#create); history is never deleted.
 */
export default function MembershipDialog({
  open,
  onClose,
  membership,
  fixedUser,
}: {
  open: boolean;
  onClose: () => void;
  /** Edit this record; omit to create. */
  membership?: AdminMembership | null;
  /** Pre-selected, non-editable user (from the user page). */
  fixedUser?: UserOption | null;
}) {
  const queryClient = useQueryClient();
  const editing = !!membership;
  const [user, setUser] = useState<UserOption | null>(null);
  const [status, setStatus] = useState<MembershipStatus>('ACTIVE');
  const [startDate, setStartDate] = useState(todayIso());
  const [endDate, setEndDate] = useState('');
  const [notes, setNotes] = useState('');

  useEffect(() => {
    if (!open) return;
    setUser(
      membership ? { id: membership.userId, fullName: membership.userFullName, email: membership.userEmail } : (fixedUser ?? null),
    );
    setStatus(membership?.status ?? 'ACTIVE');
    setStartDate(membership?.startDate ?? todayIso());
    setEndDate(membership?.endDate ?? '');
    setNotes(membership?.notes ?? '');
  }, [open, membership, fixedUser]);

  const save = useMutation({
    mutationFn: () => {
      const body = { startDate, endDate: endDate || null, status, notes: notes.trim() || null };
      return membership
        ? adminApi.updateMembership(membership.id, { ...body, version: membership.version })
        : adminApi.createMembership({ ...body, userId: user!.id });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.all });
      onClose();
    },
  });
  // Reset error state between openings.
  useEffect(() => {
    if (open) save.reset();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const badRange = !!endDate && endDate < startDate;
  const fields = save.error instanceof ApiError ? save.error.fieldMap() : {};
  const canSave = !!user && !!startDate && !badRange && !save.isPending;

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (canSave) save.mutate();
  };

  return (
    <Dialog open={open} onClose={save.isPending ? undefined : onClose} fullWidth maxWidth="sm" component="form" onSubmit={onSubmit}>
      <DialogTitle>{editing ? 'Edit membership' : 'New membership'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2.5} sx={{ pt: 1 }}>
          {save.isError && <Alert severity="error">{adminErrorMessage(save.error)}</Alert>}
          <UserPicker value={user} onChange={setUser} required disabled={editing || !!fixedUser} />
          <FormControl fullWidth>
            <InputLabel id="membership-status">Status</InputLabel>
            <Select labelId="membership-status" label="Status" value={status} onChange={(e) => setStatus(e.target.value as MembershipStatus)}>
              {MEMBERSHIP_STATUSES.map((s) => (
                <MenuItem key={s} value={s}>
                  {titleCase(s)}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          {!editing && status === 'ACTIVE' && (
            <Alert severity="info">If this user already has an active membership, it will be cancelled and replaced by this one.</Alert>
          )}
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField
              label="Start date"
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              required
              error={!!fields.startDate}
              helperText={fields.startDate ?? ' '}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label="End date"
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              error={badRange || !!fields.endDate}
              helperText={badRange ? 'Must be on or after the start date.' : (fields.endDate ?? 'Leave empty for no end date.')}
              slotProps={{ inputLabel: { shrink: true } }}
            />
          </Stack>
          <TextField
            label="Notes"
            multiline
            minRows={2}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            helperText={`${notes.length}/500`}
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={save.isPending}>
          Cancel
        </Button>
        <Button type="submit" variant="contained" disabled={!canSave}>
          {save.isPending ? 'Saving…' : 'Save'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
