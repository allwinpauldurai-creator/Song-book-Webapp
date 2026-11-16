import { useEffect, useRef } from 'react';
import { Box } from '@mui/material';
import { TextLayer, type PDFDocumentProxy } from 'pdfjs-dist';
import { isRenderCancelled } from './pdfDocument';
import './textLayer.css';

interface Props {
  doc: PDFDocumentProxy;
  pageNumber: number;
  scale: number;
  /** Text runs containing this (case-insensitive) are highlighted. */
  query: string;
  onRendered?: () => void;
  onError?: (e: unknown) => void;
}

/**
 * Renders one PDF page to a canvas, plus PDF.js's text layer for selection and search highlights.
 * Each render draws into a fresh canvas that replaces the old one when finished, so fast page
 * flips never reuse a canvas that is still being drawn and the page doesn't flash blank.
 */
export default function PdfPageView({ doc, pageNumber, scale, query, onRendered, onError }: Props) {
  const pageRef = useRef<HTMLDivElement>(null);
  const canvasHostRef = useRef<HTMLDivElement>(null);
  const textRef = useRef<HTMLDivElement>(null);
  const callbacks = useRef({ onRendered, onError });
  callbacks.current = { onRendered, onError };

  useEffect(() => {
    let cancelled = false;
    let renderTask: { cancel: () => void } | null = null;
    let textLayer: TextLayer | null = null;

    (async () => {
      try {
        const page = await doc.getPage(pageNumber);
        if (cancelled) return;
        const viewport = page.getViewport({ scale });
        const dpr = window.devicePixelRatio || 1;

        const canvas = document.createElement('canvas');
        canvas.width = Math.floor(viewport.width * dpr);
        canvas.height = Math.floor(viewport.height * dpr);
        canvas.style.cssText = `display:block;width:${Math.floor(viewport.width)}px;height:${Math.floor(viewport.height)}px`;
        canvas.setAttribute('aria-hidden', 'true');

        const task = page.render({ canvas, viewport, transform: dpr !== 1 ? [dpr, 0, 0, dpr, 0, 0] : undefined });
        renderTask = task;
        await task.promise;
        if (cancelled) return;

        const host = pageRef.current!;
        host.style.width = `${Math.floor(viewport.width)}px`;
        host.style.height = `${Math.floor(viewport.height)}px`;
        host.style.setProperty('--total-scale-factor', String(scale));
        canvasHostRef.current!.replaceChildren(canvas);

        const container = textRef.current!;
        container.replaceChildren();
        textLayer = new TextLayer({ textContentSource: page.streamTextContent(), container, viewport });
        await textLayer.render();
        if (cancelled) return;
        highlight(container, query);
        callbacks.current.onRendered?.();
      } catch (e) {
        if (!cancelled && !isRenderCancelled(e)) callbacks.current.onError?.(e);
      }
    })();

    return () => {
      cancelled = true;
      renderTask?.cancel();
      textLayer?.cancel();
    };
    // `query` is applied by the effect below without re-rendering the page.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [doc, pageNumber, scale]);

  useEffect(() => {
    if (textRef.current) highlight(textRef.current, query);
  }, [query]);

  return (
    <Box
      ref={pageRef}
      role="img"
      aria-label={`Page ${pageNumber}`}
      sx={{ position: 'relative', bgcolor: 'common.white', boxShadow: 3, mx: 'auto', flexShrink: 0 }}
    >
      <div ref={canvasHostRef} />
      <div ref={textRef} className="textLayer" />
    </Box>
  );
}

function highlight(container: HTMLElement, query: string) {
  const q = query.trim().toLowerCase();
  let first: Element | null = null;
  for (const span of container.querySelectorAll('span')) {
    const hit = q.length > 0 && (span.textContent ?? '').toLowerCase().includes(q);
    span.classList.toggle('highlight', hit);
    if (hit && !first) first = span;
  }
  first?.scrollIntoView({ block: 'center', inline: 'nearest' });
}
