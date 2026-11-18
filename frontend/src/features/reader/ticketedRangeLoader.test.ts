import { describe, expect, it, vi } from 'vitest';
import { TicketedRangeLoader, VersionChangedError } from './ticketedRangeLoader';
import type { AccessTicket } from '../../types/api';

const FILE = Uint8Array.from({ length: 100 }, (_, i) => i);

function grant(n: number, versionId = 'v1'): AccessTicket {
  return {
    ticket: `t${n}`,
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
    versionId,
    versionNumber: 1,
    pageCount: 3,
    contentUrl: `/api/v1/books/b1/content?ticket=t${n}`,
  };
}

/** Fake content endpoint: serves FILE ranges for tickets listed in `valid`. */
function server(valid: Set<string>, opts: { versionChanged?: boolean } = {}) {
  return vi.fn<typeof fetch>(async (input, init) => {
    const ticket = new URL(String(input), 'http://x').searchParams.get('ticket')!;
    if (opts.versionChanged) return Response.json({ code: 'VERSION_CHANGED' }, { status: 409 });
    if (!valid.has(ticket)) return Response.json({ code: 'INVALID_TICKET' }, { status: 403 });
    const range = (init!.headers as Record<string, string>).Range ?? '';
    const [, a, b] = /bytes=(\d+)-(\d+)/.exec(range)!;
    const body = FILE.slice(Number(a), Number(b) + 1);
    return new Response(body, { status: 206, headers: { 'Content-Range': `bytes ${a}-${b}/${FILE.length}` } });
  });
}

describe('TicketedRangeLoader', () => {
  it('fetches a range with Range header and reports the total size', async () => {
    const fetchImpl = server(new Set(['t1']));
    const loader = new TicketedRangeLoader({ requestGrant: async () => grant(1), fetchImpl });

    const r = await loader.fetchRange(10, 20);

    expect(Array.from(r.data)).toEqual([10, 11, 12, 13, 14, 15, 16, 17, 18, 19]);
    expect(r.total).toBe(100);
    expect((fetchImpl.mock.calls[0]![1]!.headers as Record<string, string>).Range).toBe('bytes=10-19');
  });

  it('reuses a fresh ticket and renews it once it gets old', async () => {
    let clock = 0;
    let n = 0;
    const requestGrant = vi.fn(async () => grant(++n));
    const loader = new TicketedRangeLoader({ requestGrant, fetchImpl: server(new Set(['t1', 't2'])), now: () => clock });

    await loader.fetchRange(0, 10);
    clock = 30_000;
    await loader.fetchRange(10, 20);
    expect(requestGrant).toHaveBeenCalledTimes(1);

    clock = 41_000;
    await loader.fetchRange(20, 30);
    expect(requestGrant).toHaveBeenCalledTimes(2);
  });

  it('renews and retries once when the server says the ticket expired', async () => {
    let n = 0;
    const requestGrant = vi.fn(async () => grant(++n));
    const loader = new TicketedRangeLoader({ requestGrant, fetchImpl: server(new Set(['t2'])) });

    const r = await loader.fetchRange(0, 5);

    expect(r.data.length).toBe(5);
    expect(requestGrant).toHaveBeenCalledTimes(2);
  });

  it('gives up after one retry', async () => {
    let n = 0;
    const loader = new TicketedRangeLoader({ requestGrant: async () => grant(++n), fetchImpl: server(new Set()) });
    await expect(loader.fetchRange(0, 5)).rejects.toMatchObject({ code: 'INVALID_TICKET', status: 403 });
  });

  it('shares one renewal between parallel range requests', async () => {
    let n = 0;
    const requestGrant = vi.fn(async () => grant(++n));
    const loader = new TicketedRangeLoader({ requestGrant, fetchImpl: server(new Set(['t1'])) });

    await Promise.all([loader.fetchRange(0, 10), loader.fetchRange(10, 20), loader.fetchRange(20, 30)]);

    expect(requestGrant).toHaveBeenCalledTimes(1);
  });

  it('reports a version change from the server', async () => {
    const loader = new TicketedRangeLoader({ requestGrant: async () => grant(1), fetchImpl: server(new Set(['t1']), { versionChanged: true }) });
    await expect(loader.fetchRange(0, 5)).rejects.toBeInstanceOf(VersionChangedError);
  });

  it('reports a version change when a renewed ticket is for another version', async () => {
    let clock = 0;
    let n = 0;
    const requestGrant = async () => (++n === 1 ? grant(1, 'v1') : grant(2, 'v2'));
    const loader = new TicketedRangeLoader({ requestGrant, fetchImpl: server(new Set(['t1', 't2'])), now: () => clock });

    await loader.fetchRange(0, 5);
    clock = 60_000;
    await expect(loader.fetchRange(5, 10)).rejects.toBeInstanceOf(VersionChangedError);
  });

  it('handles a server that ignores Range and sends the whole file', async () => {
    const fetchImpl = vi.fn<typeof fetch>(async () => new Response(FILE.slice(), { status: 200 }));
    const loader = new TicketedRangeLoader({ requestGrant: async () => grant(1), fetchImpl });

    const r = await loader.fetchRange(95, 200);

    expect(Array.from(r.data)).toEqual([95, 96, 97, 98, 99]);
    expect(r.total).toBe(100);
  });

  it('surfaces eligibility errors as ApiError', async () => {
    const fetchImpl = vi.fn<typeof fetch>(async () =>
      Response.json({ code: 'MEMBERSHIP_INVALID', message: 'Your membership is not valid' }, { status: 403 }),
    );
    const loader = new TicketedRangeLoader({ requestGrant: async () => grant(1), fetchImpl });
    await expect(loader.fetchRange(0, 5)).rejects.toMatchObject({ code: 'MEMBERSHIP_INVALID', message: 'Your membership is not valid' });
  });
});
