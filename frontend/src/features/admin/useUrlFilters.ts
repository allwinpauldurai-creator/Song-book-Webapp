import { useSearchParams } from 'react-router';

/**
 * Filter + paging state stored in the URL query string, so admin lists are linkable (e.g. the
 * dashboard links to /admin/books?status=DRAFT) and survive back/forward.
 * `page` is 0-based. Changing any filter resets the page.
 */
export function useUrlFilters<K extends string>(keys: readonly K[], defaultSize = 20) {
  const [params, setParams] = useSearchParams();
  const values = Object.fromEntries(keys.map((k) => [k, params.get(k) ?? ''])) as Record<K, string>;
  const page = Math.max(0, Number.parseInt(params.get('page') ?? '0', 10) || 0);
  const size = Number.parseInt(params.get('size') ?? '', 10) || defaultSize;

  const set = (changes: Partial<Record<K | 'page' | 'size', string | number | null>>) => {
    const next = new URLSearchParams(params);
    const touchesFilter = Object.keys(changes).some((k) => k !== 'page');
    for (const [k, v] of Object.entries(changes)) {
      if (v === null || v === '' || v === undefined) next.delete(k);
      else next.set(k, String(v));
    }
    if (touchesFilter && !('page' in changes)) next.delete('page');
    if (next.get('page') === '0') next.delete('page');
    setParams(next, { replace: true });
  };

  return { values, page, size, set };
}
