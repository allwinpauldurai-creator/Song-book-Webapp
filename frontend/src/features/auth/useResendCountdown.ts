import { useCallback, useEffect, useState } from 'react';

/** Seconds until "Resend code" is allowed again (the server refuses a new code within 60 s anyway). */
export function useResendCountdown(initialSeconds = 0, cooldown = 60) {
  const [left, setLeft] = useState(initialSeconds);
  useEffect(() => {
    if (left <= 0) return undefined;
    const t = window.setTimeout(() => setLeft((s) => s - 1), 1000);
    return () => window.clearTimeout(t);
  }, [left]);
  const restart = useCallback(() => setLeft(cooldown), [cooldown]);
  return { secondsLeft: left, canResend: left <= 0, restart };
}
