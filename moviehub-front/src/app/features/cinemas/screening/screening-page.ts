import { CurrencyPipe } from '@angular/common';
import { Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { combineLatest, switchMap } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { ScreeningDetails, SeatMap } from '../../../core/models/cinema';
import { CinemaService } from '../../../core/services/cinema.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { Icon, IconName } from '../../../shared/components/icon/icon';
import { RuntimePipe } from '../../../shared/pipes/runtime.pipe';
import { SCREEN_TYPE_BADGES, SCREEN_TYPE_LABELS, formatLabel, longDate, timeOf, versionLongLabel } from '../cinema-format';
import { SeatMapView } from '../components/seat-map';

interface Fact {
  icon: IconName;
  label: string;
  value: string;
}

@Component({
  selector: 'app-screening-page',
  imports: [RouterLink, Icon, ErrorState, SeatMapView, CurrencyPipe, RuntimePipe],
  templateUrl: './screening-page.html',
})
export class ScreeningPage {
  private readonly cinemaService = inject(CinemaService);

  /** Route param :id */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly currency = environment.currency;
  protected readonly labels = SCREEN_TYPE_LABELS;
  protected readonly badges = SCREEN_TYPE_BADGES;
  private readonly attempt = signal(0);

  private readonly request = combineLatest([toObservable(this.id), toObservable(this.attempt)]);

  protected readonly state = toSignal(
    this.request.pipe(switchMap(([id]) => this.cinemaService.screening(id).pipe(toLoadState()))),
    { initialValue: LOADING as LoadState<ScreeningDetails> },
  );

  protected readonly seats = toSignal(
    this.request.pipe(switchMap(([id]) => this.cinemaService.seats(id).pipe(toLoadState()))),
    { initialValue: LOADING as LoadState<SeatMap> },
  );

  protected readonly facts = computed<Fact[]>(() => {
    const s = this.state().data;
    if (!s) return [];
    return [
      { icon: 'calendar', label: 'Date', value: longDate(s.localStart) },
      { icon: 'clock', label: 'Time', value: `${timeOf(s.localStart)} – ${timeOf(s.localEnd)}` },
      { icon: 'seat', label: 'Screen', value: `${s.room.name} · ${SCREEN_TYPE_LABELS[s.room.screenType]}` },
      { icon: 'globe', label: 'Version', value: `${versionLongLabel(s)} · ${formatLabel(s.format)}` },
    ];
  });

  /** Why tickets can't be bought, or null when they can. */
  protected readonly unavailableReason = computed(() => {
    const s = this.state().data;
    if (!s || s.bookable) return null;
    if (s.status === 'CANCELLED') return 'This screening has been cancelled.';
    return 'This screening has already started.';
  });

  constructor() {
    const title = inject(Title);
    effect(() => {
      const s = this.state().data;
      if (s) title.setTitle(`${s.movie.title} · ${timeOf(s.localStart)} · MovieHub`);
    });
  }

  protected retry(): void {
    this.attempt.update((n) => n + 1);
  }
}
