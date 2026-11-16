import type { AccessTokenResponse, ApiErrorBody, FieldError } from '../../types/api';

/**
 * Thin fetch wrapper for the Song Book API.
 *
 * - The access token lives in module memory only (never localStorage), per docs/SECURITY.md.
 * - The refresh token is an HttpOnly cookie scoped to /api/v1/auth; the browser sends it for us.
 * - On a 401 the client refreshes once and retries. Refreshes are single-flight: the backend
 *   rotates the refresh token on every use and treats a reused token as theft (revoking the whole
 *   session family), so two parallel refresh calls must never happen.
 */

export const API_BASE_URL: string = (import.meta.env?.VITE_API_BASE_URL ?? '').replace(/\/+$/, '');
const API_PREFIX = '/api/v1';

/** Turns a backend-relative path such as "/api/v1/books/x/cover" into a full URL. */
export function apiUrl(path: string): string {
  return /^https?:\/\//i.test(path) ? path : API_BASE_URL + path;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldError[];

  constructor(body: ApiErrorBody) {
    super(body.message || defaultMessage(body.status));
    this.name = 'ApiError';
    this.status = body.status;
    this.code = body.code;
    this.fieldErrors = body.fieldErrors ?? [];
  }

  /** Field-level messages keyed by field name, for form helper text. */
  fieldMap(): Record<string, string> {
    return Object.fromEntries(this.fieldErrors.map((f) => [f.field, f.message]));
  }
}

function defaultMessage(status: number): string {
  if (status === 0) return 'Cannot reach the server. Check your connection and try again.';
  if (status === 429) return 'Too many attempts. Please wait a few minutes and try again.';
  if (status >= 500) return 'Something went wrong on our side. Please try again.';
  return 'The request could not be completed.';
}

// ---------------------------------------------------------------- token state

let accessToken: string | null = null;
let refreshInFlight: Promise<string | null> | null = null;
const sessionListeners = new Set<() => void>();

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

/** Called when a refresh fails mid-session, so the UI can drop the signed-in user. */
export function onSessionExpired(listener: () => void): () => void {
  sessionListeners.add(listener);
  return () => sessionListeners.delete(listener);
}

/**
 * Exchanges the refresh cookie for a new access token. Concurrent callers share one request.
 * Resolves to null (never rejects) when there is no valid session.
 */
export function refreshAccessToken(): Promise<string | null> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const res = await fetch(apiUrl(`${API_PREFIX}/auth/refresh`), {
          method: 'POST',
          credentials: 'include',
          headers: { Accept: 'application/json' },
        });
        if (!res.ok) {
          accessToken = null;
          return null;
        }
        const body = (await res.json()) as AccessTokenResponse;
        accessToken = body.accessToken;
        return accessToken;
      } catch {
        // Network failure: keep any token we have; the original request will surface the error.
        return null;
      } finally {
        refreshInFlight = null;
      }
    })();
  }
  return refreshInFlight;
}

// ---------------------------------------------------------------- requests

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | boolean | null | undefined>;
  /** Send the bearer token and retry once after refreshing on 401. Default true. */
  auth?: boolean;
  signal?: AbortSignal;
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { auth = true } = options;
  let res = await send(path, options, auth);

  if (res.status === 401 && auth && !path.startsWith('/auth/')) {
    const hadToken = accessToken !== null;
    const token = await refreshAccessToken();
    if (token) {
      res = await send(path, options, auth);
    } else if (hadToken) {
      sessionListeners.forEach((l) => l());
    }
  }

  if (!res.ok) throw await toApiError(res);
  if (res.status === 204 || res.headers.get('Content-Length') === '0') return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

async function send(path: string, options: RequestOptions, auth: boolean): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`;

  try {
    return await fetch(apiUrl(API_PREFIX + path + toQueryString(options.query)), {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      credentials: 'include',
      signal: options.signal,
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e;
    throw new ApiError({ status: 0, code: 'NETWORK_ERROR' });
  }
}

export function toQueryString(query?: RequestOptions['query']): string {
  if (!query) return '';
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(query)) {
    if (v !== undefined && v !== null && v !== '') params.set(k, String(v));
  }
  const s = params.toString();
  return s ? `?${s}` : '';
}

async function toApiError(res: Response): Promise<ApiError> {
  try {
    const body = (await res.json()) as Partial<ApiErrorBody>;
    return new ApiError({ ...body, status: res.status, code: body.code ?? `HTTP_${res.status}` });
  } catch {
    return new ApiError({ status: res.status, code: `HTTP_${res.status}` });
  }
}

/** Message suitable for an Alert, for any thrown value. */
export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  if (e instanceof Error) return e.message;
  return 'Something went wrong. Please try again.';
}
