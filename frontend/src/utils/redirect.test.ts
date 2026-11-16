import { describe, expect, it } from 'vitest';
import { loginPath, safeNext } from './redirect';

describe('safeNext', () => {
  it('accepts same-site relative paths', () => {
    expect(safeNext('/read/42?page=3')).toBe('/read/42?page=3');
  });

  it.each([
    'https://evil.example',
    '//evil.example',
    '/\\evil.example',
    'javascript:alert(1)',
    'read/42',
    '/foo\nbar',
  ])('rejects %s', (value) => {
    expect(safeNext(value, '/me')).toBe('/me');
  });

  it('falls back when missing', () => {
    expect(safeNext(null)).toBe('/');
  });

  it('round-trips through loginPath', () => {
    const next = new URLSearchParams(loginPath('/read/1?x=1&y=2').split('?')[1]).get('next');
    expect(safeNext(next)).toBe('/read/1?x=1&y=2');
  });
});
