import { CurrencyPipe } from '@angular/common';
import { Component, computed, effect, inject, input, numberAttribute, signal, untracked } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { Router, RouterLink } from '@angular/router';
import { combineLatest, switchMap } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage } from '../../../core/http/api-error.util';
import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { ScreeningDetails, SeatMap, SeatView } from '../../../core/models/cinema';
import { BookingService } from '../../../core/services/booking.service';
import { CinemaService } from '../../../core/services/cinema.service';
import { ToastService } from '../../../core/ui/toast.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { Icon, IconName } from '../../../shared/components/icon/icon';
import { RuntimePipe } from '../../../shared/pipes/runtime.pipe';
import { SCREEN_TYPE_BADGES, SCREEN_TYPE_LABELS, formatLabel, longDate, timeOf, versionLongLabel } from '../cinema-format';
import { SeatMapView } from '../components/seat-map';

/** Same limit as the backend (BookingRules.MAX_SEATS). */
export const MAX_SEATS = 10;

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
  private readonly bookingService = inject(BookingService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  /** Route param :id */
  readonly id = input.required({ transform: numberAttribute });
  /** Query param ?seats=12,13: the selection is restored after logging in. */
  readonly seatsParam = input<string | undefined>(undefined, { alias: 'seats' });

  protected readonly currency = environment.currency;
  protected readonly labels = SCREEN_TYPE_LABELS;
  protected readonly badges = SCREEN_TYPE_BADGES;
  protected readonly maxSeats = MAX_SEATS;
  private readonly attempt = signal(0);
  private readonly seatsVersion = signal(0);

  protected readonly selectedIds = signal<number[]>([]);
  protected readonly holding = signal(false);

  protected readonly state = toSignal(
    combineLatest([toObservable(this.id), toObservable(this.attempt)]).pipe(
      switchMap(([id]) => this.cinemaService.screening(id).pipe(toLoadState())),
    ),
    { initialValue: LOADING as LoadState<ScreeningDetails> },
  );

  protected readonly seats = toSignal(
    combineLatest([toObservable(this.id), toObservable(this.attempt), toObservable(this.seatsVersion)]).pipe(
      switchMap(([id]) => this.cinemaService.seats(id).pipe(toLoadState())),
    ),
    { initialValue: LOADING as LoadState<SeatMap> },
  );

  /** Chosen seats, in seat order. */
  protected readonly selectedSeats = computed<SeatView[]>(() => {
    const chosen = new Set(this.selectedIds());
    return (this.seats().data?.seats ?? []).filter((s) => chosen.has(s.id));
  });

  protected readonly seatLabels = computed(() => this.selectedSeats().map((s) => s.label).join(', '));

  protected readonly total = computed(() => {
    const s = this.state().data;
    if (!s) return 0;
    return this.selectedSeats().reduce((sum, seat) => sum + (seat.type === 'VIP' ? s.vipPrice : s.price), 0);
  });

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

  protected readonly soldOut = computed(() => this.seats().data?.available === 0 && !this.selectedIds().length);

  constructor() {
    const title = inject(Title);
    effect(() => {
      const s = this.state().data;
      if (s) title.setTitle(`${s.movie.title} · ${timeOf(s.localStart)} · MovieHub`);
    });

    // Whenever the seat map (re)loads, keep only chosen seats that are still free; restore ?seats= once.
    let restored = false;
    effect(() => {
      const map = this.seats().data;
      if (!map) return;
      const free = new Set(map.seats.filter((s) => s.status === 'AVAILABLE').map((s) => s.id));
      untracked(() => {
        let ids = this.selectedIds();
        if (!restored) {
          restored = true;
          ids = (this.seatsParam() ?? '').split(',').map(Number).filter((n) => Number.isInteger(n) && n > 0);
        }
        this.selectedIds.set(ids.filter((id) => free.has(id)).slice(0, MAX_SEATS));
      });
    });
  }

  protected toggle(seat: SeatView): void {
    this.selectedIds.update((ids) => {
      if (ids.includes(seat.id)) return ids.filter((id) => id !== seat.id);
      if (ids.length >= MAX_SEATS) {
        this.toast.info(`You can book up to ${MAX_SEATS} seats at once.`);
        return ids;
      }
      return [...ids, seat.id];
    });
  }

  protected clearSelection(): void {
    this.selectedIds.set([]);
  }

  /** Holds the chosen seats and goes to the checkout (via the login page if needed). */
  protected continueToPayment(): void {
    const ids = this.selectedSeats().map((s) => s.id);
    if (!ids.length || this.holding()) return;
    if (!this.auth.isAuthenticated()) {
      this.toast.info('Log in to book your seats.');
      void this.router.navigate(['/login'], {
        queryParams: { returnUrl: `/screenings/${this.id()}?seats=${ids.join(',')}` },
      });
      return;
    }
    this.holding.set(true);
    this.bookingService.create(this.id(), ids).subscribe({
      next: (booking) => void this.router.navigate(['/checkout', booking.id]),
      error: (error: unknown) => {
        this.holding.set(false);
        this.toast.error(errorMessage(error, 'Your seats could not be held. Please try again.'));
        // Someone may have taken some of the seats: show the current map.
        this.seatsVersion.update((n) => n + 1);
      },
    });
  }

  protected retry(): void {
    this.attempt.update((n) => n + 1);
  }
}
