import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  IconButton,
  Paper,
  Skeleton,
  Stack,
  Switch,
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
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import { adminApi, adminKeys } from '../../services/api/adminApi';
import type { AdminCategory } from '../../types/admin';
import { EmptyState } from '../../components/common';
import { AdminError, adminErrorMessage, PageHeader } from './common';

function useInvalidate() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: adminKeys.categories });
    void queryClient.invalidateQueries({ queryKey: ['categories'] }); // public chips on the catalogue
  };
}

function NewCategory() {
  const invalidate = useInvalidate();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const create = useMutation({
    mutationFn: () => adminApi.createCategory({ name: name.trim(), description: description.trim() || null }),
    onSuccess: () => {
      setName('');
      setDescription('');
      invalidate();
    },
  });
  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (name.trim()) create.mutate();
  };
  return (
    <Card>
      <CardContent>
        <Typography variant="h3" component="h2" sx={{ mb: 2 }}>New category</Typography>
        <Stack component="form" onSubmit={onSubmit} direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ alignItems: { md: 'flex-start' } }}>
          <TextField size="small" label="Name" value={name} onChange={(e) => setName(e.target.value)} required sx={{ maxWidth: { md: 240 } }}
            slotProps={{ htmlInput: { maxLength: 100 } }} />
          <TextField size="small" label="Description" value={description} onChange={(e) => setDescription(e.target.value)}
            slotProps={{ htmlInput: { maxLength: 2000 } }} />
          <Button type="submit" variant="contained" disabled={!name.trim() || create.isPending} sx={{ flexShrink: 0 }}>
            {create.isPending ? 'Adding…' : 'Add'}
          </Button>
        </Stack>
        {create.isError && <Alert severity="error" sx={{ mt: 2 }}>{adminErrorMessage(create.error)}</Alert>}
      </CardContent>
    </Card>
  );
}

function CategoryRow({ c }: { c: AdminCategory }) {
  const invalidate = useInvalidate();
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(c.name);
  const [description, setDescription] = useState(c.description ?? '');
  const save = useMutation({
    mutationFn: (changes: Partial<{ name: string; description: string | null; active: boolean }>) =>
      adminApi.updateCategory(c.id, { name: c.name, description: c.description ?? null, active: c.active, version: c.version, ...changes }),
    onSuccess: () => {
      setEditing(false);
      invalidate();
    },
  });

  return (
    <>
      <TableRow hover>
        <TableCell sx={{ width: { md: 240 } }}>
          {editing ? (
            <TextField size="small" value={name} onChange={(e) => setName(e.target.value)} aria-label="Name" slotProps={{ htmlInput: { maxLength: 100 } }} />
          ) : (
            <Typography sx={{ fontWeight: 600 }}>{c.name}</Typography>
          )}
        </TableCell>
        <TableCell>
          {editing ? (
            <TextField size="small" value={description} onChange={(e) => setDescription(e.target.value)} aria-label="Description" slotProps={{ htmlInput: { maxLength: 2000 } }} />
          ) : (
            <Typography variant="body2" color="text.secondary">{c.description}</Typography>
          )}
        </TableCell>
        <TableCell sx={{ whiteSpace: 'nowrap' }}>
          <Tooltip title={c.active ? 'Shown on the public catalogue' : 'Hidden from the public catalogue'}>
            <Switch
              checked={c.active}
              disabled={save.isPending}
              onChange={(e) => save.mutate({ active: e.target.checked })}
              slotProps={{ input: { 'aria-label': `${c.name} active` } }}
            />
          </Tooltip>
          {!c.active && <Chip size="small" label="Inactive" />}
        </TableCell>
        <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
          {editing ? (
            <>
              <IconButton aria-label="Save" disabled={!name.trim() || save.isPending}
                onClick={() => save.mutate({ name: name.trim(), description: description.trim() || null })}>
                <CheckIcon />
              </IconButton>
              <IconButton aria-label="Cancel" onClick={() => { setEditing(false); setName(c.name); setDescription(c.description ?? ''); save.reset(); }}>
                <CloseIcon />
              </IconButton>
            </>
          ) : (
            <IconButton aria-label={`Edit ${c.name}`} onClick={() => setEditing(true)}>
              <EditIcon fontSize="small" />
            </IconButton>
          )}
        </TableCell>
      </TableRow>
      {save.isError && (
        <TableRow>
          <TableCell colSpan={4}><Alert severity="error">{adminErrorMessage(save.error)}</Alert></TableCell>
        </TableRow>
      )}
    </>
  );
}

export default function CategoriesPage() {
  const list = useQuery({ queryKey: adminKeys.categories, queryFn: adminApi.categories });
  return (
    <>
      <PageHeader title="Categories" subtitle="Categories are deactivated, never deleted, so books keep their history." />
      <Stack spacing={3}>
        <NewCategory />
        {list.isError ? (
          <AdminError error={list.error} onRetry={() => list.refetch()} />
        ) : list.isPending ? (
          <Skeleton variant="rounded" height={200} />
        ) : list.data.length === 0 ? (
          <EmptyState title="No categories yet" />
        ) : (
          <Paper variant="outlined">
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Name</TableCell>
                    <TableCell>Description</TableCell>
                    <TableCell>Active</TableCell>
                    <TableCell />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {list.data.map((c) => <CategoryRow key={`${c.id}-${c.version}`} c={c} />)}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
        )}
      </Stack>
      <Box />
    </>
  );
}
