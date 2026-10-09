// Pure helpers (no React, no imports) so they can be unit-tested with `node --test`.

/** A rate from the API. `reported` means it is the source's own figure, not ours. */
export type Rate = { value: number | null; reported: boolean };

/** Missing is shown as an em dash, never as 0. */
export function num(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : String(value);
}

/** Rates to two decimals; a source-published rate is marked with †. */
export function rate(r: Rate | null | undefined): string {
  if (!r || r.value === null || r.value === undefined) {
    return '—';
  }
  return r.value.toFixed(2) + (r.reported ? '†' : '');
}

/**
 * Marks a figure with ‡ when it rests on a count we recovered from the source's published rate
 * (see docs/06). Unknown figures stay a plain dash.
 */
export function recoveredMark(text: string, recovered: boolean): string {
  return recovered && text !== '—' ? text + '‡' : text;
}

/**
 * When a category total covers only some of a player's sources, says which (e.g. "SCA only"),
 * so a reader doesn't take it for the all-sources figure. Null when it covers them all.
 */
export function coverageNote(sources: string[], covered: string[] | null | undefined,
                             label: (source: string) => string): string | null {
  if (!covered || covered.length === 0 || covered.length >= sources.length) {
    return null;
  }
  return covered.map(label).join(' + ') + ' only';
}

/** "3 hours ago", "2 days ago"; "Never" for null. */
export function relativeTime(iso: string | null | undefined, now: Date = new Date()): string {
  if (!iso) {
    return 'Never';
  }
  const then = new Date(iso);
  if (Number.isNaN(then.getTime())) {
    return 'Unknown';
  }
  const seconds = Math.max(0, Math.round((now.getTime() - then.getTime()) / 1000));
  const units: [number, string][] = [
    [60 * 60 * 24 * 365, 'year'],
    [60 * 60 * 24 * 30, 'month'],
    [60 * 60 * 24, 'day'],
    [60 * 60, 'hour'],
    [60, 'minute'],
  ];
  for (const [size, name] of units) {
    if (seconds >= size) {
      const n = Math.floor(seconds / size);
      return `${n} ${name}${n === 1 ? '' : 's'} ago`;
    }
  }
  return 'just now';
}

/**
 * Sort descending by a numeric key with unknown values last, then by name,
 * so "—" rows never appear above real figures.
 */
export function byDescNullsLast<T>(get: (item: T) => number | null | undefined, name: (item: T) => string) {
  return (a: T, b: T): number => {
    const x = get(a);
    const y = get(b);
    const xu = x === null || x === undefined;
    const yu = y === null || y === undefined;
    if (xu && yu) return name(a).localeCompare(name(b));
    if (xu) return 1;
    if (yu) return -1;
    if (y !== x) return (y as number) - (x as number);
    return name(a).localeCompare(name(b));
  };
}

/** "Sandeep Chandrasekharan Nair Roja" → "SR". */
export function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '?';
  const first = words[0][0] ?? '';
  const last = words.length > 1 ? words[words.length - 1][0] ?? '' : '';
  return (first + last).toUpperCase();
}
