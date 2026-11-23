import { CurrencyPipe } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { combineLatest, switchMap } from 'rxjs';

import { errorMessage } from '../../../core/http/api-error.util';
import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { Booking } from '../../../core/models/booking';
import { BookingService } from '../../../core/services/booking.service';
import { ToastService } from '../../../core/ui/toast.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { Icon } from '../../../shared/components/icon/icon';
import { STATUS_BADGES, STATUS_LABELS, shortDateTime } from '../../booking/booking-format';
import { SCREEN_TYPE_LABELS, formatLabel, longDate, mapLink, timeOf, versionLabel } from '../../cinemas/cinema-format';

/** A booking as a ticket: QR code to show at the entrance, details, cancellation. */
@Component({
  selector: 'app-booking-details-page',
  imports: [RouterLink, Icon, ErrorState, CurrencyPipe],
  templateUrl: './booking-details-page.html',
  styles: `
    /* Ticket notches between the stub and the body. */
    .ticket-cut::before,
    .ticket-cut::after {
      content: '';
      position: absolute;
      width: 1.5rem;
      height: 1.5rem;
      border-radius: 9999px;
      background: var(--color-background);
    }
    .ticket-cut::before { left: -0.75rem; top: -0.75rem; }
    .ticket-cut::after { right: -0.75rem; top: -0.75rem; }
    @media (min-width: 768px) {
      .ticket-cut::before { left: -0.75rem; top: -0.75rem; }
      .ticket-cut::after { left: -0.75rem; top: auto; bottom: -0.75rem; right: auto; }
    }
    @media print {
      :host { color: #000; }
    }
  `,
})
export class BookingDetailsPage {
  private readonly bookings = inject(BookingService);
  private readonly toast = inject(ToastService);

  /** Route param :id */
  readonly id = input.required({ transform: numberAttribute });

  private readonly attempt = signal(0);
  /** Replaces the loaded booking after a cancellation. */
  private readonly updated = signal<Booking | null>(null);
  protected readonly qrUrl = signal<string | null>(null);
  protected readonly qrFailed = signal(false);
  protected readonly confirmingCancel = signal(false);
  protected readonly cancelling = signal(false);

  private readonly loaded = toSignal(
    combineLatest([toObservable(this.id), toObservable(this.attempt)]).pipe(
      switchMap(([id]) => this.bookings.get(id).pipe(toLoadState())),
    ),
    { initialValue: LOADING as LoadState<Booking> },
  );

  protected readonly state = computed<LoadState<Booking>>(() => {
    const updated = this.updated();
    return updated && updated.id === this.id() ? { loading: false, data: updated, error: null } : this.loaded();
  });

  protected readonly labels = STATUS_LABELS;
  protected readonly badges = STATUS_BADGES;

  protected readonly view = computed(() => {
    const b = this.state().data;
    if (!b) return null;
    const s = b.screening;
    return {
      day: longDate(s.localStart),
      time: `${timeOf(s.localStart)} – ${timeOf(s.localEnd)}`,
      start: timeOf(s.localStart),
      screen: `${s.room.name} · ${SCREEN_TYPE_LABELS[s.room.screenType]}`,
      version: `${formatLabel(s.format)} · ${versionLabel(s)}`,
      seats: b.seats.map((seat) => seat.label).join(', '),
      directions: s.cinema.latitude != null && s.cinema.longitude != null ? mapLink(s.cinema.latitude, s.cinema.longitude) : null,
      paidAt: b.paidAt ? shortDateTime(b.paidAt) : null,
      cancelledAt: b.cancelledAt ? shortDateTime(b.cancelledAt) : null,
      // In cinema time, like the showtime itself.
      cancellableUntil: b.cancellableUntil ? shortDateTime(b.cancellableUntil, s.cinema.timeZone) : null,
      checkedInAt: b.checkedInAt ? shortDateTime(b.checkedInAt) : null,
      past: new Date(s.startsAt).getTime() < Date.now(),
    };
  });

  constructor() {
    // Fetch the QR code (an authenticated image) for confirmed bookings.
    let objectUrl: string | null = null;
    const revoke = () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      objectUrl = null;
    };
    inject(DestroyRef).onDestroy(revoke);
    effect((onCleanup) => {
      const b = this.state().data;
      revoke();
      this.qrUrl.set(null);
      this.qrFailed.set(false);
      if (b?.status !== 'CONFIRMED') return;
      const sub = this.bookings.ticketQr(b.id).subscribe({
        next: (blob) => {
          objectUrl = URL.createObjectURL(blob);
          this.qrUrl.set(objectUrl);
        },
        error: () => this.qrFailed.set(true),
      });
      onCleanup(() => sub.unsubscribe());
    });
  }

  protected cancel(): void {
    const b = this.state().data;
    if (!b || this.cancelling()) return;
    this.cancelling.set(true);
    this.bookings.cancel(b.id).subscribe({
      next: (booking) => {
        this.updated.set(booking);
        this.cancelling.set(false);
        this.confirmingCancel.set(false);
        this.toast.success(booking.refunded ? 'Booking cancelled. You will be refunded.' : 'Booking cancelled.');
      },
      error: (e: unknown) => {
        this.cancelling.set(false);
        this.confirmingCancel.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }

  protected print(): void {
    window.print();
  }

  protected retry(): void {
    this.updated.set(null);
    this.attempt.update((n) => n + 1);
  }
}
