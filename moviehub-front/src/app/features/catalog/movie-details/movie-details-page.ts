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
import { MediaCarousel } from '../../../shared/components/media-carousel/media-carousel';
import { TrailerModal } from '../../../shared/components/trailer-modal/trailer-modal';
import { RuntimePipe } from '../../../shared/pipes/runtime.pipe';
import { CastRow } from '../components/cast-row';
import { DetailsSkeleton } from '../components/details-skeleton';
import { TitleHero } from '../components/title-hero';

@Component({
  selector: 'app-movie-details-page',
  imports: [TitleHero, CastRow, MediaCarousel, TrailerModal, ErrorState, DetailsSkeleton, CurrencyPipe],
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

  protected readonly facts = computed(() => {
    const movie = this.state().data;
    if (!movie) return [];
    return [
      movie.releaseDate ? new Date(movie.releaseDate).toLocaleDateString(undefined, { dateStyle: 'medium' }) : null,
      new RuntimePipe().transform(movie.runtime) || null,
      movie.originalLanguage?.toUpperCase() ?? null,
    ].filter((f): f is string => !!f);
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
