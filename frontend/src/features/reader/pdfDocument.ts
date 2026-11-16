import { getDocument, GlobalWorkerOptions, PDFDataRangeTransport, type PDFDocumentProxy } from 'pdfjs-dist';
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';
import type { TicketedRangeLoader } from './ticketedRangeLoader';

GlobalWorkerOptions.workerSrc = workerUrl;

/** Bytes fetched per range request, and the size of the first probe. */
const CHUNK = 64 * 1024;

export interface OpenedPdf {
  doc: PDFDocumentProxy;
  /** Stops all loading; safe to call more than once. */
  destroy: () => void;
}

/**
 * Opens a PDF through PDF.js using ranged, ticket-authorised requests: the first chunk tells us
 * the file size, then PDF.js asks for only the ranges it needs as pages are viewed.
 * `onError` receives failures from later range requests (expired eligibility, version change),
 * which PDF.js itself would otherwise just wait on forever.
 */
export async function openPdf(loader: TicketedRangeLoader, onError: (e: unknown) => void): Promise<OpenedPdf> {
  const abort = new AbortController();
  const first = await loader.fetchRange(0, CHUNK, abort.signal);

  const transport = new PDFDataRangeTransport(first.total, first.data);
  transport.requestDataRange = (begin: number, end: number) => {
    loader
      .fetchRange(begin, end, abort.signal)
      .then((r) => transport.onDataRange(begin, r.data))
      .catch((e: unknown) => {
        if (!abort.signal.aborted) onError(e);
      });
  };

  const task = getDocument({
    range: transport,
    rangeChunkSize: CHUNK,
    disableAutoFetch: true, // fetch pages on demand only
  });

  let destroyed = false;
  const destroy = () => {
    if (destroyed) return;
    destroyed = true;
    abort.abort();
    void task.destroy();
  };

  try {
    return { doc: await task.promise, destroy };
  } catch (e) {
    destroy();
    throw e;
  }
}

/** True for PDF.js's "render was cancelled" rejection, which is expected when pages change fast. */
export function isRenderCancelled(e: unknown): boolean {
  return e instanceof Error && e.name === 'RenderingCancelledException';
}
