import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { combineLatest, switchMap } from 'rxjs';

import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { Booking, BookingScope } from '../../../core/models/booking';
import { Page } from '../../../core/models/catalog';
import { BookingService } from '../../../core/services/booking.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { Icon } from '../../../shared/components/icon/icon';
import { Pagination } from '../../../shared/components/pagination/pagination';
import { STATUS_BADGES, STATUS_LABELS } from '../../booking/booking-format';
import { longDate, timeOf } from '../../cinemas/cinema-format';
import { PageHeader } from '../components/page-header';

const TABS: { value: BookingScope; label: string }[] = [
  { value: 'UPCOMING', label: 'Upcoming' },
  { value: 'PAST', label: 'Past & cancelled' },
];

@Component({
  selector: 'app-my-bookings-page',
  imports: [RouterLink, Icon, ErrorState, Pagination, PageHeader, CurrencyPipe],
  template: `
    <app-page-header heading="My Bookings" icon="ticket" subheading="Your cinema tickets">
      <div class="flex gap-2" role="tablist" aria-label="Bookings">
        @for (tab of tabs; track tab.value) {
          <button type="button" role="tab" class="chip" [class.chip-active]="scope() === tab.value" [attr.aria-selected]="scope() === tab.value" (click)="setScope(tab.value)">
            {{ tab.label }}
          </button>
        }
      </div>
    </app-page-header>

    @if (state().error; as error) {
      <app-error-state [message]="error" (retry)="retry()" />
    } @else if (state().loading) {
      <div class="space-y-4">
        @for (i of [1, 2, 3]; track i) {
          <div class="card flex gap-5 p-4"><div class="skeleton h-28 w-20 rounded-xl"></div><div class="flex-1 space-y-3 py-2"><div class="skeleton h-5 w-1/2 rounded"></div><div class="skeleton h-4 w-1/3 rounded"></div><div class="skeleton h-4 w-1/4 rounded"></div></div></div>
        }
      </div>
    } @else if (state().data; as page) {
      @if (page.content.length) {
        <ul class="space-y-4">
          @for (b of page.content; track b.id; let i = $index) {
            <li class="animate-fade-up" [style.animation-delay.ms]="i * 50">
              <a [routerLink]="['/account/bookings', b.id]" class="card card-hover group flex gap-4 p-4 sm:gap-5" [class.opacity-70]="b.status === 'CANCELLED' || b.status === 'EXPIRED'">
                @if (b.screening.movie.posterUrl) {
                  <img [src]="b.screening.movie.posterUrl" alt="" class="aspect-[2/3] w-20 shrink-0 self-start rounded-xl object-cover ring-1 ring-white/10 sm:w-24" />
                } @else {
                  <div class="grid aspect-[2/3] w-20 shrink-0 place-items-center self-start rounded-xl bg-surface-raised text-subtle ring-1 ring-border sm:w-24"><app-icon name="film" class="size-6" /></div>
                }
                <div class="flex min-w-0 flex-1 flex-col">
                  <div class="flex flex-wrap items-start justify-between gap-2">
                    <h2 class="font-display text-lg leading-snug font-bold group-hover:text-primary">{{ b.screening.movie.title }}</h2>
                    <span class="rounded-full px-2.5 py-1 text-xs font-semibold ring-1" [class]="badges[b.status]">{{ statusLabel(b) }}</span>
                  </div>
                  <p class="mt-1 flex items-center gap-1.5 text-sm text-muted"><app-icon name="calendar" class="size-4 shrink-0" /> {{ day(b) }} · {{ time(b) }}</p>
                  <p class="mt-1 flex min-w-0 items-center gap-1.5 text-sm text-muted"><app-icon name="map-pin" class="size-4 shrink-0" /> <span class="truncate">{{ b.screening.cinema.name }} · {{ b.screening.room.name }}</span></p>
                  <div class="mt-auto flex flex-wrap items-end justify-between gap-3 pt-3">
                    <p class="text-sm">
                      <span class="text-muted">{{ b.seats.length === 1 ? 'Seat' : 'Seats' }}</span>
                      <span class="font-semibold"> {{ seatList(b) }}</span>
                      <span class="text-muted"> · {{ b.total | currency: b.currency }}</span>
                    </p>
                    @if (b.status === 'PENDING') {
                      <button type="button" class="btn btn-primary btn-sm" (click)="pay($event, b)">Complete payment</button>
                    } @else if (b.status === 'CONFIRMED' && scope() === 'UPCOMING') {
                      <span class="flex items-center gap-1.5 text-sm font-semibold text-primary"><app-icon name="qr" class="size-4" /> View ticket</span>
                    }
                  </div>
                </div>
              </a>
            </li>
          }
        </ul>
        <app-pagination [page]="page.page" [totalPages]="page.totalPages" (pageChange)="goToPage($event)" />
      } @else {
        <div class="card flex flex-col items-center px-6 py-16 text-center">
          <span class="grid size-16 place-items-center rounded-2xl bg-primary/10 text-primary ring-1 ring-primary/20"><app-icon name="ticket" class="size-7" /></span>
          @if (scope() === 'UPCOMING') {
            <p class="mt-5 font-display text-lg font-semibold">No upcoming bookings</p>
            <p class="mt-1 max-w-sm text-sm text-muted">Find a showtime near you and book your seats in a few taps.</p>
            <a routerLink="/cinemas" class="btn btn-primary mt-6"><app-icon name="clapperboard" class="size-4" /> Find a cinema</a>
          } @else {
            <p class="mt-5 font-display text-lg font-semibold">Nothing here yet</p>
            <p class="mt-1 max-w-sm text-sm text-muted">Past and cancelled bookings will show up here.</p>
          }
        </div>
      }
    }
  `,
})
export class MyBookingsPage {
  private readonly bookings = inject(BookingService);
  private readonly router = inject(Router);

  /** Query param ?tab=past */
  readonly tab = input<string | undefined>();

  protected readonly tabs = TABS;
  protected readonly badges = STATUS_BADGES;
  protected readonly scope = computed<BookingScope>(() => (this.tab() === 'past' ? 'PAST' : 'UPCOMING'));
  private readonly page = signal(1);
  private readonly attempt = signal(0);

  protected readonly state = toSignal(
    combineLatest([toObservable(this.scope), toObservable(this.page), toObservable(this.attempt)]).pipe(
      switchMap(([scope, page]) => this.bookings.list(scope, page).pipe(toLoadState())),
    ),
    { initialValue: LOADING as LoadState<Page<Booking>> },
  );

  protected statusLabel(b: Booking): string {
    if (b.status === 'CONFIRMED' && b.checkedInAt) return 'Used';
    if (b.status === 'CANCELLED' && b.refunded) return 'Refunded';
    return STATUS_LABELS[b.status];
  }

  protected day(b: Booking): string {
    return longDate(b.screening.localStart);
  }

  protected time(b: Booking): string {
    return timeOf(b.screening.localStart);
  }

  protected seatList(b: Booking): string {
    return b.seats.map((s) => s.label).join(', ');
  }

  protected setScope(scope: BookingScope): void {
    this.page.set(1);
    void this.router.navigate([], { queryParams: { tab: scope === 'PAST' ? 'past' : null }, replaceUrl: true });
  }

  protected pay(event: Event, b: Booking): void {
    event.preventDefault();
    event.stopPropagation();
    void this.router.navigate(['/checkout', b.id]);
  }

  protected goToPage(page: number): void {
    this.page.set(page);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected retry(): void {
    this.attempt.update((n) => n + 1);
  }
}
