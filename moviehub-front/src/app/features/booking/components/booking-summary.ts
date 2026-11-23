import { CurrencyPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Booking } from '../../../core/models/booking';
import { Icon } from '../../../shared/components/icon/icon';
import { SCREEN_TYPE_LABELS, formatLabel, longDate, timeOf, versionLabel } from '../../cinemas/cinema-format';

/** Movie, showtime, seats and total of a booking (checkout and ticket pages). */
@Component({
  selector: 'app-booking-summary',
  imports: [RouterLink, Icon, CurrencyPipe],
  host: { class: 'block' },
  template: `
    @let b = booking();
    @let s = b.screening;
    <div class="flex gap-4">
      <a [routerLink]="['/movies', s.movie.tmdbId]" class="w-20 shrink-0 sm:w-24">
        @if (s.movie.posterUrl) {
          <img [src]="s.movie.posterUrl" [alt]="s.movie.title" class="aspect-[2/3] w-full rounded-xl object-cover ring-1 ring-white/10" />
        } @else {
          <div class="grid aspect-[2/3] w-full place-items-center rounded-xl bg-surface-raised text-subtle ring-1 ring-border"><app-icon name="film" class="size-6" /></div>
        }
      </a>
      <div class="min-w-0">
        <p class="font-display text-lg leading-snug font-bold">{{ s.movie.title }}</p>
        <p class="mt-1 text-sm text-muted">{{ s.cinema.name }}</p>
        <p class="mt-3 flex items-center gap-1.5 text-sm font-medium"><app-icon name="calendar" class="size-4 text-primary" /> {{ day() }}</p>
        <p class="mt-1 flex items-center gap-1.5 text-sm font-medium"><app-icon name="clock" class="size-4 text-primary" /> {{ time() }}</p>
        <p class="mt-1 text-xs text-muted">{{ s.room.name }} · {{ screenLabel() }} · {{ format() }} · {{ version() }}</p>
      </div>
    </div>

    <ul class="mt-6 divide-y divide-border border-y border-border">
      @for (seat of b.seats; track seat.seatId) {
        <li class="flex items-center gap-3 py-3 text-sm">
          <span class="grid h-7 min-w-9 place-items-center rounded-md px-1.5 text-xs font-bold" [class]="seat.type === 'VIP' ? 'bg-accent/15 text-accent' : seat.type === 'ACCESSIBLE' ? 'bg-sky-400/15 text-sky-300' : 'bg-white/8 text-foreground'">{{ seat.label }}</span>
          <span class="flex-1 text-muted">{{ seat.type === 'VIP' ? 'VIP seat' : seat.type === 'ACCESSIBLE' ? 'Wheelchair space' : 'Standard seat' }}</span>
          <span class="font-medium">{{ seat.price | currency: b.currency }}</span>
        </li>
      }
    </ul>
    <div class="mt-4 flex items-baseline justify-between">
      <span class="text-sm text-muted">Total</span>
      <span class="font-display text-2xl font-bold">{{ b.total | currency: b.currency }}</span>
    </div>
  `,
})
export class BookingSummary {
  readonly booking = input.required<Booking>();

  protected readonly day = computed(() => longDate(this.booking().screening.localStart));
  protected readonly time = computed(() => {
    const s = this.booking().screening;
    return `${timeOf(s.localStart)} – ${timeOf(s.localEnd)}`;
  });
  protected readonly screenLabel = computed(() => SCREEN_TYPE_LABELS[this.booking().screening.room.screenType]);
  protected readonly format = computed(() => formatLabel(this.booking().screening.format));
  protected readonly version = computed(() => versionLabel(this.booking().screening));
}
