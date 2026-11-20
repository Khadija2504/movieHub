import { CurrencyPipe } from '@angular/common';
import { Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { combineLatest, switchMap } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { MovieDetails } from '../../../core/models/catalog';
import { CatalogService } from '../../../core/services/catalog.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { Icon } from '../../../shared/components/icon/icon';
import { MediaCarousel } from '../../../shared/components/media-carousel/media-carousel';
import { TrailerModal } from '../../../shared/components/trailer-modal/trailer-modal';
import { RuntimePipe } from '../../../shared/pipes/runtime.pipe';
import { CastRow } from '../components/cast-row';
import { DetailsSkeleton } from '../components/details-skeleton';
import { InfoItem, InfoList } from '../components/info-list';
import { HeroFact, TitleHero } from '../components/title-hero';

const runtime = new RuntimePipe();
const languageNames = new Intl.DisplayNames(['en'], { type: 'language' });

@Component({
  selector: 'app-movie-details-page',
  imports: [TitleHero, CastRow, InfoList, MediaCarousel, TrailerModal, ErrorState, DetailsSkeleton, CurrencyPipe, Icon],
  templateUrl: './movie-details-page.html',
})
export class MovieDetailsPage {
  private readonly catalog = inject(CatalogService);

  /** Route param :id (TMDB id). */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly currency = environment.currency;
  protected readonly trailerOpen = signal(false);
  private readonly attempt = signal(0);

  protected readonly state = toSignal(
    combineLatest([toObservable(this.id), toObservable(this.attempt)]).pipe(
      switchMap(([id]) => this.catalog.movie(id).pipe(toLoadState())),
    ),
    { initialValue: LOADING as LoadState<MovieDetails> },
  );

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
