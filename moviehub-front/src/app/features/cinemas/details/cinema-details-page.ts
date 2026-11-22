import { Component, computed, effect, inject, input, linkedSignal, numberAttribute, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { Router, RouterLink } from '@angular/router';
import { combineLatest, switchMap } from 'rxjs';

import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { CinemaDetails, CinemaSchedule } from '../../../core/models/cinema';
import { CinemaService } from '../../../core/services/cinema.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { Icon } from '../../../shared/components/icon/icon';
import { RuntimePipe } from '../../../shared/pipes/runtime.pipe';
import { SCREEN_TYPE_BADGES, SCREEN_TYPE_LABELS, longDate, mapLink } from '../cinema-format';
import { CinemaMap, MapMarker } from '../components/cinema-map';
import { DateStrip } from '../components/date-strip';
import { ShowtimeChip } from '../components/showtime-chip';

@Component({
  selector: 'app-cinema-details-page',
  imports: [RouterLink, Icon, ErrorState, CinemaMap, DateStrip, ShowtimeChip, RuntimePipe],
  templateUrl: './cinema-details-page.html',
})
export class CinemaDetailsPage {
  private readonly cinemaService = inject(CinemaService);
  private readonly router = inject(Router);

  /** Route param :id */
  readonly id = input.required({ transform: numberAttribute });
  /** Query param ?date=yyyy-MM-dd */
  readonly date = input<string | undefined>();

  protected readonly labels = SCREEN_TYPE_LABELS;
  protected readonly badges = SCREEN_TYPE_BADGES;
  protected readonly longDate = longDate;
  private readonly attempt = signal(0);

  protected readonly cinema = toSignal(
    combineLatest([toObservable(this.id), toObservable(this.attempt)]).pipe(
      switchMap(([id]) => this.cinemaService.cinema(id).pipe(toLoadState())),
    ),
    { initialValue: LOADING as LoadState<CinemaDetails> },
  );

  protected readonly schedule = toSignal(
    combineLatest([toObservable(this.id), toObservable(this.date), toObservable(this.attempt)]).pipe(
      switchMap(([id, date]) => this.cinemaService.cinemaSchedule(id, date).pipe(toLoadState())),
    ),
    { initialValue: LOADING as LoadState<CinemaSchedule> },
  );

  /** Last loaded schedule, kept while another day loads so the date strip doesn't flicker. */
  protected readonly lastSchedule = linkedSignal<LoadState<CinemaSchedule>, CinemaSchedule | null>({
    source: this.schedule,
    computation: (state, previous) => state.data ?? previous?.value ?? null,
  });

  protected readonly markers = computed<MapMarker[]>(() => {
    const c = this.cinema().data;
    return c?.latitude != null && c.longitude != null
      ? [{ id: c.id, name: c.name, subtitle: `${c.address}, ${c.city}`, latitude: c.latitude, longitude: c.longitude }]
      : [];
  });

  protected readonly directions = computed(() => {
    const c = this.cinema().data;
    return c?.latitude != null && c.longitude != null ? mapLink(c.latitude, c.longitude) : null;
  });

  protected readonly seatCount = computed(() => (this.cinema().data?.rooms ?? []).reduce((sum, r) => sum + r.capacity, 0));

  protected readonly screenTypes = computed(() => [...new Set((this.cinema().data?.rooms ?? []).map((r) => r.screenType))]);

  constructor() {
    const title = inject(Title);
    effect(() => {
      const cinema = this.cinema().data;
      if (cinema) title.setTitle(`${cinema.name} · MovieHub`);
    });
  }

  protected selectDate(date: string): void {
    this.router.navigate([], { queryParams: { date }, replaceUrl: true });
  }

  protected retry(): void {
    this.attempt.update((n) => n + 1);
  }
}
