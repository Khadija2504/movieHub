import {
  Component,
  computed,
  effect,
  inject,
  input,
  linkedSignal,
  numberAttribute,
  signal,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { combineLatest, of, switchMap } from 'rxjs';

import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { SeasonDetails, SeriesDetails } from '../../../core/models/catalog';
import { CatalogService } from '../../../core/services/catalog.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { MediaCarousel } from '../../../shared/components/media-carousel/media-carousel';
import { TrailerModal } from '../../../shared/components/trailer-modal/trailer-modal';
import { RuntimePipe } from '../../../shared/pipes/runtime.pipe';
import { Icon } from '../../../shared/components/icon/icon';
import { WatchlistButton } from '../../../shared/components/watchlist-button/watchlist-button';
import { CastRow } from '../components/cast-row';
import { ReviewsSection } from '../components/reviews-section';
import { DetailsSkeleton } from '../components/details-skeleton';
import { InfoItem, InfoList } from '../components/info-list';
import { HeroFact, TitleHero } from '../components/title-hero';

@Component({
  selector: 'app-series-details-page',
  imports: [TitleHero, CastRow, ReviewsSection, WatchlistButton, InfoList, MediaCarousel, TrailerModal, ErrorState, DetailsSkeleton, RuntimePipe, Icon],
  templateUrl: './series-details-page.html',
})
export class SeriesDetailsPage {
  private readonly catalog = inject(CatalogService);

  readonly id = input.required({ transform: numberAttribute });

  protected readonly trailerOpen = signal(false);
  private readonly attempt = signal(0);

  protected readonly state = toSignal(
    combineLatest([toObservable(this.id), toObservable(this.attempt)]).pipe(
      switchMap(([id]) => this.catalog.series(id).pipe(toLoadState())),
    ),
    { initialValue: LOADING as LoadState<SeriesDetails> },
  );

  /**
   * Season picked by the user; defaults to the first regular season (not "Specials" = 0).
   * linkedSignal resets the choice when the route id changes (clicking a recommended series).
   */
  private readonly pickedSeason = linkedSignal<number, number | null>({
    source: this.id,
    computation: () => null,
  });
  protected readonly selectedSeason = computed(() => {
    const picked = this.pickedSeason();
    if (picked !== null) return picked;
    const seasons = this.state().data?.seasons ?? [];
    return (seasons.find((s) => s.seasonNumber > 0) ?? seasons[0])?.seasonNumber ?? null;
  });

  private readonly seasonAttempt = signal(0);
  protected readonly season = toSignal(
    combineLatest([
      toObservable(computed(() => this.state().data?.id ?? null)),
      toObservable(this.selectedSeason),
      toObservable(this.seasonAttempt),
    ]).pipe(
      switchMap(([seriesId, seasonNumber]) =>
        seriesId !== null && seasonNumber !== null
          ? this.catalog.season(seriesId, seasonNumber).pipe(toLoadState())
          : of(LOADING as LoadState<SeasonDetails>),
      ),
    ),
    { initialValue: LOADING as LoadState<SeasonDetails> },
  );

  private readonly years = computed(() => {
    const s = this.state().data;
    const start = s?.firstAirDate ? new Date(s.firstAirDate).getFullYear() : null;
    const end = s?.status === 'Ended' && s.lastAirDate ? new Date(s.lastAirDate).getFullYear() : null;
    if (!start) return null;
    return end && end !== start ? `${start}–${end}` : s?.status === 'Ended' ? `${start}` : `${start}–`;
  });

  protected readonly facts = computed<HeroFact[]>(() => {
    const s = this.state().data;
    if (!s) return [];
    const facts: HeroFact[] = [];
    const years = this.years();
    if (years) facts.push({ icon: 'calendar', label: years });
    if (s.numberOfSeasons) facts.push({ icon: 'tv', label: `${s.numberOfSeasons} season${s.numberOfSeasons > 1 ? 's' : ''}` });
    if (s.episodeRuntime) facts.push({ icon: 'clock', label: `${s.episodeRuntime}m / episode` });
    return facts;
  });

  protected readonly info = computed<InfoItem[]>(() => {
    const s = this.state().data;
    if (!s) return [];
    return [
      { label: s.creators.length > 1 ? 'Creators' : 'Creator', value: s.creators.map((c) => c.name).join(', ') },
      { label: 'Status', value: s.status },
      { label: 'Aired', value: this.years() },
      { label: 'Seasons', value: s.numberOfSeasons?.toString() },
      { label: 'Episodes', value: s.numberOfEpisodes?.toString() },
      { label: 'Genres', value: s.genres.map((g) => g.name).join(', ') },
    ];
  });

  constructor() {
    const title = inject(Title);
    effect(() => {
      const series = this.state().data;
      if (series) title.setTitle(`${series.title} · MovieHub`);
    });
  }

  protected selectSeason(seasonNumber: number): void {
    this.pickedSeason.set(seasonNumber);
  }

  protected retry(): void {
    this.attempt.update((n) => n + 1);
  }

  protected retrySeason(): void {
    this.seasonAttempt.update((n) => n + 1);
  }
}
