import { Link as RouterLink } from 'react-router';
import { Box, Card, CardActionArea, CardContent, Skeleton, Typography } from '@mui/material';
import type { BookSummary } from '../api/types';
import { Cover } from './common';

export const bookGridSx = {
  display: 'grid',
  gap: 2,
  gridTemplateColumns: {
    xs: 'repeat(2, minmax(0, 1fr))',
    sm: 'repeat(3, minmax(0, 1fr))',
    md: 'repeat(4, minmax(0, 1fr))',
  },
} as const;

export default function BookCard({ book }: { book: BookSummary }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardActionArea component={RouterLink} to={`/books/${book.id}`} sx={{ height: '100%', alignItems: 'stretch' }}>
        <Cover url={book.coverUrl} title={book.title} />
        <CardContent sx={{ p: 1.5 }}>
          <Typography sx={{ ...clamp(2), fontWeight: 600 }} title={book.title}>
            {book.title}
          </Typography>
          {book.category && (
            <Typography sx={{ fontWeight: 600 }} variant="caption" color="secondary" component="div" noWrap>
              {book.category.name}
            </Typography>
          )}
          {book.description && (
            <Typography variant="body2" color="text.secondary" sx={{ ...clamp(2), mt: 0.5 }}>
              {book.description}
            </Typography>
          )}
        </CardContent>
      </CardActionArea>
    </Card>
  );
}

export function BookCardSkeleton() {
  return (
    <Box>
      <Skeleton variant="rectangular" sx={{ aspectRatio: '3 / 4', height: 'auto', borderRadius: 1 }} />
      <Skeleton width="80%" sx={{ mt: 1 }} />
      <Skeleton width="40%" />
    </Box>
  );
}

function clamp(lines: number) {
  return {
    display: '-webkit-box',
    WebkitLineClamp: lines,
    WebkitBoxOrient: 'vertical' as const,
    overflow: 'hidden',
  };
}
