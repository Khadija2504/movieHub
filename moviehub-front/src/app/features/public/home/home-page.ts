import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, map, switchMap } from 'rxjs';

import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { MediaSummary } from '../../../core/models/catalog';
import { CatalogService } from '../../../core/services/catalog.service';
import { MediaCarousel } from '../../../shared/components/media-carousel/media-carousel';

@Component({
  selector: 'app-home-page',
  imports: [RouterLink, MediaCarousel],
  templateUrl: './home-page.html',
})
export class HomePage {
  private readonly catalog = inject(CatalogService);

  protected readonly trending = this.row(() => this.catalog.trending());
  protected readonly popularMovies = this.row(() => this.catalog.browse('MOVIE', { sort: 'POPULAR' }));
  protected readonly popularSeries = this.row(() => this.catalog.browse('SERIES', { sort: 'POPULAR' }));
  protected readonly topRatedMovies = this.row(() => this.catalog.browse('MOVIE', { sort: 'TOP_RATED' }));

  /** Hero: the first trending title that has a backdrop image. */
  protected readonly featured = computed(
    () => this.trending.state().data?.find((item) => item.backdropUrl) ?? null,
  );

  /** One independently loading (and retryable) carousel row. */
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
