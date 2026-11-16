import { useCallback, useEffect, useRef, useState } from 'react';
import type { PDFDocumentProxy } from 'pdfjs-dist';

export interface PageMatch {
  page: number;
  count: number;
}

export interface SearchState {
  query: string;
  matches: PageMatch[];
  searching: boolean;
  /** Pages scanned so far, for progress. */
  scanned: number;
}

const EMPTY: SearchState = { query: '', matches: [], searching: false, scanned: 0 };

/** Case-insensitive occurrences of `q` in `text` (non-overlapping). */
export function countMatches(text: string, q: string): number {
  if (!q) return 0;
  const hay = text.toLowerCase();
  const needle = q.toLowerCase();
  let count = 0;
  for (let i = hay.indexOf(needle); i !== -1; i = hay.indexOf(needle, i + needle.length)) count++;
  return count;
}

/**
 * Searches the whole document page by page using PDF.js text extraction. Results stream in as
 * pages are scanned; starting a new search (or closing) cancels the previous one.
 */
export function usePdfSearch(doc: PDFDocumentProxy | null) {
  const [state, setState] = useState<SearchState>(EMPTY);
  const runId = useRef(0);

  const clear = useCallback(() => {
    runId.current++;
    setState(EMPTY);
  }, []);

  const search = useCallback(
    async (raw: string) => {
      const query = raw.trim();
      const id = ++runId.current;
      if (!doc || !query) {
        setState({ ...EMPTY, query });
        return;
      }
      setState({ query, matches: [], searching: true, scanned: 0 });
      const matches: PageMatch[] = [];
      for (let p = 1; p <= doc.numPages; p++) {
        let text = '';
        try {
          const content = await (await doc.getPage(p)).getTextContent();
          text = content.items.map((item) => ('str' in item ? item.str : '')).join(' ');
        } catch {
          // A page that fails to load is skipped; the reader reports load errors separately.
        }
        if (id !== runId.current) return;
        const count = countMatches(text, query);
        if (count) matches.push({ page: p, count });
        setState({ query, matches: [...matches], searching: p < doc.numPages, scanned: p });
      }
    },
    [doc],
  );

  useEffect(() => clear, [doc, clear]);

  return { ...state, search, clear };
}
