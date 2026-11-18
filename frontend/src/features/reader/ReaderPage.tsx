import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type KeyboardEvent as ReactKeyboardEvent, type TouchEvent } from 'react';
import { Link as RouterLink, useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Collapse,
  Divider,
  IconButton,
  InputAdornment,
  Paper,
  Snackbar,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import NavigateBeforeIcon from '@mui/icons-material/NavigateBefore';
import NavigateNextIcon from '@mui/icons-material/NavigateNext';
import ZoomInIcon from '@mui/icons-material/ZoomIn';
import ZoomOutIcon from '@mui/icons-material/ZoomOut';
import FitScreenIcon from '@mui/icons-material/FitScreen';
import SearchIcon from '@mui/icons-material/Search';
import CloseIcon from '@mui/icons-material/Close';
import FullscreenIcon from '@mui/icons-material/Fullscreen';
import FullscreenExitIcon from '@mui/icons-material/FullscreenExit';
import DownloadIcon from '@mui/icons-material/Download';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import type { PDFDocumentProxy } from 'pdfjs-dist';
import { ApiError, errorMessage } from '../../services/api/client';
import { catalogueApi, queryKeys, readerApi } from '../../services/api/endpoints';
import { openPdf, type OpenedPdf } from './pdfDocument';
import { TicketedRangeLoader, VersionChangedError } from './ticketedRangeLoader';
import PdfPageView from './PdfPageView';
import { usePdfSearch } from './usePdfSearch';
import { usePdfDownload } from './usePdfDownload';

const ZOOM_STEPS = [0.5, 0.75, 1, 1.25, 1.5, 2, 2.5, 3];
const SAVE_DELAY_MS = 1500;
const PROGRESS_WAIT_MS = 1500;
const clamp = (n: number, min: number, max: number) => Math.min(max, Math.max(min, n));

/** PDF.js reader for /read/:id (docs/UI_FLOWS.md "Reader" wireframe, brief §7). */
export default function ReaderPage() {
  const { id = '' } = useParams();
  const book = useQuery({ queryKey: queryKeys.book(id), queryFn: () => catalogueApi.book(id) });
  const download = usePdfDownload(id);

  // ------------------------------------------------------------ document loading
  const [reloadKey, setReloadKey] = useState(0);
  const [doc, setDoc] = useState<PDFDocumentProxy | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [page, setPage] = useState(1);
  const savedPage = useRef(1);
  /** Set once the reader turns a page, so late-arriving saved progress never yanks them back. */
  const navigated = useRef(false);

  useEffect(() => {
    let cancelled = false;
    let opened: OpenedPdf | null = null;
    setDoc(null);
    setError(null);
    navigated.current = false;
    const loader = new TicketedRangeLoader({ requestGrant: () => readerApi.readAccess(id) });
    // Saved progress is a convenience and must never delay the book: use it if it arrives within
    // PROGRESS_WAIT_MS of the PDF being ready, otherwise open at page 1 and jump when it lands
    // (unless the reader has already moved).
    const progress = readerApi.progress(id).catch(() => null);

    (async () => {
      try {
        const pdf = await openPdf(loader, (e) => !cancelled && setError(e));
        if (cancelled) return pdf.destroy();
        opened = pdf;
        const total = pdf.doc.numPages;
        const early = await Promise.race([progress, new Promise<undefined>((r) => setTimeout(r, PROGRESS_WAIT_MS))]);
        if (cancelled) return;
        const start = clamp(early?.lastPage ?? 1, 1, total);
        savedPage.current = start;
        setPage(start);
        setDoc(pdf.doc);
        if (early === undefined) {
          const late = await progress;
          if (!cancelled && late && !navigated.current) {
            const p = clamp(late.lastPage, 1, total);
            savedPage.current = p;
            setPage(p);
          }
        }
      } catch (e) {
        if (!cancelled) setError(e);
      }
    })();

    return () => {
      cancelled = true;
      opened?.destroy();
    };
  }, [id, reloadKey]);

  const numPages = doc?.numPages ?? 0;
  const goTo = useCallback(
    (p: number) => {
      if (!numPages) return;
      navigated.current = true;
      setPage(clamp(p, 1, numPages));
    },
    [numPages],
  );

  // ------------------------------------------------------------ progress (debounced save)
  useEffect(() => {
    if (!doc || page === savedPage.current) return;
    const timer = setTimeout(() => {
      readerApi.saveProgress(id, page).then(
        () => (savedPage.current = page),
        () => undefined, // best effort; retried on the next page change
      );
    }, SAVE_DELAY_MS);
    return () => clearTimeout(timer);
  }, [doc, id, page]);

  // Flush the last page when leaving the reader.
  const latest = useRef({ id, page, doc });
  latest.current = { id, page, doc };
  useEffect(
    () => () => {
      const { id: bookId, page: p, doc: d } = latest.current;
      if (d && p !== savedPage.current) void readerApi.saveProgress(bookId, p).catch(() => undefined);
    },
    [],
  );

  // ------------------------------------------------------------ zoom / fit width
  const scrollRef = useRef<HTMLDivElement>(null);
  const [containerWidth, setContainerWidth] = useState(0);
  const [baseWidth, setBaseWidth] = useState(0); // page width at scale 1
  const [zoom, setZoom] = useState<'fit' | number>('fit');

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const ro = new ResizeObserver(([entry]) => setContainerWidth(entry!.contentRect.width));
    ro.observe(el);
    return () => ro.disconnect();
  }, [doc]);

  useEffect(() => {
    if (!doc) return;
    let live = true;
    doc.getPage(page).then((p) => live && setBaseWidth(p.getViewport({ scale: 1 }).width), () => undefined);
    return () => {
      live = false;
    };
  }, [doc, page]);

  const fitScale = baseWidth && containerWidth ? clamp((containerWidth - 32) / baseWidth, 0.25, 4) : 1;
  const scale = zoom === 'fit' ? fitScale : zoom;
  const zoomIn = () => setZoom(ZOOM_STEPS.find((s) => s > scale + 0.01) ?? ZOOM_STEPS.at(-1)!);
  const zoomOut = () => setZoom([...ZOOM_STEPS].reverse().find((s) => s < scale - 0.01) ?? ZOOM_STEPS[0]!);

  // ------------------------------------------------------------ search
  const search = usePdfSearch(doc);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchText, setSearchText] = useState('');
  const matchPos = useMemo(() => search.matches.findIndex((m) => m.page === page), [search.matches, page]);
  const totalHits = search.matches.reduce((n, m) => n + m.count, 0);

  const jumpMatch = (dir: 1 | -1) => {
    if (!search.matches.length) return;
    const next =
      dir === 1 ? search.matches.find((m) => m.page > page) ?? search.matches[0] : [...search.matches].reverse().find((m) => m.page < page) ?? search.matches.at(-1);
    if (next) goTo(next.page);
  };

  const submitSearch = async (e: FormEvent) => {
    e.preventDefault();
    await search.search(searchText);
  };
  // Jump to the first result once it arrives, unless the current page already matches.
  const jumpedFor = useRef('');
  useEffect(() => {
    const first = search.matches[0];
    if (!first || jumpedFor.current === search.query) return;
    jumpedFor.current = search.query;
    if (!search.matches.some((m) => m.page === page)) goTo(first.page);
  }, [search.matches, search.query, page, goTo]);

  const closeSearch = () => {
    setSearchOpen(false);
    setSearchText('');
    jumpedFor.current = '';
    search.clear();
  };

  // ------------------------------------------------------------ fullscreen
  const rootRef = useRef<HTMLDivElement>(null);
  const [fullscreen, setFullscreen] = useState(false);
  useEffect(() => {
    const onChange = () => setFullscreen(document.fullscreenElement === rootRef.current);
    document.addEventListener('fullscreenchange', onChange);
    return () => document.removeEventListener('fullscreenchange', onChange);
  }, []);
  const toggleFullscreen = () => {
    if (document.fullscreenElement) void document.exitFullscreen();
    else void rootRef.current?.requestFullscreen?.();
  };

  // ------------------------------------------------------------ keyboard + swipe
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement | null;
      if (t?.closest('input, textarea, [contenteditable="true"]') || e.altKey || e.ctrlKey || e.metaKey) return;
      if (e.key === 'ArrowRight' || e.key === 'PageDown') goTo(page + 1);
      else if (e.key === 'ArrowLeft' || e.key === 'PageUp') goTo(page - 1);
      else if (e.key === 'Home') goTo(1);
      else if (e.key === 'End') goTo(numPages);
      else if (e.key === '+' || e.key === '=') zoomIn();
      else if (e.key === '-') zoomOut();
      else return;
      e.preventDefault();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  const touch = useRef<{ x: number; y: number } | null>(null);
  const onTouchStart = (e: TouchEvent) => {
    const t = e.touches[0];
    touch.current = e.touches.length === 1 && t ? { x: t.clientX, y: t.clientY } : null;
  };
  const onTouchEnd = (e: TouchEvent) => {
    const start = touch.current;
    const t = e.changedTouches[0];
    touch.current = null;
    if (!start || !t || scale > fitScale * 1.05) return; // when zoomed in, swipes pan instead
    const dx = t.clientX - start.x;
    if (Math.abs(dx) > 60 && Math.abs(t.clientY - start.y) < 50) goTo(page + (dx < 0 ? 1 : -1));
  };

  // ------------------------------------------------------------ render
  const title = book.data?.title ?? 'Reader';

  if (error) {
    return <ReaderError error={error} bookId={id} onRetry={() => setReloadKey((k) => k + 1)} />;
  }

  return (
    <Box
      ref={rootRef}
      sx={{
        display: 'flex',
        flexDirection: 'column',
        height: fullscreen ? '100dvh' : { xs: 'calc(100dvh - 56px)', sm: 'calc(100dvh - 64px)' },
        bgcolor: 'background.default',
      }}
    >
      {/* Top toolbar */}
      <Paper square elevation={0} sx={{ borderBottom: 1, borderColor: 'divider', px: 1, py: 0.5 }}>
        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', minWidth: 0 }}>
          <Tooltip title="Back to book">
            <IconButton component={RouterLink} to={`/books/${id}`} aria-label="Back to book">
              <ArrowBackIcon />
            </IconButton>
          </Tooltip>
          <Typography noWrap sx={{ fontWeight: 600, flex: 1, minWidth: 0 }} title={title} component="h1">
            {title}
          </Typography>

          <Box sx={{ display: { xs: 'none', md: 'flex' }, alignItems: 'center' }}>
            <PageNav page={page} numPages={numPages} goTo={goTo} />
            <Divider orientation="vertical" flexItem sx={{ mx: 1 }} />
          </Box>

          <Box sx={{ display: { xs: 'none', sm: 'flex' }, alignItems: 'center' }}>
            <Tooltip title="Zoom out (−)">
              <span>
                <IconButton onClick={zoomOut} disabled={!doc || scale <= ZOOM_STEPS[0]!} aria-label="Zoom out">
                  <ZoomOutIcon />
                </IconButton>
              </span>
            </Tooltip>
            <Typography variant="body2" sx={{ width: 48, textAlign: 'center' }} aria-live="polite">
              {Math.round(scale * 100)}%
            </Typography>
            <Tooltip title="Zoom in (+)">
              <span>
                <IconButton onClick={zoomIn} disabled={!doc || scale >= ZOOM_STEPS.at(-1)!} aria-label="Zoom in">
                  <ZoomInIcon />
                </IconButton>
              </span>
            </Tooltip>
            <Tooltip title="Fit width">
              <span>
                <IconButton onClick={() => setZoom('fit')} disabled={!doc} aria-label="Fit width" color={zoom === 'fit' ? 'primary' : 'default'}>
                  <FitScreenIcon />
                </IconButton>
              </span>
            </Tooltip>
          </Box>

          <Tooltip title="Search">
            <span>
              <IconButton onClick={() => (searchOpen ? closeSearch() : setSearchOpen(true))} disabled={!doc} aria-label="Search in book" color={searchOpen ? 'primary' : 'default'}>
                <SearchIcon />
              </IconButton>
            </span>
          </Tooltip>
          <Tooltip title={fullscreen ? 'Exit full screen' : 'Full screen'}>
            <IconButton onClick={toggleFullscreen} aria-label={fullscreen ? 'Exit full screen' : 'Full screen'}>
              {fullscreen ? <FullscreenExitIcon /> : <FullscreenIcon />}
            </IconButton>
          </Tooltip>
          <Tooltip title="Download PDF">
            <span>
              <IconButton onClick={() => download.mutate()} disabled={download.isPending} aria-label="Download PDF">
                {download.isPending ? <CircularProgress size={20} /> : <DownloadIcon />}
              </IconButton>
            </span>
          </Tooltip>
        </Stack>

        <Collapse in={searchOpen} unmountOnExit>
          <Stack
            component="form"
            onSubmit={submitSearch}
            direction="row"
            spacing={1}
            sx={{ alignItems: 'center', py: 1, px: 1, flexWrap: 'wrap', rowGap: 1 }}
          >
            <TextField
              size="small"
              autoFocus
              placeholder="Search in this book"
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              onKeyDown={(e: ReactKeyboardEvent) => e.key === 'Escape' && closeSearch()}
              sx={{ maxWidth: 320 }}
              slotProps={{
                htmlInput: { 'aria-label': 'Search text' },
                input: {
                  endAdornment: search.searching ? (
                    <InputAdornment position="end">
                      <CircularProgress size={16} />
                    </InputAdornment>
                  ) : undefined,
                },
              }}
            />
            <Button type="submit" variant="contained" size="small" disabled={!searchText.trim()}>
              Find
            </Button>
            <IconButton size="small" onClick={() => jumpMatch(-1)} disabled={!search.matches.length} aria-label="Previous match">
              <KeyboardArrowUpIcon />
            </IconButton>
            <IconButton size="small" onClick={() => jumpMatch(1)} disabled={!search.matches.length} aria-label="Next match">
              <KeyboardArrowDownIcon />
            </IconButton>
            <Typography variant="body2" color="text.secondary" aria-live="polite">
              {search.query &&
                (search.searching && !search.matches.length
                  ? `Searching… page ${search.scanned} of ${numPages}`
                  : search.matches.length
                    ? `${totalHits} match${totalHits === 1 ? '' : 'es'} on ${search.matches.length} page${search.matches.length === 1 ? '' : 's'}` +
                      (matchPos >= 0 ? ` · page ${matchPos + 1} of ${search.matches.length}` : '')
                    : search.searching
                      ? ''
                      : 'No matches')}
            </Typography>
            <IconButton size="small" onClick={closeSearch} aria-label="Close search" sx={{ ml: 'auto' }}>
              <CloseIcon fontSize="small" />
            </IconButton>
          </Stack>
        </Collapse>
      </Paper>

      {/* Page area */}
      <Box
        ref={scrollRef}
        onTouchStart={onTouchStart}
        onTouchEnd={onTouchEnd}
        // Focusable so keyboard users can scroll a zoomed page with the arrow keys (WCAG 2.1.1).
        tabIndex={0}
        role="region"
        aria-label={`${title}, page ${page}${numPages ? ` of ${numPages}` : ''}`}
        sx={{
          flex: 1,
          overflow: 'auto',
          bgcolor: 'action.hover',
          py: 2,
          px: 2,
          display: 'flex',
          '&:focus-visible': { outline: 2, outlineColor: 'primary.main', outlineOffset: -2 },
        }}
      >
        {doc ? (
          <Box sx={{ m: 'auto' }}>
            <PdfPageView doc={doc} pageNumber={page} scale={scale} query={search.query} onError={setError} />
          </Box>
        ) : (
          <Stack sx={{ m: 'auto', alignItems: 'center' }} spacing={2}>
            <CircularProgress aria-label="Loading book" />
            <Typography color="text.secondary">Opening book…</Typography>
          </Stack>
        )}
      </Box>

      {/* Mobile bottom bar */}
      <Paper square elevation={0} sx={{ display: { xs: 'flex', md: 'none' }, justifyContent: 'center', borderTop: 1, borderColor: 'divider', py: 0.5 }}>
        <PageNav page={page} numPages={numPages} goTo={goTo} />
      </Paper>

      <Snackbar
        open={download.isError}
        autoHideDuration={6000}
        onClose={() => download.reset()}
        message={download.isError ? `Download failed: ${errorMessage(download.error)}` : ''}
      />
    </Box>
  );
}

