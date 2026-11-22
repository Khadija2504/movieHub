import { Component, computed, effect, inject, input, linkedSignal, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { combineLatest, switchMap } from 'rxjs';

import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { MovieSchedule } from '../../../core/models/cinema';
import { CinemaService } from '../../../core/services/cinema.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { Icon } from '../../../shared/components/icon/icon';
import { DateStrip } from './date-strip';
import { ShowtimeChip } from './showtime-chip';

/**
 * "Showtimes" section of a movie page: every cinema showing the movie on a day, with a city filter.
 * Renders nothing when the movie has no upcoming screening.
 */
@Component({
  selector: 'app-movie-showtimes',
  imports: [RouterLink, Icon, ErrorState, DateStrip, ShowtimeChip],
  host: { class: 'block' },
  template: `
    @if (shown(); as s) {
      <section id="showtimes" class="scroll-mt-28 py-8" aria-labelledby="showtimes-heading">
        <div class="mb-5 flex flex-wrap items-center justify-between gap-4">
          <div class="flex items-center gap-3">
            <span class="grid size-9 place-items-center rounded-xl bg-primary/12 text-primary ring-1 ring-primary/20">
              <app-icon name="ticket" class="size-[1.1rem]" />
            </span>
            <div>
              <h2 id="showtimes-heading" class="section-title">In cinemas</h2>
              <p class="mt-0.5 text-sm text-muted">Pick a day and a showtime to book your seats</p>
            </div>
          </div>
          @if (s.cities.length > 1) {
            <label class="sr-only" for="showtimes-city">City</label>
            <select id="showtimes-city" class="input h-10! w-44! rounded-full!" (change)="city.set($any($event.target).value || null)">
              <option value="" [selected]="!city()">All cities</option>
              @for (c of s.cities; track c) {
                <option [value]="c" [selected]="c === city()">{{ c }}</option>
              }
            </select>
          }
        </div>

        <app-date-strip [dates]="s.dates" [selected]="date() ?? s.date" (select)="date.set($event)" />

        <div class="mt-5 space-y-3" [class.opacity-60]="state().loading">
          @if (state().error; as error) {
            <app-error-state [message]="error" (retry)="retry()" compact />
          }
          @for (entry of s.cinemas; track entry.cinema.id) {
            <article class="card p-4 sm:p-5">
              <div class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
                <a [routerLink]="['/cinemas', entry.cinema.id]" class="font-display font-bold hover:text-primary">{{ entry.cinema.name }}</a>
                <p class="flex items-center gap-1 text-xs text-muted"><app-icon name="map-pin" class="size-3.5" /> {{ entry.cinema.address }}, {{ entry.cinema.city }}</p>
              </div>
              <div class="mt-3 flex flex-wrap gap-2">
                @for (showtime of entry.showtimes; track showtime.id) {
                  <app-showtime-chip [showtime]="showtime" />
                }
              </div>
            </article>
          } @empty {
            <p class="card px-5 py-8 text-center text-sm text-muted">No showtimes on this day. Try another day.</p>
          }
        </div>
      </section>
    }
  `,
})
export class MovieShowtimes {
  private readonly cinemaService = inject(CinemaService);

  readonly tmdbId = input.required<number>();
  /** Emits whether the movie has upcoming showtimes (to show a "Get tickets" shortcut). */
  readonly available = output<boolean>();

  protected readonly date = signal<string | null>(null);
  protected readonly city = signal<string | null>(null);
  private readonly attempt = signal(0);

  protected readonly state = toSignal(
    combineLatest([toObservable(this.tmdbId), toObservable(this.date), toObservable(this.city), toObservable(this.attempt)]).pipe(
      switchMap(([id, date, city]) => this.cinemaService.movieSchedule(id, date, city).pipe(toLoadState())),
    ),
    { initialValue: LOADING as LoadState<MovieSchedule> },
  );

  /** Last loaded schedule, kept while the next one loads. Null (section hidden) when nothing is showing. */
  protected readonly shown = linkedSignal<LoadState<MovieSchedule>, MovieSchedule | null>({
    source: this.state,
    computation: (state, previous) => {
      const data = state.data ?? previous?.value ?? null;
      return data && (data.dates.length || data.cities.length) ? data : null;
    },
  });

  private readonly hasShowtimes = computed(() => !!this.shown());

  constructor() {
    // A different movie resets the filters.
    effect(() => {
      this.tmdbId();
      this.date.set(null);
      this.city.set(null);
    });
    effect(() => this.available.emit(this.hasShowtimes()));
    // When the city changes, the selected day may not exist there: let the server pick the first one.
    effect(() => {
      this.city();
      this.date.set(null);
    });
  }

  protected retry(): void {
    this.attempt.update((n) => n + 1);
  }
}
