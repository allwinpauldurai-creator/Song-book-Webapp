import { useEffect, useState, type FormEvent } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Autocomplete,
  Box,
  Breadcrumbs,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Container,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  Link,
  MenuItem,
  Pagination,
  Select,
  Skeleton,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { ApiError, errorMessage } from '../../services/api/client';
import { catalogueApi, memberApi, queryKeys } from '../../services/api/endpoints';
import type { ModificationRequest, RequestStatus, RequestType } from '../../types/api';
import { EmptyState, ErrorAlert } from '../../components/common';
import { formatDate } from '../../utils/format';

const PAGE_SIZE = 10;
const MAX_DESCRIPTION = 5000;

export const REQUEST_TYPES: { value: RequestType; label: string; hint: string }[] = [
  { value: 'CORRECTION', label: 'Correction', hint: 'A mistake in lyrics, chords, numbering or metadata.' },
  { value: 'ADDITION', label: 'Addition', hint: 'A song or book you would like added.' },
  { value: 'OTHER', label: 'Other', hint: 'Anything else about the library.' },
];

const STATUS: Record<RequestStatus, { label: string; color: 'default' | 'success' | 'error' | 'warning' }> = {
  PENDING: { label: 'Pending', color: 'warning' },
  APPROVED: { label: 'Approved', color: 'success' },
  REJECTED: { label: 'Rejected', color: 'error' },
};

interface BookOption {
  id: string;
  title: string;
}

/** Book picker that searches the public catalogue as you type. */
function BookPicker({ value, onChange }: { value: BookOption | null; onChange: (b: BookOption | null) => void }) {
  const [input, setInput] = useState('');
  const [term, setTerm] = useState('');
  useEffect(() => {
    const t = setTimeout(() => setTerm(input.trim()), 300);
    return () => clearTimeout(t);
  }, [input]);

  const query = { q: term || undefined, size: 20, sort: 'title,asc' as const };
  const results = useQuery({
    queryKey: queryKeys.books(query),
    queryFn: ({ signal }) => catalogueApi.books(query, signal),
    placeholderData: keepPreviousData,
  });
  const options: BookOption[] = (results.data?.content ?? []).map((b) => ({ id: b.id, title: b.title }));

  return (
    <Autocomplete
      value={value}
      onChange={(_, v) => onChange(v)}
      inputValue={input}
      onInputChange={(_, v) => setInput(v)}
      options={value && !options.some((o) => o.id === value.id) ? [value, ...options] : options}
      getOptionLabel={(o) => o.title}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      filterOptions={(x) => x} // the server already filtered
      loading={results.isFetching}
      noOptionsText={term ? 'No matching books' : 'Type to search'}
      renderInput={(params) => (
        <TextField
          {...params}
          label="Book (optional)"
          helperText="Leave empty for a general request."
          slotProps={{
            ...params.slotProps,
            input: {
              ...params.slotProps.input,
              endAdornment: (
                <>
                  {results.isFetching && <CircularProgress size={18} />}
                  {params.slotProps.input.endAdornment}
                </>
              ),
            },
          }}
        />
      )}
    />
  );
}

