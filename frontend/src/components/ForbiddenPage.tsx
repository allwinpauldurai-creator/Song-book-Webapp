import { Link as RouterLink } from 'react-router';
import { Button, Container } from '@mui/material';
import { EmptyState } from './common';

export default function ForbiddenPage() {
  return (
    <Container maxWidth="sm" sx={{ py: 6, px: 2 }}>
      <EmptyState title="You don't have access to this page">
        <Button component={RouterLink} to="/" variant="contained" sx={{ mt: 2 }}>
          Go home
        </Button>
      </EmptyState>
    </Container>
  );
}
