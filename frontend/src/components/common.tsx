import { useState, type ReactNode } from 'react';
import { Alert, Box, Button, Paper, Typography } from '@mui/material';
import LibraryMusicOutlinedIcon from '@mui/icons-material/LibraryMusicOutlined';
import { apiUrl, errorMessage } from '../services/api/client';

export function ErrorAlert({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
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
      {errorMessage(error)}
    </Alert>
  );
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <Box sx={{ textAlign: 'center', py: 8, color: 'text.secondary' }}>
      <LibraryMusicOutlinedIcon sx={{ fontSize: 56, opacity: 0.5 }} />
      <Typography variant="h3" sx={{ mt: 1, color: 'text.primary' }}>
        {title}
      </Typography>
      {children && <Box sx={{ mt: 1 }}>{children}</Box>}
    </Box>
  );
}

/** Book cover with a neutral placeholder when there is no cover or it fails to load. */
export function Cover({ url, title, height }: { url?: string | null; title: string; height?: number | string }) {
  const [failed, setFailed] = useState(false);
  const showImage = url && !failed;
  return (
    <Box
      sx={{
        aspectRatio: height ? undefined : '3 / 4',
        height,
        width: '100%',
        bgcolor: 'action.hover',
        display: 'grid',
        placeItems: 'center',
        overflow: 'hidden',
      }}
    >
      {showImage ? (
        <Box
          component="img"
          src={apiUrl(url)}
          alt={`Cover of ${title}`}
          loading="lazy"
          onError={() => setFailed(true)}
          sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
        />
      ) : (
        <LibraryMusicOutlinedIcon aria-hidden sx={{ fontSize: 48, color: 'text.disabled' }} />
      )}
    </Box>
  );
}

/** Centered card used by the login/register/reset pages. */
export function AuthCard({ title, subtitle, children }: { title: string; subtitle?: ReactNode; children: ReactNode }) {
  return (
    <Box sx={{ px: 2, py: { xs: 4, sm: 8 }, display: 'flex', justifyContent: 'center' }}>
      <Paper variant="outlined" sx={{ p: { xs: 3, sm: 4 }, width: '100%', maxWidth: 440 }}>
        <Typography variant="h2" component="h1" gutterBottom>
          {title}
        </Typography>
        {subtitle && (
          <Typography color="text.secondary" sx={{ mb: 3 }}>
            {subtitle}
          </Typography>
        )}
        {children}
      </Paper>
    </Box>
  );
}
