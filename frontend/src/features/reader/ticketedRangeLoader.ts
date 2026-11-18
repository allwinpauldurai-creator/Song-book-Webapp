import { ApiError, apiUrl } from '../../services/api/client';
import type { AccessTicket, ApiErrorBody } from '../../types/api';

/**
 * Loads byte ranges of a book PDF through the ticket-gated content endpoint.
 *
 * Read tickets live only ~60 s and the backend re-checks expiry on every request
 * (docs/SECURITY.md §5), while PDF.js keeps requesting ranges for as long as the reader is open.
 * So this loader renews the ticket before it gets old and, if a request still comes back
 * INVALID_TICKET (clock skew, a slow network), renews once and retries that range.
 *
 * Freshness is tracked with the local clock from when the ticket was received rather than the
 * server's `expiresAt`, so a skewed client clock cannot cause renew loops or stale tickets.
 */

/** The book got a new PDF version while it was open; the reader must reopen it. */
export class VersionChangedError extends Error {
  constructor() {
    super('A newer version of this book is available.');
    this.name = 'VersionChangedError';
  }
}

export interface RangeResult {
  data: Uint8Array;
  /** Total file size in bytes. */
  total: number;
}

export interface LoaderOptions {
  /** Requests a new read ticket (GET /books/{id}/read-access). */
  requestGrant: () => Promise<AccessTicket>;
  fetchImpl?: typeof fetch;
  now?: () => number;
  /** Treat a ticket as fresh for this long after receiving it. Default 40 s (TTL is 60 s). */
  freshForMs?: number;
}

export class TicketedRangeLoader {
  private grant: AccessTicket | null = null;
  private receivedAt = 0;
  private renewing: Promise<AccessTicket> | null = null;
  private readonly fetchImpl: typeof fetch;
  private readonly now: () => number;
  private readonly freshForMs: number;

  constructor(private readonly options: LoaderOptions) {
    this.fetchImpl = options.fetchImpl ?? ((...args) => fetch(...args));
    this.now = options.now ?? Date.now;
    this.freshForMs = options.freshForMs ?? 40_000;
  }

  /** The version being read; fixed by the first ticket. */
  get versionId(): string | null {
    return this.grant?.versionId ?? null;
  }

  get pageCount(): number | null {
    return this.grant?.pageCount ?? null;
  }

  /** Returns a fresh ticket, renewing it (single-flight) when stale or when `force` is set. */
  async ensureGrant(force = false): Promise<AccessTicket> {
    const fresh = this.grant && this.now() - this.receivedAt < this.freshForMs;
    if (fresh && !force) return this.grant!;
    this.renewing ??= (async () => {
      try {
        const next = await this.options.requestGrant();
        if (this.grant && next.versionId !== this.grant.versionId) throw new VersionChangedError();
        this.grant = next;
        this.receivedAt = this.now();
        return next;
      } finally {
        this.renewing = null;
      }
    })();
    return this.renewing;
  }

  /** Fetches bytes [begin, end) — `end` is exclusive, as in PDF.js. */
  async fetchRange(begin: number, end: number, signal?: AbortSignal): Promise<RangeResult> {
    for (let attempt = 0; ; attempt++) {
      const grant = await this.ensureGrant(attempt > 0);
      const res = await this.fetchImpl(apiUrl(grant.contentUrl), {
        headers: { Range: `bytes=${begin}-${end - 1}` },
        credentials: 'include',
        cache: 'no-store',
        signal,
      });

      if (res.status === 206) {
        return { data: new Uint8Array(await res.arrayBuffer()), total: totalFromContentRange(res) };
      }
      if (res.ok) {
        // Server ignored the Range header and sent the whole file.
        const all = new Uint8Array(await res.arrayBuffer());
        return { data: all.subarray(begin, Math.min(end, all.length)), total: all.length };
      }

      const body = await readError(res);
      if (body.code === 'VERSION_CHANGED') throw new VersionChangedError();
      if (body.code === 'INVALID_TICKET' && attempt === 0) continue;
      throw new ApiError(body);
    }
  }
}

function totalFromContentRange(res: Response): number {
  // "bytes 0-65535/1234567"
  const match = /\/(\d+)\s*$/.exec(res.headers.get('Content-Range') ?? '');
  if (!match) throw new ApiError({ status: res.status, code: 'BAD_RANGE_RESPONSE', message: 'The PDF could not be loaded.' });
  return Number(match[1]);
}

async function readError(res: Response): Promise<ApiErrorBody> {
  try {
    const body = (await res.json()) as Partial<ApiErrorBody>;
    return { ...body, status: res.status, code: body.code ?? `HTTP_${res.status}` };
  } catch {
    return { status: res.status, code: `HTTP_${res.status}` };
  }
}
