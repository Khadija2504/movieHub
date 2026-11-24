import { CurrencyPipe } from '@angular/common';
import { Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { catchError, combineLatest, of, switchMap } from 'rxjs';
import { Router, RouterLink } from '@angular/router';

import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage } from '../../../core/http/api-error.util';
import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { MovieDetails } from '../../../core/models/catalog';
import { MovieAccess } from '../../../core/models/streaming';
import { CatalogService } from '../../../core/services/catalog.service';
import { StoreService } from '../../../core/services/store.service';
import { StreamingService } from '../../../core/services/streaming.service';
import { ToastService } from '../../../core/ui/toast.service';
import { clock, timeLeft } from '../../streaming/streaming-format';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { Icon } from '../../../shared/components/icon/icon';
import { MediaCarousel } from '../../../shared/components/media-carousel/media-carousel';
import { TrailerModal } from '../../../shared/components/trailer-modal/trailer-modal';
import { RuntimePipe } from '../../../shared/pipes/runtime.pipe';
import { WatchlistButton } from '../../../shared/components/watchlist-button/watchlist-button';
import { MovieShowtimes } from '../../cinemas/components/movie-showtimes';
import { CastRow } from '../components/cast-row';
import { ReviewsSection } from '../components/reviews-section';
import { DetailsSkeleton } from '../components/details-skeleton';
import { InfoItem, InfoList } from '../components/info-list';
import { HeroFact, TitleHero } from '../components/title-hero';

const runtime = new RuntimePipe();
const languageNames = new Intl.DisplayNames(['en'], { type: 'language' });

@Component({
  selector: 'app-movie-details-page',
  imports: [RouterLink, TitleHero, MovieShowtimes, CastRow, ReviewsSection, WatchlistButton, InfoList, MediaCarousel, TrailerModal, ErrorState, DetailsSkeleton, CurrencyPipe, Icon],
  templateUrl: './movie-details-page.html',
})
export class MovieDetailsPage {
  private readonly catalog = inject(CatalogService);
  private readonly auth = inject(AuthService);
  private readonly streaming = inject(StreamingService);
  private readonly store = inject(StoreService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  /** Route param :id (TMDB id). */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly currency = environment.currency;
  protected readonly trailerOpen = signal(false);
  /** Set by the showtimes section: the movie has upcoming screenings. */
  protected readonly inCinemas = signal(false);
  private readonly attempt = signal(0);

  protected readonly state = toSignal(
    combineLatest([toObservable(this.id), toObservable(this.attempt)]).pipe(
      switchMap(([id]) => this.catalog.movie(id).pipe(toLoadState())),
    ),
    { initialValue: LOADING as LoadState<MovieDetails> },
  );

  /** What the logged-in user can do with this movie (null: logged out, not streamable, or loading). */
  protected readonly access = toSignal(
    combineLatest([toObservable(this.state), toObservable(this.attempt)]).pipe(
      switchMap(([state]) => {
        const movie = state.data;
        if (!movie?.offer.streamable || !this.auth.isAuthenticated()) return of(null);
        return this.streaming.access(movie.id).pipe(catchError(() => of(null)));
      }),
    ),
    { initialValue: null as MovieAccess | null },
  );
  /** "Rent" or "Buy" while its order is being created. */
  protected readonly ordering = signal<'RENTAL' | 'PURCHASE' | null>(null);

  protected readonly rentalLeft = computed(() => timeLeft(this.access()?.rentalEndsAt ?? null, Date.now()));
  protected readonly resumeLabel = computed(() => {
    const access = this.access();
    return access && access.resumeAt > 0 ? `Resume from ${clock(access.resumeAt)}` : 'Watch now';
  });

  protected readonly facts = computed<HeroFact[]>(() => {
    const movie = this.state().data;
    if (!movie) return [];
    const facts: HeroFact[] = [];
    if (movie.year) facts.push({ icon: 'calendar', label: String(movie.year) });
    if (movie.runtime) facts.push({ icon: 'clock', label: runtime.transform(movie.runtime) });
    if (movie.originalLanguage) facts.push({ icon: 'globe', label: language(movie.originalLanguage) });
    return facts;
  });

  protected readonly info = computed<InfoItem[]>(() => {
    const movie = this.state().data;
    if (!movie) return [];
    return [
      { label: movie.directors.length > 1 ? 'Directors' : 'Director', value: movie.directors.map((d) => d.name).join(', ') },
      { label: 'Release date', value: movie.releaseDate ? formatDate(movie.releaseDate) : null },
      { label: 'Runtime', value: runtime.transform(movie.runtime) },
      { label: 'Status', value: movie.status },
      { label: 'Original language', value: movie.originalLanguage ? language(movie.originalLanguage) : null },
      { label: 'Genres', value: movie.genres.map((g) => g.name).join(', ') },
    ];
  });

  constructor() {
    const title = inject(Title);
    effect(() => {
      const movie = this.state().data;
      if (movie) title.setTitle(`${movie.title} · MovieHub`);
    });
  }

  protected retry(): void {
    this.attempt.update((n) => n + 1);
  }

  /** Starts a rental or purchase, then goes to its checkout. */
  protected order(kind: 'RENTAL' | 'PURCHASE', tmdbId: number): void {
    if (!this.auth.isAuthenticated()) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: `/movies/${tmdbId}` } });
      return;
    }
    this.ordering.set(kind);
    this.store.orderMovie(kind, tmdbId).subscribe({
      next: (order) => void this.router.navigate(['/pay', order.id]),
      error: (e: unknown) => {
        this.ordering.set(null);
        this.toast.error(errorMessage(e));
      },
    });
  }

  protected scrollToWatchOptions(): void {
    document.getElementById('watch-options')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  protected scrollToShowtimes(): void {
    document.getElementById('showtimes')?.scrollIntoView({ behavior: 'smooth' });
  }
}

function language(code: string): string {
  try {
    return languageNames.of(code) ?? code.toUpperCase();
  } catch {
    return code.toUpperCase();
  }
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en', { dateStyle: 'long' });
}
