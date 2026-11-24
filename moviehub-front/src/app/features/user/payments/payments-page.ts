import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { combineLatest, switchMap } from 'rxjs';

import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { Page } from '../../../core/models/catalog';
import { Order } from '../../../core/models/streaming';
import { StoreService } from '../../../core/services/store.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { Icon } from '../../../shared/components/icon/icon';
import { Pagination } from '../../../shared/components/pagination/pagination';
import { ORDER_KIND_LABELS, ORDER_STATUS_BADGES, ORDER_STATUS_LABELS, shortDate } from '../../streaming/streaming-format';
import { PageHeader } from '../components/page-header';

/** Payment history: purchases, rentals and subscription payments. */
@Component({
  selector: 'app-payments-page',
  imports: [RouterLink, Icon, ErrorState, Pagination, PageHeader, CurrencyPipe],
  template: `
    <app-page-header heading="Payments" icon="card" subheading="Purchases, rentals and subscription payments" />

    @if (state().error; as error) {
      <app-error-state [message]="error" (retry)="reload()" />
    } @else if (state().loading) {
      <div class="space-y-3">
        @for (i of [1, 2, 3]; track i) {
          <div class="skeleton h-20 rounded-card"></div>
        }
      </div>
    } @else if (state().data; as page) {
      @if (page.content.length) {
        <ul class="card divide-y divide-border overflow-hidden">
          @for (o of page.content; track o.id) {
            <li class="flex items-center gap-4 p-4">
              <span class="grid size-11 shrink-0 place-items-center rounded-xl ring-1" [class]="o.kind === 'SUBSCRIPTION' ? 'bg-amber-500/12 text-amber-400 ring-amber-500/25' : 'bg-violet-500/12 text-violet-400 ring-violet-500/20'">
                <app-icon [name]="o.kind === 'SUBSCRIPTION' ? 'crown' : o.kind === 'RENTAL' ? 'clock' : 'library'" class="size-5" />
              </span>
              <div class="min-w-0 flex-1">
                @if (o.movie) {
                  <a [routerLink]="['/movies', o.movie.tmdbId]" class="block truncate font-semibold hover:text-primary">{{ o.movie.title }}</a>
                } @else {
                  <p class="truncate font-semibold">MovieHub {{ o.plan?.name }}{{ o.renewal ? ' · renewal' : '' }}</p>
                }
                <p class="mt-0.5 text-xs text-muted">{{ kinds[o.kind] }} · {{ date(o.paidAt ?? o.createdAt) }} · <span class="font-mono">{{ o.reference }}</span>{{ o.paymentProvider === 'DEMO' ? ' · demo' : '' }}</p>
              </div>
              <div class="flex shrink-0 flex-col items-end gap-1">
                <span class="font-semibold tabular-nums">{{ o.amount | currency: o.currency }}</span>
                <span class="rounded-full px-2 py-0.5 text-[0.7rem] font-semibold ring-1" [class]="badges[o.status]">{{ labels[o.status] }}</span>
              </div>
            </li>
          }
        </ul>
        <app-pagination [page]="page.page" [totalPages]="page.totalPages" (pageChange)="goToPage($event)" />
      } @else {
        <div class="card flex flex-col items-center px-6 py-16 text-center">
          <span class="grid size-16 place-items-center rounded-2xl bg-primary/10 text-primary ring-1 ring-primary/20"><app-icon name="card" class="size-7" /></span>
          <p class="mt-5 font-display text-lg font-semibold">No payments yet</p>
          <p class="mt-1 max-w-sm text-sm text-muted">Movies you buy or rent and your subscription payments will be listed here. Cinema tickets are in My Bookings.</p>
          <a routerLink="/account/bookings" class="btn btn-secondary mt-6"><app-icon name="ticket" class="size-4" /> My Bookings</a>
        </div>
      }
    }
  `,
})
export class PaymentsPage {
  private readonly store = inject(StoreService);

  protected readonly kinds = ORDER_KIND_LABELS;
  protected readonly labels = ORDER_STATUS_LABELS;
  protected readonly badges = ORDER_STATUS_BADGES;
  private readonly page = signal(1);
  private readonly attempt = signal(0);

  protected readonly state = toSignal(
    combineLatest([toObservable(this.page), toObservable(this.attempt)]).pipe(
      switchMap(([page]) => this.store.history(page).pipe(toLoadState())),
    ),
    { initialValue: LOADING as LoadState<Page<Order>> },
  );

  protected date(instant: string): string {
    return shortDate(instant);
  }

  protected goToPage(page: number): void {
    this.page.set(page);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected reload(): void {
    this.attempt.update((n) => n + 1);
  }
}
