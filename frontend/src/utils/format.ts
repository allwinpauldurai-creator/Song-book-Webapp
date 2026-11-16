const dateFormat = new Intl.DateTimeFormat(undefined, { year: 'numeric', month: 'short', day: 'numeric' });

/**
 * Formats an ISO date ("2026-11-16") or instant ("2026-11-16T10:00:00Z").
 * Plain dates are calendar dates in the business timezone, so they are formatted without shifting
 * them through the browser's timezone (which could show the previous day).
 */
export function formatDate(value: string | null | undefined): string {
  if (!value) return '';
  const plain = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (plain) {
    const [, y, m, d] = plain;
    return dateFormat.format(new Date(Number(y), Number(m) - 1, Number(d)));
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : dateFormat.format(date);
}
