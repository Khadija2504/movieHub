import { Component, DestroyRef, OnInit, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Subject, catchError, combineLatest, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';

import { LoadState, toLoadState } from '../../../core/http/load-state';
import { Genre, MediaSummary, Page } from '../../../core/models/catalog';
import { CatalogService } from '../../../core/services/catalog.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { Icon } from '../../../shared/components/icon/icon';
import { MediaCard, MediaCardSkeleton } from '../../../shared/components/media-card/media-card';
import { Pagination } from '../../../shared/components/pagination/pagination';

const IDLE: LoadState<Page<MediaSummary>> = { loading: false, data: null, error: null };

/** /search?q=...&page=... : the query lives in the URL; typing updates it after a short pause. */
@Component({
  selector: 'app-search-page',
  imports: [RouterLink, MediaCard, MediaCardSkeleton, Pagination, ErrorState, Icon],
  template: `
    <section class="relative overflow-hidden border-b border-border">
      <div class="pointer-events-none absolute -top-40 left-1/2 h-80 w-[60rem] -translate-x-1/2 rounded-full bg-primary/15 blur-[120px]" aria-hidden="true"></div>
      <div class="container-page page-top relative pb-10 text-center">
        <p class="eyebrow">Search</p>
        <h1 class="mt-3 text-4xl font-extrabold sm:text-5xl">Find your next <span class="text-gradient">favourite</span></h1>

        <div class="relative mx-auto mt-8 max-w-2xl">
          <app-icon name="search" class="pointer-events-none absolute left-5 top-1/2 z-10 size-5 -translate-y-1/2 text-muted" />
          <input
            type="search"
            class="input h-14! rounded-full! pl-14 pr-6 text-base! shadow-2xl"
            placeholder="Search movies and TV series…"
            aria-label="Search movies and TV series"
            [value]="q()"
            (input)="typed$.next($any($event.target).value)"
            autofocus
          />
        </div>

        @if (!q().trim() && genres().length) {
          <div class="mx-auto mt-6 flex max-w-3xl flex-wrap justify-center gap-2">
            <span class="self-center text-sm text-subtle">Popular genres:</span>
            @for (g of genres().slice(0, 7); track g.id) {
              <a routerLink="/movies" [queryParams]="{ genre: g.id }" class="chip">{{ g.name }}</a>
            }
          </div>
        }
      </div>
    </section>

    <section class="container-page pt-10">
      @if (!q().trim()) {
        <div class="mx-auto flex max-w-md flex-col items-center py-10 text-center">
          <span class="grid size-16 place-items-center rounded-2xl bg-white/5 text-muted ring-1 ring-border">
            <app-icon name="film" class="size-7" />
          </span>
          <p class="mt-5 font-display text-lg font-semibold">Start typing to search</p>
          <p class="mt-1 text-sm text-muted">Search across thousands of movies and TV series.</p>
        </div>
      } @else if (results().error; as error) {
        <app-error-state [message]="error" (retry)="retry()" />
      } @else if (results().loading) {
        <div class="grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 lg:gap-x-5 xl:grid-cols-6">
          @for (i of skeletons; track i) {
            <app-media-card-skeleton />
          }
        </div>
      } @else if (results().data; as page) {
        @if (page.content.length) {
          <p class="mb-6 text-sm text-muted">
            Results for <span class="font-semibold text-foreground">“{{ q() }}”</span>
          </p>
          <div class="grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 lg:gap-x-5 xl:grid-cols-6">
            @for (item of page.content; track item.mediaType + item.id; let i = $index) {
              <app-media-card [item]="item" showType class="animate-fade-up" [style.animation-delay.ms]="i * 30" />
            }
          </div>
          <app-pagination [page]="page.page" [totalPages]="page.totalPages" (pageChange)="goToPage($event)" />
        } @else {
          <div class="mx-auto flex max-w-md flex-col items-center py-10 text-center">
            <span class="grid size-16 place-items-center rounded-2xl bg-white/5 text-muted ring-1 ring-border">
              <app-icon name="search" class="size-7" />
            </span>
            <p class="mt-5 font-display text-lg font-semibold">No results for “{{ q() }}”</p>
            <p class="mt-1 text-sm text-muted">Check the spelling or try another title.</p>
          </div>
        }
      }
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

  protected readonly genres = toSignal(
    this.catalog.genres('MOVIE').pipe(catchError(() => of<Genre[]>([]))),
    { initialValue: [] as Genre[] },
  );

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