function NewRequestDialog({ open, onClose, initialBook }: { open: boolean; onClose: () => void; initialBook: BookOption | null }) {
  const queryClient = useQueryClient();
  const [type, setType] = useState<RequestType>('CORRECTION');
  const [book, setBook] = useState<BookOption | null>(initialBook);
  const [description, setDescription] = useState('');
  useEffect(() => {
    if (open) setBook(initialBook);
  }, [open, initialBook]);

  const create = useMutation({
    mutationFn: () => memberApi.createRequest({ requestType: type, bookId: book?.id ?? null, description: description.trim() }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['member', 'requests'] });
      setDescription('');
      setType('CORRECTION');
      onClose();
    },
  });
  const fieldErrors = create.error instanceof ApiError ? create.error.fieldMap() : {};

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (description.trim()) create.mutate();
  };

  return (
    <Dialog open={open} onClose={create.isPending ? undefined : onClose} fullWidth maxWidth="sm" component="form" onSubmit={onSubmit}>
      <DialogTitle>New request</DialogTitle>
      <DialogContent>
        <Stack spacing={2.5} sx={{ pt: 1 }}>
          {create.isError && !Object.keys(fieldErrors).length && <Alert severity="error">{errorMessage(create.error)}</Alert>}
          <FormControl fullWidth>
            <InputLabel id="request-type">Type</InputLabel>
            <Select labelId="request-type" label="Type" value={type} onChange={(e) => setType(e.target.value as RequestType)}>
              {REQUEST_TYPES.map((t) => (
                <MenuItem key={t.value} value={t.value}>
                  {t.label}
                </MenuItem>
              ))}
            </Select>
            <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, ml: 1.75 }}>
              {REQUEST_TYPES.find((t) => t.value === type)?.hint}
            </Typography>
          </FormControl>
          <BookPicker value={book} onChange={setBook} />
          <TextField
            label="Description"
            multiline
            minRows={4}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            required
            error={!!fieldErrors.description}
            helperText={fieldErrors.description ?? `Describe the change, e.g. song number and the line to fix. ${description.length}/${MAX_DESCRIPTION}`}
            slotProps={{ htmlInput: { maxLength: MAX_DESCRIPTION } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={create.isPending}>
          Cancel
        </Button>
        <Button type="submit" variant="contained" disabled={create.isPending || !description.trim()}>
          {create.isPending ? 'Sending…' : 'Send request'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function RequestCard({ r }: { r: ModificationRequest }) {
  const status = STATUS[r.status];
  const typeLabel = REQUEST_TYPES.find((t) => t.value === r.requestType)?.label ?? r.requestType;
  return (
    <Card>
      <CardContent>
        <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap', mb: 1 }}>
          <Chip label={status.label} color={status.color} size="small" />
          <Chip label={typeLabel} size="small" variant="outlined" />
          {r.bookId && r.bookTitle && (
            <Link component={RouterLink} to={`/books/${r.bookId}`} variant="body2" sx={{ fontWeight: 600 }}>
              {r.bookTitle}
            </Link>
          )}
          <Typography variant="body2" color="text.secondary" sx={{ ml: 'auto' }}>
            {formatDate(r.createdAt)}
          </Typography>
        </Stack>
        <Typography sx={{ whiteSpace: 'pre-line', overflowWrap: 'anywhere' }}>{r.description}</Typography>
        {r.reviewNote && (
          <Alert severity={r.status === 'APPROVED' ? 'success' : 'info'} sx={{ mt: 1.5 }}>
            <strong>Admin note{r.reviewedAt ? ` (${formatDate(r.reviewedAt)})` : ''}:</strong> {r.reviewNote}
          </Alert>
        )}
      </CardContent>
    </Card>
  );
}

export default function RequestsPage() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(1, Number.parseInt(params.get('page') ?? '1', 10) || 1);
  const preselectId = params.get('bookId');
  const [open, setOpen] = useState(params.get('new') === '1');

  // Pre-fill the book when arriving from a book page ("Suggest a correction").
  const preselected = useQuery({ queryKey: queryKeys.book(preselectId ?? ''), queryFn: () => catalogueApi.book(preselectId!), enabled: !!preselectId });
  const initialBook = preselected.data ? { id: preselected.data.id, title: preselected.data.title } : null;

  const requests = useQuery({
    queryKey: queryKeys.requests(page),
    queryFn: () => memberApi.requests(page - 1, PAGE_SIZE),
    placeholderData: keepPreviousData,
  });

  const closeDialog = () => {
    setOpen(false);
    if (params.has('new') || params.has('bookId')) {
      const next = new URLSearchParams(params);
      next.delete('new');
      next.delete('bookId');
      setParams(next, { replace: true });
    }
  };

  return (
    <Container maxWidth="md" sx={{ py: 4, px: 2 }}>
      <Breadcrumbs sx={{ mb: 2 }}>
        <Link component={RouterLink} to="/me" underline="hover" color="inherit">
          Dashboard
        </Link>
        <Typography color="text.primary">My requests</Typography>
      </Breadcrumbs>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, mb: 3 }}>
        <Box>
          <Typography variant="h2" component="h1">
            My requests
          </Typography>
          <Typography color="text.secondary">Suggest corrections or additions. An administrator will review them.</Typography>
        </Box>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)} sx={{ flexShrink: 0 }}>
          New request
        </Button>
      </Stack>

      {requests.isError ? (
        <ErrorAlert error={requests.error} onRetry={() => requests.refetch()} />
      ) : requests.isPending ? (
        <Stack spacing={2}>
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} variant="rounded" height={110} />
          ))}
        </Stack>
      ) : requests.data.content.length === 0 ? (
        <EmptyState title="No requests yet">Found a mistake in a song book? Send a request and an administrator will fix it.</EmptyState>
      ) : (
        <>
          <Stack spacing={2} sx={{ opacity: requests.isPlaceholderData ? 0.6 : 1 }}>
            {requests.data.content.map((r) => (
              <RequestCard key={r.id} r={r} />
            ))}
          </Stack>
          {requests.data.totalPages > 1 && (
            <Stack sx={{ alignItems: 'center', mt: 3 }}>
              <Pagination
                count={requests.data.totalPages}
                page={page}
                color="primary"
                onChange={(_, p) => {
                  const next = new URLSearchParams(params);
                  if (p > 1) next.set('page', String(p));
                  else next.delete('page');
                  setParams(next);
                }}
              />
            </Stack>
          )}
        </>
      )}

      <NewRequestDialog open={open} onClose={closeDialog} initialBook={initialBook} />
    </Container>
  );
}
