import { useCallback, useEffect, useState } from 'react';

export type AsyncState<T> =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'success'; data: T };

/**
 * Runs `load` when `deps` change and on `reload()`. Ignores results that arrive
 * after a newer request started, so fast tab switching never shows stale data.
 */
export function useAsync<T>(load: () => Promise<T>, deps: unknown[]): AsyncState<T> & { reload: () => void } {
  const [state, setState] = useState<AsyncState<T>>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);
  const reload = useCallback(() => setAttempt((n) => n + 1), []);

  useEffect(() => {
    let current = true;
    setState({ status: 'loading' });
    load().then(
      (data) => current && setState({ status: 'success', data }),
      (e: unknown) => current && setState({ status: 'error', message: e instanceof Error ? e.message : String(e) }),
    );
    return () => {
      current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, attempt]);

  return { ...state, reload };
}
