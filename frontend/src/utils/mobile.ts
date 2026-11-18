/**
 * Mobile numbers, mirroring the backend's PhoneNumbers: stored and sent as E.164 (+919876543210).
 * A 10-digit Indian mobile (starting 6-9), optionally prefixed with 0 or 91, gets +91; any other
 * country needs its "+" code. Spaces, dashes, dots and brackets are ignored.
 */
const INDIAN_MOBILE = /^[6-9]\d{9}$/;
const E164 = /^\+[1-9]\d{7,14}$/;

export function normalizeMobile(raw: string): string | null {
  let s = raw.trim().replace(/[\s\-().]/g, '');
  if (s.startsWith('00')) s = `+${s.slice(2)}`;
  if (!s.startsWith('+')) {
    if (s.startsWith('0') && s.length === 11) s = s.slice(1);
    else if (s.startsWith('91') && s.length === 12) s = s.slice(2);
    if (!INDIAN_MOBILE.test(s)) return null;
    s = `+91${s}`;
  }
  if (s.startsWith('+91') && !INDIAN_MOBILE.test(s.slice(3))) return null;
  return E164.test(s) ? s : null;
}

/** "+919876543210" -> "+91 98765 43210"; other countries are shown as stored. */
export function formatMobile(e164: string | null | undefined): string {
  if (!e164) return '';
  if (e164.startsWith('+91') && e164.length === 13) return `+91 ${e164.slice(3, 8)} ${e164.slice(8)}`;
  return e164;
}

export const MOBILE_HELP = '10-digit mobile number. Outside India, start with + and the country code.';
