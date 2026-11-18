import { useEffect, useState, type FormEvent } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Breadcrumbs,
  Button,
  Card,
  CardContent,
  Chip,
  FormControl,
  FormControlLabel,
  FormLabel,
  IconButton,
  InputLabel,
  LinearProgress,
  Link,
  MenuItem,
  Radio,
  RadioGroup,
  Select,
  Skeleton,
  Stack,
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
import DownloadIcon from '@mui/icons-material/Download';
import ArchiveIcon from '@mui/icons-material/Archive';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import { ApiError } from '../../services/api/client';
import { adminApi, adminKeys } from '../../services/api/adminApi';
import type { AdminBookDetail, BookMetadata, BookStatus } from '../../types/admin';
import { Cover } from '../../components/common';
import { formatDate } from '../../utils/format';
import { AdminError, adminErrorMessage, BookChip, ConfirmDialog, formatBytes, PageHeader, todayIso } from './common';
import FilePicker, { checkImage, checkPdf } from './FilePicker';

const EDITABLE_STATUSES: { value: BookStatus; label: string; hint: string }[] = [
  { value: 'DRAFT', label: 'Draft', hint: 'Not visible to anyone but admins.' },
  { value: 'PUBLISHED', label: 'Published', hint: 'Visible in the catalogue between the effective and expiry dates.' },
  { value: 'INACTIVE', label: 'Inactive', hint: 'Temporarily hidden; can be published again.' },
];

function useInvalidateBooks() {
  const queryClient = useQueryClient();
  return (id?: string) => {
    void queryClient.invalidateQueries({ queryKey: ['admin', 'books'] });
    void queryClient.invalidateQueries({ queryKey: adminKeys.stats });
    void queryClient.invalidateQueries({ queryKey: ['books'] }); // public catalogue
    if (id) {
      void queryClient.invalidateQueries({ queryKey: adminKeys.book(id) });
      void queryClient.invalidateQueries({ queryKey: adminKeys.versions(id) });
      void queryClient.invalidateQueries({ queryKey: ['book', id] });
    }
  };
}

// ---------------------------------------------------------------- metadata form (create + edit)

