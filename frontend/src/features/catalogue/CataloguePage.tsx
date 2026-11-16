import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import {
  Box,
  Chip,
  Container,
  FormControl,
  InputLabel,
  MenuItem,
  Pagination,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { catalogueApi, queryKeys, type BookQuery, type BookSort } from '../../services/api/endpoints';
import BookCard, { BookCardSkeleton, bookGridSx } from './BookCard';
import { EmptyState, ErrorAlert } from '../../components/common';

const PAGE_SIZE = 20;
const SORTS: { value: BookSort; label: string }[] = [
  { value: 'title,asc', label: 'Title (A–Z)' },
  { value: 'updatedAt,desc', label: 'Recently updated' },
  { value: 'effectiveDate,desc', label: 'Newest' },
];

function readSort(v: string | null): BookSort {
  return SORTS.some((s) => s.value === v) ? (v as BookSort) : 'title,asc';
}

/** Catalogue state lives in the URL so results are linkable and survive back/forward. */
export default function CataloguePage() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const categoryId = params.get('category') ?? '';
  const sort = readSort(params.get('sort'));
  const page = Math.max(1, Number.parseInt(params.get('page') ?? '1', 10) || 1);

  const update = (changes: Record<string, string | null>, resetPage = true) => {
    const next = new URLSearchParams(params);
    for (const [k, v] of Object.entries(changes)) {
      if (v) next.set(k, v);
      else next.delete(k);
    }
    if (resetPage) next.delete('page');
    setParams(next);
  };

  // Local search box state, debounced into the URL.
  const [term, setTerm] = useState(q);
  useEffect(() => setTerm(q), [q]);
  useEffect(() => {
    if (term.trim() === q) return;
    const t = setTimeout(() => update({ q: term.trim() || null }), 350);
    return () => clearTimeout(t);
    // Intentionally keyed on `term` only: react to typing, not to URL changes.
  }, [term]);

  const query: BookQuery = { q: q || undefined, categoryId: categoryId || undefined, sort, page: page - 1, size: PAGE_SIZE };
  const categories = useQuery({ queryKey: queryKeys.categories, queryFn: catalogueApi.categories, staleTime: 5 * 60_000 });
  const books = useQuery({
    queryKey: queryKeys.books(query),
    queryFn: ({ signal }) => catalogueApi.books(query, signal),
    placeholderData: keepPreviousData,
  });

  return (
    <Container maxWidth="lg" sx={{ py: 4, px: 2 }}>
      <Typography variant="h2" component="h1" sx={{ mb: 2 }}>
        Catalogue
      </Typography>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          label="Search"
          value={term}
          onChange={(e) => setTerm(e.target.value)}
          size="small"
          type="search"
        />
        <FormControl size="small" sx={{ minWidth: 200 }}>
          <InputLabel id="sort-label">Sort</InputLabel>
          <Select labelId="sort-label" label="Sort" value={sort} onChange={(e) => update({ sort: e.target.value })}>
            {SORTS.map((s) => (
              <MenuItem key={s.value} value={s.value}>
                {s.label}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </Stack>

      {categories.data && categories.data.length > 0 && (
        <Stack direction="row" spacing={1} useFlexGap sx={{ mb: 3, flexWrap: 'wrap' }} aria-label="Categories">
          <Chip label="All" color={categoryId ? 'default' : 'primary'} onClick={() => update({ category: null })} />
          {categories.data.map((c) => (
            <Chip
              key={c.id}
              label={c.name}
              color={categoryId === c.id ? 'primary' : 'default'}
              onClick={() => update({ category: categoryId === c.id ? null : c.id })}
            />
          ))}
        </Stack>
      )}

      {books.isError ? (
        <ErrorAlert error={books.error} onRetry={() => books.refetch()} />
      ) : books.isPending ? (
        <Box sx={bookGridSx}>
          {Array.from({ length: 8 }, (_, i) => (
            <BookCardSkeleton key={i} />
          ))}
        </Box>
      ) : books.data.content.length === 0 ? (
        <EmptyState title="No books match your search">Try a different word or category.</EmptyState>
      ) : (
        <>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
            {books.data.totalElements} {books.data.totalElements === 1 ? 'book' : 'books'}
          </Typography>
          <Box sx={{ ...bookGridSx, opacity: books.isPlaceholderData ? 0.6 : 1, transition: 'opacity .2s' }}>
            {books.data.content.map((b) => (
              <BookCard key={b.id} book={b} />
            ))}
          </Box>
          {books.data.totalPages > 1 && (
            <Stack sx={{ alignItems: 'center', mt: 4 }}>
              <Pagination
                count={books.data.totalPages}
                page={page}
                onChange={(_, p) => {
                  update({ page: p > 1 ? String(p) : null }, false);
                  window.scrollTo({ top: 0 });
                }}
                color="primary"
              />
            </Stack>
          )}
        </>
      )}
    </Container>
  );
}
