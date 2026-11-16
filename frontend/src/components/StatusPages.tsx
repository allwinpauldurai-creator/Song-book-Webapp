import { Link as RouterLink } from 'react-router';
import { Button, Container } from '@mui/material';
import { EmptyState } from './common';

export function NotFoundPage() {
  return (
    <Container maxWidth="sm" sx={{ py: 6, px: 2 }}>
      <EmptyState title="Page not found">
        <Button component={RouterLink} to="/" variant="contained" sx={{ mt: 2 }}>
          Go home
        </Button>
      </EmptyState>
    </Container>
  );
}