function MetadataForm({ book }: { book?: AdminBookDetail }) {
  const navigate = useNavigate();
  const invalidate = useInvalidateBooks();
  const categories = useQuery({ queryKey: adminKeys.categories, queryFn: adminApi.categories });
  const archived = book?.status === 'ARCHIVED';

  const [title, setTitle] = useState(book?.title ?? '');
  const [description, setDescription] = useState(book?.description ?? '');
  const [categoryId, setCategoryId] = useState(book?.category?.id ?? '');
  const [effectiveDate, setEffectiveDate] = useState(book?.effectiveDate ?? todayIso());
  const [expiryDate, setExpiryDate] = useState(book?.expiryDate ?? '');
  const [status, setStatus] = useState<BookStatus>(book?.status ?? 'DRAFT');
  const [pdf, setPdf] = useState<File | null>(null);
  const [cover, setCover] = useState<File | null>(null);
  const [progress, setProgress] = useState<number | null>(null);

  useEffect(() => {
    if (!book) return;
    setTitle(book.title);
    setDescription(book.description ?? '');
    setCategoryId(book.category?.id ?? '');
    setEffectiveDate(book.effectiveDate);
    setExpiryDate(book.expiryDate ?? '');
    setStatus(book.status);
  }, [book]);

  const metadata = (): BookMetadata => ({
    title: title.trim(),
    description: description.trim() || null,
    categoryId: categoryId || null,
    effectiveDate,
    expiryDate: expiryDate || null,
    status,
  });

  const save = useMutation({
    mutationFn: async () => {
      if (book) return adminApi.updateBook(book.id, { ...metadata(), version: book.version });
      setProgress(0);
      return adminApi.createBook(metadata(), pdf!, cover, { onProgress: setProgress });
    },
    onSuccess: (saved) => {
      invalidate(saved.id);
      if (!book) navigate(`/admin/books/${saved.id}`, { replace: true });
    },
    onSettled: () => setProgress(null),
  });

  const pdfError = checkPdf(pdf);
  const coverError = checkImage(cover);
  const badRange = !!expiryDate && expiryDate < effectiveDate;
  const fields = save.error instanceof ApiError ? save.error.fieldMap() : {};
  const canSave = !archived && title.trim() && effectiveDate && !badRange && (book || (pdf && !pdfError)) && !coverError && !save.isPending;

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (canSave) save.mutate();
  };

  const activeCategories = (categories.data ?? []).filter((c) => c.active || c.id === categoryId);

  return (
    <Card>
      <CardContent>
        <Typography variant="h3" component="h2" sx={{ mb: 2 }}>{book ? 'Details' : 'Book details'}</Typography>
        <Box component="form" onSubmit={onSubmit} noValidate>
          <Stack spacing={2.5}>
            {archived && <Alert severity="info">This book is archived. Archived books cannot be edited and never appear in the catalogue.</Alert>}
            {save.isError && <Alert severity="error">{adminErrorMessage(save.error)}</Alert>}
            {save.isSuccess && book && <Alert severity="success">Saved.</Alert>}
            <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required disabled={archived}
              error={!!fields.title} helperText={fields.title ?? ' '} slotProps={{ htmlInput: { maxLength: 200 } }} />
            <FormControl fullWidth disabled={archived}>
              <InputLabel id="book-cat">Category</InputLabel>
              <Select labelId="book-cat" label="Category" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
                <MenuItem value="">None</MenuItem>
                {activeCategories.map((c) => <MenuItem key={c.id} value={c.id}>{c.name}{c.active ? '' : ' (inactive)'}</MenuItem>)}
              </Select>
            </FormControl>
            <TextField label="Description" multiline minRows={3} value={description} onChange={(e) => setDescription(e.target.value)} disabled={archived}
              helperText={fields.description ?? `${description.length}/10000`} error={!!fields.description} slotProps={{ htmlInput: { maxLength: 10000 } }} />

            {!book && (
              <>
                <FilePicker label="PDF" required accept="application/pdf,.pdf" file={pdf} onChange={setPdf} error={pdfError} hint="Max 100 MB." disabled={save.isPending} />
                <FilePicker label="Cover image" accept="image/jpeg,image/png,image/webp" file={cover} onChange={setCover} error={coverError}
                  hint="Optional. JPEG, PNG or WebP, max 5 MB. A placeholder is shown when there is no cover." disabled={save.isPending} />
              </>
            )}

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField label="Effective date" type="date" value={effectiveDate} onChange={(e) => setEffectiveDate(e.target.value)} required disabled={archived}
                helperText={fields.effectiveDate ?? 'First day it can appear in the catalogue.'} error={!!fields.effectiveDate} slotProps={{ inputLabel: { shrink: true } }} />
              <TextField label="Expiry date" type="date" value={expiryDate} onChange={(e) => setExpiryDate(e.target.value)} disabled={archived}
                error={badRange || !!fields.expiryDate} helperText={badRange ? 'Must be on or after the effective date.' : (fields.expiryDate ?? 'Leave empty for "No expiry".')}
                slotProps={{ inputLabel: { shrink: true } }} />
            </Stack>

            {!archived && (
              <FormControl>
                <FormLabel id="book-status">Status</FormLabel>
                <RadioGroup row aria-labelledby="book-status" value={status} onChange={(e) => setStatus(e.target.value as BookStatus)}>
                  {EDITABLE_STATUSES.map((s) => <FormControlLabel key={s.value} value={s.value} control={<Radio />} label={s.label} />)}
                </RadioGroup>
                <Typography variant="caption" color="text.secondary">{EDITABLE_STATUSES.find((s) => s.value === status)?.hint}</Typography>
              </FormControl>
            )}

            {progress !== null && (
              <Box>
                <LinearProgress variant="determinate" value={progress * 100} />
                <Typography variant="caption" color="text.secondary">
                  {progress < 1 ? `Uploading… ${Math.round(progress * 100)}%` : 'Checking the file…'}
                </Typography>
              </Box>
            )}

            {!archived && (
              <Stack direction="row" spacing={1}>
                <Button type="submit" variant="contained" disabled={!canSave}>
                  {save.isPending ? (book ? 'Saving…' : 'Uploading…') : book ? 'Save details' : 'Create book'}
                </Button>
                {!book && <Button component={RouterLink} to="/admin/books" disabled={save.isPending}>Cancel</Button>}
              </Stack>
            )}
          </Stack>
        </Box>
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------- cover (edit)

function CoverCard({ book }: { book: AdminBookDetail }) {
  const invalidate = useInvalidateBooks();
  const [file, setFile] = useState<File | null>(null);
  const replace = useMutation({
    mutationFn: () => adminApi.replaceCover(book.id, file!),
    onSuccess: () => {
      setFile(null);
      invalidate(book.id);
    },
  });
  const error = checkImage(file);
  // The backend versions the cover URL (?v=<asset id>), so a replaced cover shows immediately.
  const url = book.coverUrl ?? null;
  return (
    <Card>
      <CardContent>
        <Typography variant="h3" component="h2" sx={{ mb: 2 }}>Cover</Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <Box sx={{ width: 120, flexShrink: 0, borderRadius: 1, overflow: 'hidden', border: 1, borderColor: 'divider' }}>
            <Cover url={url} title={book.title} />
          </Box>
          <Stack spacing={1} sx={{ minWidth: 0 }}>
            {replace.isError && <Alert severity="error">{adminErrorMessage(replace.error)}</Alert>}
            {replace.isSuccess && <Alert severity="success">Cover replaced.</Alert>}
            <FilePicker label={book.coverUrl ? 'Replace cover' : 'Add cover'} accept="image/jpeg,image/png,image/webp" file={file} onChange={setFile}
              error={error} hint="JPEG, PNG or WebP, max 5 MB." disabled={replace.isPending || book.status === 'ARCHIVED'} />
            <Box>
              <Button variant="contained" disabled={!file || !!error || replace.isPending} onClick={() => replace.mutate()}>
                {replace.isPending ? 'Uploading…' : 'Upload cover'}
              </Button>
            </Box>
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------- versions (edit)

function VersionsCard({ book }: { book: AdminBookDetail }) {
  const invalidate = useInvalidateBooks();
  const versions = useQuery({ queryKey: adminKeys.versions(book.id), queryFn: () => adminApi.versions(book.id) });
  const [pdf, setPdf] = useState<File | null>(null);
  const [note, setNote] = useState('');
  const [progress, setProgress] = useState<number | null>(null);
  const [downloadError, setDownloadError] = useState<unknown>(null);

  const upload = useMutation({
    mutationFn: () => {
      setProgress(0);
      return adminApi.addVersion(book.id, pdf!, note.trim(), { onProgress: setProgress });
    },
    onSuccess: () => {
      setPdf(null);
      setNote('');
      invalidate(book.id);
    },
    onSettled: () => setProgress(null),
  });
  const pdfError = checkPdf(pdf);
  const archived = book.status === 'ARCHIVED';

  return (
    <Card>
      <CardContent>
        <Typography variant="h3" component="h2" sx={{ mb: 0.5 }}>PDF versions</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Uploading a new version makes it current. Previous versions are kept and can be downloaded here.
        </Typography>
        {downloadError != null && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setDownloadError(null)}>{adminErrorMessage(downloadError)}</Alert>}
        {versions.isError ? (
          <AdminError error={versions.error} onRetry={() => versions.refetch()} />
        ) : versions.isPending ? (
          <Skeleton variant="rounded" height={80} />
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Version</TableCell>
                  <TableCell>File</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Pages</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Uploaded</TableCell>
                  <TableCell sx={{ display: { xs: 'none', lg: 'table-cell' } }}>Note</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {versions.data.map((v) => (
                  <TableRow key={v.id}>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>
                      v{v.versionNumber} {v.current && <Chip size="small" color="primary" label="Current" sx={{ ml: 0.5 }} />}
                    </TableCell>
                    <TableCell sx={{ overflowWrap: 'anywhere' }}>
                      {v.originalFilename}
                      <Typography variant="caption" color="text.secondary" component="div" title={`SHA-256 ${v.checksumSha256}`}>{formatBytes(v.fileSize)}</Typography>
                    </TableCell>
                    <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{v.pageCount ?? '—'}</TableCell>
                    <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{formatDate(v.createdAt)}</TableCell>
                    <TableCell sx={{ display: { xs: 'none', lg: 'table-cell' } }}>{v.changeNote}</TableCell>
                    <TableCell align="right">
                      <Tooltip title="Download this version">
                        <IconButton size="small" aria-label={`Download version ${v.versionNumber}`}
                          onClick={() => adminApi.downloadVersion(book.id, v).catch(setDownloadError)}>
                          <DownloadIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}

        {!archived && (
          <Stack spacing={1.5} sx={{ mt: 3 }}>
            <Typography sx={{ fontWeight: 600 }}>Upload a new version</Typography>
            {upload.isError && <Alert severity="error">{adminErrorMessage(upload.error)}</Alert>}
            {upload.isSuccess && <Alert severity="success">New version uploaded and now current. Members with the book open will be asked to reopen it.</Alert>}
            <FilePicker label="PDF" accept="application/pdf,.pdf" file={pdf} onChange={setPdf} error={pdfError} hint="Max 100 MB." disabled={upload.isPending} />
            <TextField size="small" label="Change note (optional)" value={note} onChange={(e) => setNote(e.target.value)} disabled={upload.isPending}
              placeholder="e.g. Fixed typos in songs 12 and 40" slotProps={{ htmlInput: { maxLength: 1000 } }} />
            {progress !== null && <LinearProgress variant="determinate" value={progress * 100} aria-label="Upload progress" />}
            <Box>
              <Button variant="contained" disabled={!pdf || !!pdfError || upload.isPending} onClick={() => upload.mutate()}>
                {upload.isPending ? (progress !== null && progress < 1 ? `Uploading ${Math.round(progress * 100)}%` : 'Checking…') : 'Upload version'}
              </Button>
            </Box>
          </Stack>
        )}
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------- page

function ArchiveButton({ book }: { book: AdminBookDetail }) {
  const invalidate = useInvalidateBooks();
  const [open, setOpen] = useState(false);
  const archive = useMutation({
    mutationFn: () => adminApi.archiveBook(book.id),
    onSuccess: () => {
      setOpen(false);
      invalidate(book.id);
    },
  });
  if (book.status === 'ARCHIVED') return null;
  return (
    <>
      <Button color="error" variant="outlined" startIcon={<ArchiveIcon />} onClick={() => setOpen(true)}>Archive</Button>
      <ConfirmDialog
        open={open}
        title="Archive this book?"
        body={<>“{book.title}” will be removed from the catalogue and members will no longer be able to read or download it. Its files and history are kept. This cannot be undone from the admin UI.</>}
        confirmLabel="Archive"
        danger
        busy={archive.isPending}
        error={archive.error}
        onConfirm={() => archive.mutate()}
        onClose={() => { setOpen(false); archive.reset(); }}
      />
    </>
  );
}

export default function BookFormPage() {
  const { id } = useParams();
  const book = useQuery({ queryKey: adminKeys.book(id ?? ''), queryFn: () => adminApi.book(id!), enabled: !!id });
  const b = book.data;

  return (
    <>
      <Breadcrumbs sx={{ mb: 2 }}>
        <Link component={RouterLink} to="/admin/books" underline="hover" color="inherit">Books</Link>
        <Typography color="text.primary" noWrap>{id ? (b?.title ?? '…') : 'New book'}</Typography>
      </Breadcrumbs>

      {!id ? (
        <>
          <PageHeader title="New book" subtitle="Upload the PDF and set when the book should appear in the catalogue." />
          <Box sx={{ maxWidth: 760 }}><MetadataForm /></Box>
        </>
      ) : book.isError ? (
        <AdminError error={book.error} onRetry={() => book.refetch()} />
      ) : !b ? (
        <Skeleton variant="rounded" height={400} />
      ) : (
        <>
          <PageHeader
            title={b.title}
            subtitle={
              <>
                {b.availableToday ? 'Visible in the public catalogue today.' : 'Not visible in the public catalogue today.'} Updated {formatDate(b.updatedAt)}.
              </>
            }
            actions={
              <>
                <BookChip status={b.status} />
                {b.availableToday && (
                  <Button component={RouterLink} to={`/books/${b.id}`} target="_blank" rel="noopener" startIcon={<OpenInNewIcon />}>Public page</Button>
                )}
                <ArchiveButton book={b} />
              </>
            }
          />
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'minmax(0, 3fr) minmax(0, 2fr)' }, alignItems: 'start' }}>
            <MetadataForm key={b.version} book={b} />
            <CoverCard book={b} />
          </Box>
          <Box sx={{ mt: 2 }}>
            <VersionsCard book={b} />
          </Box>
        </>
      )}
    </>
  );
}
