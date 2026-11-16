import { describe, expect, it } from 'vitest';
import { countMatches } from './usePdfSearch';

describe('countMatches', () => {
  it('counts case-insensitive, non-overlapping occurrences', () => {
    expect(countMatches('Verse 1 ... verse 2 ... VERSE 3', 'verse')).toBe(3);
    expect(countMatches('aaaa', 'aa')).toBe(2);
  });

  it('returns 0 for no match or an empty query', () => {
    expect(countMatches('Chorus', 'verse')).toBe(0);
    expect(countMatches('Chorus', '')).toBe(0);
  });
});
