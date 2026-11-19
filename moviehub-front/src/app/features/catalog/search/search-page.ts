import { Component, DestroyRef, OnInit, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { Subject, combineLatest, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';

import { LoadState, toLoadState } from '../../../core/http/load-state';
import { MediaSummary, Page } from '../../../core/models/catalog';
import { CatalogService } from '../../../core/services/catalog.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { MediaCard, MediaCardSkeleton } from '../../../shared/components/media-card/media-card';
import { Pagination } from '../../../shared/components/pagination/pagination';

const IDLE: LoadState<Page<MediaSummary>> = { loading: false, data: null, error: null };

/** /search?q=...&page=... : the query lives in the URL; typing updates it after a short pause. */
@Component({
  selector: 'app-search-page',
  imports: [MediaCard, MediaCardSkeleton, Pagination, ErrorState],
  template: `
    <section class="container-page py-8">
      <h1 class="text-3xl font-bold">Search</h1>

      <div class="relative mt-6 max-w-2xl">
        <input
          type="search"
          class="input py-3 pl-11 text-base"
          placeholder="Search movies and TV series…"
          aria-label="Search movies and TV series"
          [value]="q()"
          (input)="typed$.next($any($event.target).value)"
          autofocus
        />
        <svg class="pointer-events-none absolute left-3.5 top-1/2 h-5 w-5 -translate-y-1/2 text-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" />
        </svg>
      </div>

      <div class="mt-8">
        @if (!q().trim()) {
          <p class="text-muted">Type a title to start searching.</p>
        } @else if (results().error; as error) {
          <app-error-state [message]="error" (retry)="retry()" />
        } @else if (results().loading) {
          <div class="grid grid-cols-2 gap-x-4 gap-y-6 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
            @for (i of skeletons; track i) {
              <app-media-card-skeleton />
            }
          </div>
        } @else if (results().data; as page) {
          <p class="mb-4 text-sm text-muted">
            {{ page.content.length ? 'Results' : 'No results' }} for “<span class="text-foreground">{{ q() }}</span>”
          </p>
          <div class="grid grid-cols-2 gap-x-4 gap-y-6 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
            @for (item of page.content; track item.mediaType + item.id) {
              <app-media-card [item]="item" showType />
            }
          </div>
          <app-pagination [page]="page.page" [totalPages]="page.totalPages" (pageChange)="goToPage($event)" />
        }
      </div>
    </section>
  `,
})
export class SearchPage implements OnInit {
  private readonly catalog = inject(CatalogService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly q = input('', { transform: (v: string | undefined) => v ?? '' });
  readonly page = input(1, { transform: (v: unknown) => (v == null || v === '' ? 1 : numberAttribute(v, 1)) });

  protected readonly typed$ = new Subject<string>();
  protected readonly skeletons = Array.from({ length: 12 }, (_, i) => i);
  private readonly attempt = signal(0);

  private readonly request = computed(() => ({ q: this.q().trim(), page: this.page() }));

  protected readonly results = toSignal(
    combineLatest([toObservable(this.request), toObservable(this.attempt)]).pipe(
      switchMap(([{ q, page }]) => (q ? this.catalog.search(q, page).pipe(toLoadState()) : of(IDLE))),
    ),
    { initialValue: IDLE },
  );

  ngOnInit(): void {
    this.typed$
      .pipe(
        map((value) => value.trim()),
        debounceTime(350),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((q) => {
        // replaceUrl: typing should not create one history entry per keystroke.
        void this.router.navigate([], { queryParams: { q: q || null, page: null }, replaceUrl: true });
      });
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], { queryParams: { page }, queryParamsHandling: 'merge' });
  }

  protected retry(): void {
    this.attempt.update((n) => n + 1);
  }
}
