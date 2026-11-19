import { Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { combineLatest, of, switchMap } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { BrowseFilter, Genre, MediaSummary, MediaType, Page, SortOption } from '../../../core/models/catalog';
import { CatalogService } from '../../../core/services/catalog.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { MediaCard, MediaCardSkeleton } from '../../../shared/components/media-card/media-card';
import { Pagination } from '../../../shared/components/pagination/pagination';

const SORTS: { value: SortOption; label: string }[] = [
  { value: 'POPULAR', label: 'Most popular' },
  { value: 'TOP_RATED', label: 'Top rated' },
  { value: 'NEWEST', label: 'Newest' },
  { value: 'TITLE', label: 'Title A–Z' },
];
const RATINGS = [5, 6, 7, 8];

const optionalNumber = (value: unknown) => (value == null || value === '' ? undefined : numberAttribute(value));

/**
 * Movies and TV Series browse page (the route's data.mediaType picks which).
 * All filters live in the URL query string, so results are shareable and survive reloads.
 */
@Component({
  selector: 'app-browse-page',
  imports: [MediaCard, MediaCardSkeleton, Pagination, ErrorState],
  templateUrl: './browse-page.html',
})
export class BrowsePage {
  private readonly catalog = inject(CatalogService);
  private readonly router = inject(Router);

  /** From route data. */
  readonly mediaType = input.required<MediaType>();
  /** From query params. */
  readonly genre = input(undefined, { transform: optionalNumber });
  readonly year = input(undefined, { transform: optionalNumber });
  readonly minRating = input(undefined, { transform: optionalNumber });
  readonly sort = input<SortOption | undefined>(undefined);
  readonly page = input(1, { transform: (v: unknown) => optionalNumber(v) ?? 1 });

  protected readonly sorts = SORTS;
  protected readonly ratings = RATINGS;
  protected readonly years = Array.from({ length: new Date().getFullYear() + 2 - 1950 }, (_, i) => new Date().getFullYear() + 1 - i);
  protected readonly skeletons = Array.from({ length: 18 }, (_, i) => i);

  protected readonly heading = computed(() => (this.mediaType() === 'MOVIE' ? 'Movies' : 'TV Series'));

  protected readonly filter = computed<BrowseFilter>(() => ({
    genre: this.genre(),
    year: this.year(),
    minRating: this.minRating(),
    sort: this.sort(),
    page: this.page(),
  }));

  protected readonly hasFilters = computed(
    () => !!(this.genre() || this.year() || this.minRating() || (this.sort() && this.sort() !== 'POPULAR')),
  );

  private readonly attempt = signal(0);

  protected readonly results = toSignal(
    combineLatest([toObservable(this.mediaType), toObservable(this.filter), toObservable(this.attempt)]).pipe(
      switchMap(([type, filter]) => this.catalog.browse(type, filter).pipe(toLoadState())),
    ),
    { initialValue: LOADING as LoadState<Page<MediaSummary>> },
  );

  protected readonly genres = toSignal(
    toObservable(this.mediaType).pipe(
      switchMap((type) => this.catalog.genres(type).pipe(catchError(() => of<Genre[]>([])))),
    ),
    { initialValue: [] as Genre[] },
  );

  protected retry(): void {
    this.attempt.update((n) => n + 1);
  }

  /** Changing a filter resets to page 1. */
  protected setFilter(key: 'genre' | 'year' | 'minRating' | 'sort', value: string): void {
    void this.router.navigate([], {
      queryParams: { [key]: value || null, page: null },
      queryParamsHandling: 'merge',
    });
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], { queryParams: { page }, queryParamsHandling: 'merge' });
  }

  protected clearFilters(): void {
    void this.router.navigate([], { queryParams: {} });
  }
}
