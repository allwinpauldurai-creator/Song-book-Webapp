import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, getAccessToken, onSessionExpired, refreshAccessToken, setAccessToken, toQueryString } from './client';

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('api client', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    setAccessToken(null);
  });
  afterEach(() => vi.unstubAllGlobals());

  it('shares one refresh request between concurrent callers', async () => {
    let resolve!: (r: Response) => void;
    fetchMock.mockReturnValueOnce(new Promise<Response>((r) => (resolve = r)));

    const a = refreshAccessToken();
    const b = refreshAccessToken();
    resolve(json(200, { accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 900 }));

    expect(await a).toBe('new-token');
    expect(await b).toBe('new-token');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(getAccessToken()).toBe('new-token');
  });

  it('refreshes once on 401 and retries with the new token', async () => {
    setAccessToken('expired');
    fetchMock
      .mockResolvedValueOnce(json(401, { status: 401, code: 'UNAUTHORIZED' }))
      .mockResolvedValueOnce(json(200, { accessToken: 'fresh', tokenType: 'Bearer', expiresIn: 900 }))
      .mockResolvedValueOnce(json(200, { id: 'u1' }));

    await expect(api('/users/me')).resolves.toEqual({ id: 'u1' });

    const retryHeaders = fetchMock.mock.calls[2]![1]!.headers as Record<string, string>;
    expect(retryHeaders.Authorization).toBe('Bearer fresh');
  });

  it('notifies listeners and throws when the session cannot be refreshed', async () => {
    setAccessToken('expired');
    const expired = vi.fn();
    const off = onSessionExpired(expired);
    fetchMock
      .mockResolvedValueOnce(json(401, { status: 401, code: 'UNAUTHORIZED' }))
      .mockResolvedValueOnce(json(401, { status: 401, code: 'INVALID_REFRESH_TOKEN' }));

    await expect(api('/users/me')).rejects.toMatchObject({ status: 401, code: 'UNAUTHORIZED' });
    expect(expired).toHaveBeenCalledOnce();
    expect(getAccessToken()).toBeNull();
    off();
  });

  it('does not try to refresh for auth endpoints', async () => {
    fetchMock.mockResolvedValueOnce(json(401, { status: 401, code: 'INVALID_CREDENTIALS', message: 'Bad' }));
    await expect(api('/auth/login', { method: 'POST', body: {}, auth: false })).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('maps validation errors to a field map', async () => {
    fetchMock.mockResolvedValueOnce(
      json(400, { status: 400, code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'email', message: 'must be a well-formed email address' }] }),
    );
    const err = (await api('/auth/register', { method: 'POST', body: {}, auth: false }).catch((e) => e)) as ApiError;
    expect(err.fieldMap()).toEqual({ email: 'must be a well-formed email address' });
  });

  it('reports network failures as status 0', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    await expect(api('/books', { auth: false })).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
  });

  it('builds query strings without empty values', () => {
    expect(toQueryString({ q: 'hymn', categoryId: '', page: 0, size: undefined })).toBe('?q=hymn&page=0');
    expect(toQueryString({})).toBe('');
  });
});
