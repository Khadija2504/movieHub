import { CurrencyPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { combineLatest, switchMap } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { Page } from '../../../core/models/catalog';
import { LibraryEntry, LibraryFilter } from '../../../core/models/streaming';
import { StreamingService } from '../../../core/services/streaming.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { Icon } from '../../../shared/components/icon/icon';
import { Pagination } from '../../../shared/components/pagination/pagination';
import { RuntimePipe } from '../../../shared/pipes/runtime.pipe';
import { shortDate, timeLeft } from '../../streaming/streaming-format';
import { PageHeader } from '../components/page-header';

const TABS: { value: LibraryFilter; label: string; param?: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'PURCHASED', label: 'Purchased', param: 'purchased' },
  { value: 'RENTED', label: 'Rented', param: 'rented' },
];

@Component({
  selector: 'app-library-page',
  imports: [RouterLink, Icon, ErrorState, Pagination, PageHeader, RuntimePipe, CurrencyPipe],
  template: `
    <app-page-header heading="My Library" icon="library" subheading="Movies you bought or rented">
      <div class="flex gap-2" role="tablist" aria-label="Library">
        @for (tab of tabs; track tab.value) {
          <button type="button" role="tab" class="chip" [class.chip-active]="filter() === tab.value" [attr.aria-selected]="filter() === tab.value" (click)="setFilter(tab.param)">
            {{ tab.label }}
          </button>
        }
      </div>
    </app-page-header>

    @if (state().error; as error) {
      <app-error-state [message]="error" (retry)="retry()" />
    } @else if (state().loading) {
      <div class="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
        @for (i of [1, 2, 3]; track i) {
          <div class="skeleton aspect-video rounded-card"></div>
        }
      </div>
    } @else if (state().data; as page) {
      @if (page.content.length) {
        <ul class="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
          @for (item of page.content; track item.tmdbId; let i = $index) {
            <li class="card group animate-fade-up overflow-hidden" [style.animation-delay.ms]="i * 40" [class.opacity-70]="!item.active">
              <a [routerLink]="item.active ? ['/watch', item.tmdbId] : ['/movies', item.tmdbId]" class="relative block aspect-video bg-surface-raised" [attr.aria-label]="(item.active ? 'Watch ' : 'View ') + item.title">
                @if (item.backdropUrl || item.posterUrl) {
                  <img [src]="item.backdropUrl ?? item.posterUrl" alt="" class="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105" loading="lazy" />
                }
                <div class="absolute inset-0 bg-linear-to-t from-black/85 via-black/20 to-transparent"></div>
                @if (item.active) {
                  <span class="absolute inset-0 m-auto grid size-14 place-items-center rounded-full bg-primary/90 text-white opacity-0 shadow-glow transition group-hover:opacity-100 group-focus-visible:opacity-100"><app-icon name="play" filled class="ml-0.5 size-6" /></span>
                }
                <span class="badge absolute top-3 left-3 text-white" [class]="badge(item)">{{ status(item) }}</span>
                @if (item.progressPercent) {
                  <div class="absolute inset-x-0 bottom-0 h-1 bg-white/20"><div class="h-full bg-primary" [style.width.%]="item.progressPercent"></div></div>
                }
              </a>
              <div class="flex items-start justify-between gap-3 p-4">
                <div class="min-w-0">
                  <h2 class="truncate font-display font-semibold">{{ item.title }}</h2>
                  <p class="mt-0.5 text-xs text-muted">
                    {{ item.year }}@if (item.runtime) { · {{ item.runtime | runtime }} } · {{ item.kind === 'PURCHASE' ? 'Bought' : 'Rented' }} {{ date(item.acquiredAt) }}
                  </p>
                </div>
                @if (item.active) {
                  <a [routerLink]="['/watch', item.tmdbId]" class="btn btn-primary btn-sm shrink-0">{{ item.progressPercent && item.progressPercent < 100 ? 'Resume' : 'Play' }}</a>
                } @else if (item.rentalPrice != null) {
                  <a [routerLink]="['/movies', item.tmdbId]" class="btn btn-secondary btn-sm shrink-0">Rent again · {{ item.rentalPrice | currency: currency }}</a>
                }
              </div>
            </li>
          }
        </ul>
        <app-pagination [page]="page.page" [totalPages]="page.totalPages" (pageChange)="goToPage($event)" />
      } @else {
        <div class="card flex flex-col items-center px-6 py-16 text-center">
          <span class="grid size-16 place-items-center rounded-2xl bg-violet-500/12 text-violet-400 ring-1 ring-violet-500/20"><app-icon name="library" class="size-7" /></span>
          <p class="mt-5 font-display text-lg font-semibold">{{ filter() === 'RENTED' ? 'No rentals yet' : filter() === 'PURCHASED' ? 'No purchases yet' : 'Your library is empty' }}</p>
          <p class="mt-1 max-w-sm text-sm text-muted">Movies you buy stay here forever. Rentals stay for 48 hours after payment.</p>
          <a routerLink="/movies" class="btn btn-primary mt-6"><app-icon name="film" class="size-4" /> Browse movies</a>
        </div>
      }
    }
  `,
})
export class LibraryPage {
  private readonly streaming = inject(StreamingService);
  private readonly router = inject(Router);

  /** Query param ?tab=purchased|rented */
  readonly tab = input<string | undefined>();

  protected readonly tabs = TABS;
  protected readonly currency = environment.currency;
  protected readonly filter = computed<LibraryFilter>(() => TABS.find((t) => t.param && t.param === this.tab())?.value ?? 'ALL');
  private readonly page = signal(1);
  private readonly attempt = signal(0);
  private readonly now = signal(Date.now());

  protected readonly state = toSignal(
    combineLatest([toObservable(this.filter), toObservable(this.page), toObservable(this.attempt)]).pipe(
      switchMap(([filter, page]) => this.streaming.library(filter, page).pipe(toLoadState())),
    ),
    { initialValue: LOADING as LoadState<Page<LibraryEntry>> },
  );

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), 60_000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  protected status(item: LibraryEntry): string {
    if (item.kind === 'PURCHASE') return 'Owned';
    return item.active ? timeLeft(item.expiresAt, this.now()) : 'Rental ended';
  }

  protected badge(item: LibraryEntry): string {
    if (item.kind === 'PURCHASE') return 'bg-violet-500/80';
    if (!item.active) return 'bg-black/60';
    const hoursLeft = (new Date(item.expiresAt!).getTime() - this.now()) / 3600_000;
    return hoursLeft < 6 ? 'bg-danger/85' : 'bg-black/60 ring-1 ring-white/20';
  }

  protected date(instant: string): string {
    return shortDate(instant);
  }

  protected setFilter(param: string | undefined): void {
    this.page.set(1);
    void this.router.navigate([], { queryParams: { tab: param ?? null }, replaceUrl: true });
  }

  protected goToPage(page: number): void {
    this.page.set(page);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected retry(): void {
    this.attempt.update((n) => n + 1);
  }
}