function PageNav({ page, numPages, goTo }: { page: number; numPages: number; goTo: (p: number) => void }) {
  const [draft, setDraft] = useState(String(page));
  useEffect(() => setDraft(String(page)), [page]);
  const commit = () => {
    const n = Number.parseInt(draft, 10);
    if (Number.isFinite(n)) goTo(n);
    else setDraft(String(page));
  };
  return (
    <Stack direction="row" sx={{ alignItems: 'center' }}>
      <Tooltip title="Previous page (←)">
        <span>
          <IconButton onClick={() => goTo(page - 1)} disabled={!numPages || page <= 1} aria-label="Previous page">
            <NavigateBeforeIcon />
          </IconButton>
        </span>
      </Tooltip>
      <TextField
        size="small"
        value={numPages ? draft : ''}
        onChange={(e) => setDraft(e.target.value.replace(/\D/g, ''))}
        onBlur={commit}
        onKeyDown={(e) => e.key === 'Enter' && commit()}
        disabled={!numPages}
        sx={{ width: 64 }}
        slotProps={{ htmlInput: { inputMode: 'numeric', 'aria-label': 'Page number', style: { textAlign: 'center', padding: '6px 4px' } } }}
      />
      <Typography variant="body2" sx={{ mx: 1, whiteSpace: 'nowrap' }} color="text.secondary">
        / {numPages || '–'}
      </Typography>
      <Tooltip title="Next page (→)">
        <span>
          <IconButton onClick={() => goTo(page + 1)} disabled={!numPages || page >= numPages} aria-label="Next page">
            <NavigateNextIcon />
          </IconButton>
        </span>
      </Tooltip>
    </Stack>
  );
}

