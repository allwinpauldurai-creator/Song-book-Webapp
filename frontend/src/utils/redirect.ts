/**
 * Validates a `?next=` redirect target. Only same-site relative paths are allowed, so a crafted
 * link such as /login?next=https://evil.example cannot send a freshly signed-in user elsewhere.
 */
export function safeNext(next: string | null | undefined, fallback = '/'): string {
  if (!next) return fallback;
  if (!next.startsWith('/') || next.startsWith('//') || next.startsWith('/\\')) return fallback;
  if (/[\u0000-\u001f]/.test(next)) return fallback;
  return next;
}

/** Builds /login?next=<current path> for a protected location. */
export function loginPath(next: string): string {
  return `/login?next=${encodeURIComponent(next)}`;
}
