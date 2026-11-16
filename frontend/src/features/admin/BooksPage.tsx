import { useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import {
  Box,
  Button,
  Chip,
  FormControl,
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
import AddIcon from '@mui/icons-material/Add';
import { adminApi, adminKeys } from '../../services/api/adminApi';
import { Cover, EmptyState } from '../../components/common';
import { formatDate } from '../../utils/format';
import { AdminError, BookChip, BOOK_STATUSES, PageHeader, Pager, titleCase } from './common';
import { useUrlFilters } from './useUrlFilters';

export default function BooksPage() {
  const { values, page, size, set } = useUrlFilters(['q', 'status', 'categoryId'] as const);
  const [search, setSearch] = useState(values.q);
  useEffect(() => setSearch(values.q), [values.q]);
  useEffect(() => {
    if (search.trim() === values.q) return;
    const t = setTimeout(() => set({ q: search.trim() || null }), 350);
    return () => clearTimeout(t);
    // Intentionally keyed on `search` only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search]);

  const categories = useQuery({ queryKey: adminKeys.categories, queryFn: adminApi.categories });
  const q = { q: values.q || undefined, status: values.status as never, categoryId: values.categoryId || undefined, page, size, sort: 'updatedAt,desc' };
  const books = useQuery({ queryKey: adminKeys.books(q), queryFn: () => adminApi.books(q), placeholderData: keepPreviousData });

  return (
    <>
      <PageHeader
        title="Books"
        subtitle="All books in every status. Only published books inside their date window appear in the public catalogue."
        actions={<Button variant="contained" startIcon={<AddIcon />} component={RouterLink} to="/admin/books/new">New book</Button>}
      />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <TextField size="small" type="search" label="Search title" value={search} onChange={(e) => setSearch(e.target.value)} />
        <FormControl size="small" sx={{ minWidth: 150 }}>
          <InputLabel id="b-status">Status</InputLabel>
          <Select labelId="b-status" label="Status" value={values.status} onChange={(e) => set({ status: e.target.value })}>
            <MenuItem value="">Any</MenuItem>
            {BOOK_STATUSES.map((s) => <MenuItem key={s} value={s}>{titleCase(s)}</MenuItem>)}
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: 180 }}>
          <InputLabel id="b-cat">Category</InputLabel>
          <Select labelId="b-cat" label="Category" value={values.categoryId} onChange={(e) => set({ categoryId: e.target.value })}>
            <MenuItem value="">Any</MenuItem>
            {(categories.data ?? []).map((c) => <MenuItem key={c.id} value={c.id}>{c.name}{c.active ? '' : ' (inactive)'}</MenuItem>)}
          </Select>
        </FormControl>
      </Stack>

      {books.isError ? (
        <AdminError error={books.error} onRetry={() => books.refetch()} />
      ) : (
        <Paper variant="outlined">
          <TableContainer>
            <Table size="small" sx={{ opacity: books.isPlaceholderData ? 0.6 : 1 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Book</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Available</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Effective</TableCell>
                  <TableCell sx={{ display: { xs: 'none', lg: 'table-cell' } }}>Expiry</TableCell>
                  <TableCell sx={{ display: { xs: 'none', lg: 'table-cell' } }}>Updated</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {books.isPending
                  ? <TableRow><TableCell colSpan={6}>Loading…</TableCell></TableRow>
                  : books.data.content.map((b) => (
                      <TableRow key={b.id} hover>
                        <TableCell>
                          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                            <Box sx={{ width: 36, flexShrink: 0, borderRadius: 0.5, overflow: 'hidden' }}><Cover url={b.coverUrl} title={b.title} /></Box>
                            <Box sx={{ minWidth: 0 }}>
                              <Link component={RouterLink} to={`/admin/books/${b.id}`} sx={{ fontWeight: 600 }}>{b.title}</Link>
                              {b.category && <Typography variant="body2" color="text.secondary">{b.category.name}</Typography>}
                            </Box>
                          </Stack>
                        </TableCell>
                        <TableCell><BookChip status={b.status} /></TableCell>
                        <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>
                          {b.availableToday ? <Chip size="small" color="success" variant="outlined" label="Public now" /> : <Typography variant="body2" color="text.secondary">No</Typography>}
                        </TableCell>
                        <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{formatDate(b.effectiveDate)}</TableCell>
                        <TableCell sx={{ display: { xs: 'none', lg: 'table-cell' } }}>{b.expiryDate ? formatDate(b.expiryDate) : 'No expiry'}</TableCell>
                        <TableCell sx={{ display: { xs: 'none', lg: 'table-cell' } }}>{formatDate(b.updatedAt)}</TableCell>
                      </TableRow>
                    ))}
              </TableBody>
            </Table>
          </TableContainer>
          {books.data && books.data.content.length === 0 && <EmptyState title="No books match these filters" />}
          {books.data && <Pager data={books.data} onPage={(p) => set({ page: p })} onSize={(s) => set({ size: s })} />}
        </Paper>
      )}
    </>
  );
}
