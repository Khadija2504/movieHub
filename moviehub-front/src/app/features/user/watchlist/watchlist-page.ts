import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { combineLatest, switchMap } from 'rxjs';

import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { MediaSummary, MediaType, Page } from '../../../core/models/catalog';
import { WatchlistItem } from '../../../core/models/user-features';
import { WatchlistService } from '../../../core/services/watchlist.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { Icon } from '../../../shared/components/icon/icon';
import { MediaCard, MediaCardSkeleton } from '../../../shared/components/media-card/media-card';
import { Pagination } from '../../../shared/components/pagination/pagination';
import { PageHeader } from '../components/page-header';

const FILTERS: { value: MediaType | null; label: string }[] = [
  { value: null, label: 'All' },
  { value: 'MOVIE', label: 'Movies' },
  { value: 'SERIES', label: 'TV Series' },
];

@Component({
  selector: 'app-watchlist-page',
  imports: [RouterLink, MediaCard, MediaCardSkeleton, Pagination, ErrorState, Icon, PageHeader],
  template: `
    <app-page-header heading="My Watchlist" icon="bookmark" [subheading]="subheading()">
      <div class="flex gap-2" role="radiogroup" aria-label="Filter">
        @for (f of filters; track f.label) {
          <button type="button" role="radio" class="chip" [class.chip-active]="type() === f.value" [attr.aria-checked]="type() === f.value" (click)="setType(f.value)">
            {{ f.label }}
          </button>
        }
      </div>
    </app-page-header>

    @if (state().error; as error) {
      <app-error-state [message]="error" (retry)="retry()" />
    } @else if (state().loading) {
      <div class="grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5">
        @for (i of skeletons; track i) {
          <app-media-card-skeleton />
        }
      </div>
    } @else if (state().data; as page) {
      @if (visible().length) {
        <div class="grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5">
          @for (item of visible(); track item.mediaType + item.id) {
            <app-media-card [item]="item" [showType]="type() === null" />
          }
        </div>
        <app-pagination [page]="page.page" [totalPages]="page.totalPages" (pageChange)="goToPage($event)" />
      } @else {
        <div class="card flex flex-col items-center px-6 py-16 text-center">
          <span class="grid size-16 place-items-center rounded-2xl bg-primary/10 text-primary ring-1 ring-primary/20">
            <app-icon name="bookmark" class="size-7" />
          </span>
          <p class="mt-5 font-display text-lg font-semibold">Your watchlist is empty</p>
          <p class="mt-1 max-w-sm text-sm text-muted">Tap the <strong class="text-foreground">+</strong> on any poster to save it here for later.</p>
          <div class="mt-6 flex flex-wrap justify-center gap-3">
            <a routerLink="/movies" class="btn btn-primary"><app-icon name="film" class="size-4" /> Browse movies</a>
            <a routerLink="/series" class="btn btn-secondary"><app-icon name="tv" class="size-4" /> Browse series</a>
          </div>
        </div>
      }
    }
  `,
})
export class WatchlistPage {
  private readonly watchlist = inject(WatchlistService);

  protected readonly filters = FILTERS;
  protected readonly skeletons = Array.from({ length: 8 }, (_, i) => i);
  protected readonly type = signal<MediaType | null>(null);
  private readonly page = signal(1);
  private readonly attempt = signal(0);

  protected readonly state = toSignal(
    combineLatest([toObservable(this.type), toObservable(this.page), toObservable(this.attempt)]).pipe(
      switchMap(([type, page]) => this.watchlist.list(type, page).pipe(toLoadState())),
    ),
    { initialValue: LOADING as LoadState<Page<WatchlistItem>> },
  );

  /** Items removed from this page (via the card button) disappear immediately. */
  protected readonly visible = computed<MediaSummary[]>(() =>
    (this.state().data?.content ?? [])
      .filter((item) => this.watchlist.isSaved(item.mediaType, item.tmdbId))
      .map(toSummary),
  );

  protected readonly subheading = computed(() => {
    const n = this.watchlist.count();
    return n === 0 ? 'Titles you save will appear here' : `${n} saved title${n > 1 ? 's' : ''}`;
  });

  protected setType(type: MediaType | null): void {
    this.type.set(type);
    this.page.set(1);
  }

  protected goToPage(page: number): void {
    this.page.set(page);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected retry(): void {
    this.attempt.update((n) => n + 1);
  }
}

function toSummary(item: WatchlistItem): MediaSummary {
  return {
    id: item.tmdbId,
    mediaType: item.mediaType,
    title: item.title,
    overview: null,
    posterUrl: item.posterUrl,
    backdropUrl: null,
    releaseDate: null,
    year: item.year,
    rating: null,
    voteCount: null,
    genreIds: [],
  };
}