const ELIGIBILITY: Record<string, { title: string; body: string; action?: { label: string; to: string } }> = {
  MOBILE_NOT_VERIFIED: {
    title: 'Confirm your mobile number to read',
    body: 'Request an SMS code from your dashboard and enter it to confirm your number.',
    action: { label: 'Go to my dashboard', to: '/me' },
  },
  MEMBERSHIP_INVALID: {
    title: 'Your membership does not allow reading',
    body: 'Your membership has expired, is suspended or has not started yet. Please contact an administrator.',
    action: { label: 'View my membership', to: '/me' },
  },
  ACCOUNT_INACTIVE: {
    title: 'Your account is not active',
    body: 'Please contact an administrator.',
  },
};

function ReaderError({ error, bookId, onRetry }: { error: unknown; bookId: string; onRetry: () => void }) {
  const back = (
    <Button component={RouterLink} to={`/books/${bookId}`} variant="outlined">
      Back to book
    </Button>
  );
  let content;
  if (error instanceof VersionChangedError) {
    content = (
      <Alert severity="info" action={<Button onClick={onRetry}>Reopen</Button>}>
        This book was updated while you were reading. Reopen it to continue with the new version.
      </Alert>
    );
  } else if (error instanceof ApiError && ELIGIBILITY[error.code]) {
    const info = ELIGIBILITY[error.code]!;
    content = (
      <>
        <Alert severity="warning">
          <strong>{info.title}.</strong> {info.body}
        </Alert>
        {info.action && (
          <Button component={RouterLink} to={info.action.to} variant="contained">
            {info.action.label}
          </Button>
        )}
      </>
    );
  } else if (error instanceof ApiError && error.status === 404) {
    content = <Alert severity="info">This book is not available to read.</Alert>;
  } else {
    content = (
      <Alert severity="error" action={<Button color="inherit" onClick={onRetry}>Retry</Button>}>
        {error instanceof ApiError ? errorMessage(error) : 'The book could not be opened. Please try again.'}
      </Alert>
    );
  }
  return (
    <Box sx={{ maxWidth: 640, mx: 'auto', px: 2, py: 6 }}>
      <Stack spacing={2} sx={{ alignItems: 'flex-start' }}>
        {content}
        {back}
      </Stack>
    </Box>
  );
}
