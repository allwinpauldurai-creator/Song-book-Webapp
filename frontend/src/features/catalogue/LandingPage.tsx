import { useState, type FormEvent } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { Box, Button, Container, InputAdornment, Stack, TextField, Typography } from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import { catalogueApi, queryKeys, type BookQuery } from '../../services/api/endpoints';
import BookCard, { BookCardSkeleton, bookGridSx } from './BookCard';
import { EmptyState, ErrorAlert } from '../../components/common';
import { useAuth } from '../../contexts/AuthContext';

const LATEST: BookQuery = { page: 0, size: 8, sort: 'updatedAt,desc' };

export default function LandingPage() {
  const navigate = useNavigate();
  const { status } = useAuth();
  const [q, setQ] = useState('');
  const latest = useQuery({ queryKey: queryKeys.books(LATEST), queryFn: ({ signal }) => catalogueApi.books(LATEST, signal) });

  const search = (e: FormEvent) => {
    e.preventDefault();
    const term = q.trim();
    navigate(term ? `/books?q=${encodeURIComponent(term)}` : '/books');
  };

  return (
    <>
      <Box
        sx={(t) => ({
          py: { xs: 6, md: 10 },
          background: `linear-gradient(135deg, ${t.palette.primary.main} 0%, ${t.palette.secondary.main} 100%)`,
          color: 'common.white',
          ...t.applyStyles('dark', { color: 'grey.900' }),
        })}
      >
        <Container maxWidth="md" sx={{ textAlign: 'center' }}>
          <Typography variant="h1" gutterBottom>
            Every song book, one place
          </Typography>
          <Typography sx={{ fontSize: '1.125rem', opacity: 0.9, mb: 4 }}>
            Browse the catalogue freely. Members can read and download every book online.
          </Typography>
          <Box component="form" role="search" onSubmit={search} sx={{ maxWidth: 560, mx: 'auto' }}>
            <TextField
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search by title or description"
              aria-label="Search song books"
              sx={{ bgcolor: 'background.paper', borderRadius: 1 }}
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon />
                    </InputAdornment>
                  ),
                  endAdornment: (
                    <Button type="submit" variant="contained">
                      Search
                    </Button>
                  ),
                },
              }}
            />
          </Box>
          {status === 'anonymous' && (
            <Stack direction="row" spacing={1.5} sx={{ justifyContent: 'center', mt: 3 }}>
              <Button component={RouterLink} to="/register" variant="contained" color="inherit" sx={{ color: 'primary.main' }}>
                Become a member
              </Button>
              <Button component={RouterLink} to="/login" variant="outlined" color="inherit">
                Log in
              </Button>
            </Stack>
          )}
        </Container>
      </Box>

      <Container maxWidth="lg" sx={{ py: 5, px: 2 }}>
        <Stack direction="row" sx={{ alignItems: 'baseline', justifyContent: 'space-between', mb: 2 }}>
          <Typography variant="h2">Latest books</Typography>
          <Button component={RouterLink} to="/books">
            View all
          </Button>
        </Stack>
        {latest.isError ? (
          <ErrorAlert error={latest.error} onRetry={() => latest.refetch()} />
        ) : latest.isPending ? (
          <Box sx={bookGridSx}>
            {Array.from({ length: 4 }, (_, i) => (
              <BookCardSkeleton key={i} />
            ))}
          </Box>
        ) : latest.data.content.length === 0 ? (
          <EmptyState title="No books yet">Check back soon.</EmptyState>
        ) : (
          <Box sx={bookGridSx}>
            {latest.data.content.map((b) => (
              <BookCard key={b.id} book={b} />
            ))}
          </Box>
        )}
      </Container>
    </>
  );
}
