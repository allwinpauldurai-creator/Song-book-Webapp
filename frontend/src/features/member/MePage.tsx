import { useState } from 'react';
import { Link as RouterLink } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import {
  Box,
  Button,
  Card,
  CardActionArea,
  CardContent,
  Chip,
  Container,
  Link,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  Pagination,
  Skeleton,
  Stack,
  Typography,
} from '@mui/material';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import LibraryBooksIcon from '@mui/icons-material/LibraryBooks';
import PersonIcon from '@mui/icons-material/Person';
import RateReviewIcon from '@mui/icons-material/RateReview';
import { memberApi, queryKeys } from '../../services/api/endpoints';
import type { HistoryItem, MembershipSummary } from '../../types/api';
import { useAuth } from '../../contexts/AuthContext';
import { Cover, ErrorAlert } from '../../components/common';
import { formatDate } from '../../utils/format';
import { formatMobile } from '../../utils/mobile';
import VerifyMobileAlert from '../auth/VerifyMobileAlert';

function MembershipCard({ membership, isAdmin }: { membership?: MembershipSummary | null; isAdmin: boolean }) {
  return (
    <Card>
      <CardContent>
        <Typography variant="overline" color="text.secondary">
          Membership
        </Typography>
        {membership ? (
          <>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', my: 1 }}>
              <Chip
                label={membership.valid ? 'Active' : membership.status.charAt(0) + membership.status.slice(1).toLowerCase()}
                color={membership.valid ? 'success' : 'warning'}
                size="small"
              />
            </Stack>
            <Typography variant="body2" color="text.secondary">
              From {formatDate(membership.startDate)} {membership.endDate ? `to ${formatDate(membership.endDate)}` : '· no end date'}
            </Typography>
          </>
        ) : (
          <Typography sx={{ mt: 1 }} color="text.secondary">
            {isAdmin ? 'Administrators can read every book without a membership.' : 'No membership yet.'}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

const HISTORY_SIZE = 5;

function ContinueReading({ item }: { item: HistoryItem }) {
  return (
    <Card>
      <CardActionArea component={RouterLink} to={`/read/${item.bookId}`} sx={{ display: 'flex', alignItems: 'stretch', justifyContent: 'flex-start' }}>
        <Box sx={{ width: 96, flexShrink: 0 }}>
          <Cover url={item.coverUrl} title={item.title} />
        </Box>
        <CardContent sx={{ minWidth: 0, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
          <Typography variant="overline" color="text.secondary">
            Continue reading
          </Typography>
          <Typography variant="h3" component="p" noWrap title={item.title}>
            {item.title}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Page {item.lastPage} · {formatDate(item.updatedAt)}
          </Typography>
          <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', mt: 1, color: 'primary.main' }}>
            <MenuBookIcon fontSize="small" />
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              Resume
            </Typography>
          </Stack>
        </CardContent>
      </CardActionArea>
    </Card>
  );
}

function ReadingHistory({ canRead }: { canRead: boolean }) {
  const [page, setPage] = useState(1);
  const history = useQuery({
    queryKey: queryKeys.history(page - 1, HISTORY_SIZE),
    queryFn: () => memberApi.history(page - 1, HISTORY_SIZE),
    placeholderData: keepPreviousData,
    staleTime: 0, // the reader saves progress; always show the latest on return
  });

  if (history.isError) return <ErrorAlert error={history.error} onRetry={() => history.refetch()} />;
  if (history.isPending) return <Skeleton variant="rounded" height={140} />;

  const items = history.data.content;
  const latest = page === 1 ? items[0] : undefined;
  return (
    <Stack spacing={2}>
      {latest && canRead && <ContinueReading item={latest} />}
      <Card>
        <CardContent>
          <Typography variant="h3" component="h2" sx={{ mb: 1 }}>
            Reading history
          </Typography>
          {items.length === 0 ? (
            <Typography color="text.secondary">
              Books you open will appear here. <Link component={RouterLink} to="/books">Browse the catalogue</Link>
            </Typography>
          ) : (
            <List disablePadding sx={{ opacity: history.isPlaceholderData ? 0.6 : 1 }}>
              {items.map((h, i) => (
                <ListItem key={h.bookId} disablePadding divider={i < items.length - 1}>
                  <ListItemButton component={RouterLink} to={canRead ? `/read/${h.bookId}` : `/books/${h.bookId}`} sx={{ px: 1 }}>
                    <Box sx={{ width: 36, mr: 1.5, flexShrink: 0, borderRadius: 0.5, overflow: 'hidden' }}>
                      <Cover url={h.coverUrl} title={h.title} />
                    </Box>
                    <ListItemText
                      primary={h.title}
                      secondary={`Page ${h.lastPage} · ${formatDate(h.updatedAt)}`}
                      slotProps={{ primary: { noWrap: true } }}
                    />
                  </ListItemButton>
                </ListItem>
              ))}
            </List>
          )}
          {history.data.totalPages > 1 && (
            <Stack sx={{ alignItems: 'center', mt: 1 }}>
              <Pagination size="small" count={history.data.totalPages} page={page} onChange={(_, p) => setPage(p)} />
            </Stack>
          )}
        </CardContent>
      </Card>
    </Stack>
  );
}

export default function MePage() {
  const { user, isAdmin } = useAuth();
  if (!user) return null;
  const canRead = user.mobileVerified && (isAdmin || !!user.membership?.valid);

  return (
    <Container maxWidth="md" sx={{ py: 4, px: 2 }}>
      <Typography variant="h2" component="h1" sx={{ mb: 3 }}>
        Hello, {user.fullName.split(' ')[0]}
      </Typography>
      <Stack spacing={2}>
        {!user.mobileVerified && <VerifyMobileAlert user={user} reason="to start reading" />}
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
          <MembershipCard membership={user.membership} isAdmin={isAdmin} />
          <Card>
            <CardContent>
              <Typography variant="overline" color="text.secondary">
                Account
              </Typography>
              <Typography sx={{ fontWeight: 600, mt: 1 }}>
                {user.fullName}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
                {formatMobile(user.mobile)}
              </Typography>
              {user.email && (
                <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
                  {user.email}
                </Typography>
              )}
              <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
                {user.roles.map((r) => (
                  <Chip key={r} label={r.charAt(0) + r.slice(1).toLowerCase()} size="small" variant="outlined" />
                ))}
              </Stack>
            </CardContent>
          </Card>
        </Box>
        <ReadingHistory canRead={canRead} />
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
          <Button component={RouterLink} to="/books" variant="contained" startIcon={<LibraryBooksIcon />}>
            Browse the catalogue
          </Button>
          <Button component={RouterLink} to="/me/profile" variant="outlined" startIcon={<PersonIcon />}>
            Profile
          </Button>
          <Button component={RouterLink} to="/me/requests" variant="outlined" startIcon={<RateReviewIcon />}>
            My requests
          </Button>
        </Stack>
      </Stack>
    </Container>
  );
}
