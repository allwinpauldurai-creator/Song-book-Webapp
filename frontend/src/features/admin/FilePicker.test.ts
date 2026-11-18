import { describe, expect, it } from 'vitest';
import { checkImage, checkPdf, IMAGE_MAX, PDF_MAX } from './FilePicker';

const file = (name: string, type: string, size: number) => {
  const f = new File([new Uint8Array(1)], name, { type });
  Object.defineProperty(f, 'size', { value: size });
  return f;
};

describe('upload checks (mirror backend limits)', () => {
  it('accepts a normal PDF and nothing chosen', () => {
    expect(checkPdf(file('a.pdf', 'application/pdf', 1000))).toBeNull();
    expect(checkPdf(null)).toBeNull();
  });

  it('accepts a .pdf with an empty browser type', () => {
    expect(checkPdf(file('a.PDF', '', 1000))).toBeNull();
  });

  it('rejects non-PDFs, empty and oversized PDFs', () => {
    expect(checkPdf(file('a.docx', 'application/msword', 1000))).toMatch(/PDF/);
    expect(checkPdf(file('a.pdf', 'application/pdf', 0))).toMatch(/empty/);
    expect(checkPdf(file('a.pdf', 'application/pdf', PDF_MAX + 1))).toMatch(/100 MB/);
  });

  it('accepts JPEG/PNG/WebP covers up to 5 MB', () => {
    for (const t of ['image/jpeg', 'image/png', 'image/webp']) expect(checkImage(file('c', t, IMAGE_MAX))).toBeNull();
  });

  it('rejects other image types and oversized covers', () => {
    expect(checkImage(file('c.svg', 'image/svg+xml', 10))).toMatch(/JPEG, PNG or WebP/);
    expect(checkImage(file('c.gif', 'image/gif', 10))).toMatch(/JPEG, PNG or WebP/);
    expect(checkImage(file('c.png', 'image/png', IMAGE_MAX + 1))).toMatch(/5 MB/);
  });
});
