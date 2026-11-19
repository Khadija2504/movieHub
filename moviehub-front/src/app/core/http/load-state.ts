import { Observable, OperatorFunction, catchError, map, of, startWith } from 'rxjs';

import { errorMessage } from './api-error.util';

/** UI state of an async request: exactly one of loading / data / error is meaningful. */
export interface LoadState<T> {
  loading: boolean;
  data: T | null;
  error: string | null;
}

export const LOADING: LoadState<never> = { loading: true, data: null, error: null };

/**
 * Turns a request into a stream of LoadState: LOADING first, then data or a friendly error.
 * Never errors, so it is safe inside switchMap / toSignal.
 */
export function toLoadState<T>(): OperatorFunction<T, LoadState<T>> {
  return (source: Observable<T>) =>
    source.pipe(
      map((data): LoadState<T> => ({ loading: false, data, error: null })),
      startWith(LOADING as LoadState<T>),
      catchError((error: unknown) =>
        of<LoadState<T>>({ loading: false, data: null, error: errorMessage(error) }),
      ),
    );
}
