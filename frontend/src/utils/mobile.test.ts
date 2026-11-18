import { describe, expect, it } from 'vitest';
import { formatMobile, normalizeMobile } from './mobile';

describe('normalizeMobile', () => {
  it.each(['9876543210', '98765 43210', '+91 98765-43210', '+919876543210', '09876543210', '919876543210', '0091 98765 43210'])(
    'normalises %s to E.164',
    (raw) => {
      expect(normalizeMobile(raw)).toBe('+919876543210');
    },
  );

  it('keeps other country codes', () => {
    expect(normalizeMobile('+44 7700 900123')).toBe('+447700900123');
  });

  it.each(['', '12345', '5876543210', '+910000000000', 'abc', '98765432101'])('rejects %s', (raw) => {
    expect(normalizeMobile(raw)).toBeNull();
  });
});

describe('formatMobile', () => {
  it('groups Indian numbers and leaves others alone', () => {
    expect(formatMobile('+919876543210')).toBe('+91 98765 43210');
    expect(formatMobile('+447700900123')).toBe('+447700900123');
    expect(formatMobile(null)).toBe('');
  });
});
