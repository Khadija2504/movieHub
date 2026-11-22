import { DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, catchError, map, of, switchMap } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { Genre, MediaSummary } from '../../../core/models/catalog';
import { CatalogService } from '../../../core/services/catalog.service';
import { CinemaService } from '../../../core/services/cinema.service';
import { Icon } from '../../../shared/components/icon/icon';
import { MediaCard } from '../../../shared/components/media-card/media-card';
import { MediaCarousel } from '../../../shared/components/media-carousel/media-carousel';

const HERO_SLIDES = 5;
const HERO_INTERVAL_MS = 7000;

/** Gradient per genre tile (cycled). */
const GENRE_GRADIENTS = [
  'from-rose-500/80 to-orange-500/60',
  'from-violet-600/80 to-fuchsia-500/60',
  'from-sky-500/80 to-indigo-600/60',
  'from-emerald-500/80 to-teal-700/60',
  'from-amber-500/80 to-red-600/60',
  'from-pink-500/80 to-purple-700/60',
  'from-cyan-500/80 to-blue-700/60',
  'from-lime-500/70 to-emerald-700/60',
];

@Component({
  selector: 'app-home-page',
  imports: [RouterLink, MediaCarousel, MediaCard, Icon, DecimalPipe],
  templateUrl: './home-page.html',
})
export class HomePage {
  private readonly catalog = inject(CatalogService);
  protected readonly auth = inject(AuthService);

  protected readonly trending = this.row(() => this.catalog.trending());
  protected readonly popularMovies = this.row(() => this.catalog.browse('MOVIE', { sort: 'POPULAR' }));
  protected readonly popularSeries = this.row(() => this.catalog.browse('SERIES', { sort: 'POPULAR' }));
  protected readonly topRatedMovies = this.row(() => this.catalog.browse('MOVIE', { sort: 'TOP_RATED' }));

  /** Movies with upcoming screenings (row hidden when there are none). */
  protected readonly nowShowing = toSignal(
    inject(CinemaService).nowShowing().pipe(catchError(() => of<MediaSummary[]>([]))),
    { initialValue: [] as MediaSummary[] },
  );

  protected readonly genres = toSignal(
    this.catalog.genres('MOVIE').pipe(catchError(() => of<Genre[]>([]))),
    { initialValue: [] as Genre[] },
  );
  protected readonly genreTiles = computed(() =>
    this.genres()
      .slice(0, 8)
      .map((genre, i) => ({ ...genre, gradient: GENRE_GRADIENTS[i % GENRE_GRADIENTS.length] })),
  );

  protected readonly topTen = computed(() => (this.trending.state().data ?? []).slice(0, 10));

  // ---------------------------------------------------------------- hero carousel

  protected readonly heroSlides = computed(() =>
    (this.trending.state().data ?? []).filter((item) => item.backdropUrl).slice(0, HERO_SLIDES),
  );
  protected readonly heroIndex = signal(0);
  protected readonly heroPaused = signal(false);
  protected readonly hero = computed(() => this.heroSlides()[this.heroIndex()] ?? null);

  // Seat map illustration for the cinema promo (purely decorative).
  protected readonly seatRows = Array.from({ length: 6 }, (_, r) =>
    Array.from({ length: 12 }, (_, c) => {
      if (r === 3 && (c === 5 || c === 6)) return 'selected';
      return (r * 7 + c * 3) % 5 === 0 ? 'taken' : 'free';
    }),
  );

  constructor() {
    // Auto-advance the hero; restarts whenever the slide changes or the carousel is paused/resumed.
    effect((onCleanup) => {
      const count = this.heroSlides().length;
      this.heroIndex();
      if (count < 2 || this.heroPaused()) return;
      const timer = setTimeout(() => this.nextSlide(), HERO_INTERVAL_MS);
      onCleanup(() => clearTimeout(timer));
    });
  }

  protected goToSlide(index: number): void {
    this.heroIndex.set(index);
  }

  protected nextSlide(): void {
    const count = this.heroSlides().length;
    if (count) this.heroIndex.update((i) => (i + 1) % count);
  }

  protected prevSlide(): void {
    const count = this.heroSlides().length;
    if (count) this.heroIndex.update((i) => (i - 1 + count) % count);
  }

  protected detailsLink(item: MediaSummary): (string | number)[] {
    return [item.mediaType === 'MOVIE' ? '/movies' : '/series', item.id];
  }

  /** One independently loading (and retryable) row of titles. */
  private row(request: () => Observable<{ content: MediaSummary[] }>) {
    const attempt = signal(0);
    const state = toSignal(
      toObservable(attempt).pipe(
        switchMap(() => request().pipe(map((page) => page.content), toLoadState())),
      ),
      { initialValue: LOADING as LoadState<MediaSummary[]> },
    );
    return { state, retry: () => attempt.update((n) => n + 1) };
  }
}
