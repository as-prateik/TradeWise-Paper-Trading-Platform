import { DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { fromEvent, merge, Observable, startWith, switchMap, timer } from 'rxjs';
import { filter, map } from 'rxjs/operators';

/** Phase 1 has no WebSocket; live-ish data comes from polling. */
export const POLL_INTERVAL_MS = 12_000;

/**
 * Emits on a fixed interval while the tab is visible, and immediately again
 * when the tab regains focus. Pausing on hidden tabs keeps a background tab
 * from hammering the API.
 *
 * The data services are shaped around this function so that swapping in a
 * WebSocket source later means replacing one operator, not rewriting screens.
 */
export function pollWhileVisible<T>(
  fetcher: () => Observable<T>,
  intervalMs: number = POLL_INTERVAL_MS,
  explicitDestroyRef?: DestroyRef,
): Observable<T> {
  // A caller that starts polling outside the constructor (e.g. in ngOnInit,
  // because it depends on a required input) passes its DestroyRef in, since
  // inject() is only available during construction.
  const destroyRef = explicitDestroyRef ?? inject(DestroyRef);

  const ticks = timer(intervalMs, intervalMs).pipe(map(() => 'tick' as const));
  const becameVisible = fromEvent(document, 'visibilitychange').pipe(
    filter(() => !document.hidden),
    map(() => 'visible' as const),
  );

  return merge(ticks, becameVisible).pipe(
    startWith('initial' as const),
    filter(() => !document.hidden),
    switchMap(() => fetcher()),
    takeUntilDestroyed(destroyRef),
  );
}
