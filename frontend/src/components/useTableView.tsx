import { ReactNode, useMemo, useState } from 'react';

type Value = string | number | boolean | null | undefined;

/** Rows shown before "Show all": enough for a month, light enough for the browser when a list grows to thousands. */
export const PAGE_ROWS = 200;

/**
 * Sorting and paging for a list table. {@code sorters} maps a column key to the value it sorts by. Click a header once
 * for ascending, again for descending, a third time for the list's own order. Long lists show {@link PAGE_ROWS} rows
 * and a "Show all" button; totals stay computed by the page over the whole list.
 */
export function useTableView<T>(rows: readonly T[] | null | undefined, sorters: Record<string, (row: T) => Value>) {
  const [sort, setSort] = useState<{ key: string; desc: boolean } | null>(null);
  const [all, setAll] = useState(false);

  const sorted = useMemo(() => {
    const list = rows ?? [];
    const by = sort && sorters[sort.key];
    if (!by) return list;
    const dir = sort.desc ? -1 : 1;
    return [...list].sort((a, b) => {
      const x = by(a), y = by(b);
      const ex = empty(x), ey = empty(y);
      // Blanks stay last whichever way the column is sorted.
      if (ex || ey) return ex && ey ? 0 : ex ? 1 : -1;
      return dir * compare(x, y);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- sorters is a fresh object each render; the key alone picks the column.
  }, [rows, sort]);

  const visible = all ? sorted : sorted.slice(0, PAGE_ROWS);

  function th(key: string, label: ReactNode, className?: string) {
    const on = sort?.key === key;
    const next = () => setSort(!on ? { key, desc: false } : !sort!.desc ? { key, desc: true } : null);
    return (
      <th className={className} aria-sort={on ? (sort!.desc ? 'descending' : 'ascending') : 'none'}>
        <button type="button" className="sort-head" onClick={next}>
          {label}
          <span className="sort-mark" aria-hidden="true">{on ? (sort!.desc ? '▼' : '▲') : '↕'}</span>
        </button>
      </th>
    );
  }

  const more =
    !all && sorted.length > PAGE_ROWS ? (
      <p className="table-more">
        Showing {PAGE_ROWS} of {sorted.length}.{' '}
        <button type="button" className="link" onClick={() => setAll(true)}>
          Show all {sorted.length}
        </button>
      </p>
    ) : null;

  return { rows: visible, th, more };
}

const empty = (v: Value) => v === null || v === undefined || v === '';

/** Numbers by size; text in natural order ("Branch 2" before "Branch 10"), ignoring case. */
function compare(a: Value, b: Value): number {
  if (typeof a === 'number' && typeof b === 'number') return a - b;
  return String(a).localeCompare(String(b), undefined, { numeric: true, sensitivity: 'base' });
}
