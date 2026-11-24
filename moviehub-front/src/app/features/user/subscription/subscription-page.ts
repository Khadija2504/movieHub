import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, catchError, combineLatest, of, switchMap } from 'rxjs';

import { errorMessage } from '../../../core/http/api-error.util';
import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { Plan, Subscription } from '../../../core/models/streaming';
import { StoreService } from '../../../core/services/store.service';
import { ToastService } from '../../../core/ui/toast.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { Icon } from '../../../shared/components/icon/icon';
import { qualityName, shortDate } from '../../streaming/streaming-format';
import { PageHeader } from '../components/page-header';

/** The user's subscription: plan, renewal, auto-renew on/off, plan change. */
@Component({
  selector: 'app-subscription-page',
  imports: [RouterLink, Icon, ErrorState, PageHeader, CurrencyPipe],
  template: `
    <app-page-header heading="Subscription" icon="crown" subheading="Your MovieHub plan and renewals">
      <a routerLink="/account/payments" class="btn btn-ghost btn-sm text-muted"><app-icon name="card" class="size-4" /> Payment history</a>
    </app-page-header>

    @if (state().error; as error) {
      <app-error-state [message]="error" (retry)="reload()" />
    } @else if (state().loading) {
      <div class="skeleton h-72 rounded-[1.75rem]"></div>
    } @else {
      @let sub = subscription();
      @if (sub && sub.active) {
        <!-- Running subscription -->
        <section class="relative overflow-hidden rounded-[1.75rem] border border-border bg-surface p-6 sm:p-8">
          <div class="pointer-events-none absolute -top-24 -right-16 size-72 rounded-full bg-primary/20 blur-[90px]" aria-hidden="true"></div>
          <div class="relative flex flex-wrap items-start justify-between gap-4">
            <div>
              <span class="badge bg-success/12 text-success ring-1 ring-success/25"><app-icon name="check" class="size-3.5" /> Active</span>
              <h2 class="mt-3 font-display text-3xl font-extrabold">MovieHub {{ sub.plan.name }}</h2>
              <p class="mt-1 text-muted">{{ sub.plan.price | currency: sub.plan.currency }} every {{ sub.plan.periodDays }} days · {{ quality(sub.plan.maxQuality) }} · {{ sub.plan.screens }} {{ sub.plan.screens === 1 ? 'screen' : 'screens' }}</p>
            </div>
            <a routerLink="/movies" class="btn btn-primary"><app-icon name="play" filled class="size-4" /> Start watching</a>
          </div>

          <dl class="relative mt-8 grid gap-4 sm:grid-cols-3">
            <div class="rounded-2xl bg-white/[0.03] p-4 ring-1 ring-border">
              <dt class="text-xs text-muted">Member since</dt>
              <dd class="mt-1 font-semibold">{{ date(sub.startedAt) }}</dd>
            </div>
            <div class="rounded-2xl bg-white/[0.03] p-4 ring-1 ring-border">
              <dt class="text-xs text-muted">Current period</dt>
              <dd class="mt-1 font-semibold">{{ date(sub.currentPeriodStart) }} – {{ date(sub.currentPeriodEnd) }}</dd>
            </div>
            <div class="rounded-2xl p-4 ring-1" [class]="sub.autoRenew ? 'bg-white/[0.03] ring-border' : 'bg-warning/10 ring-warning/25'">
              <dt class="text-xs text-muted">{{ sub.autoRenew ? 'Next payment' : 'Access ends' }}</dt>
              <dd class="mt-1 font-semibold">
                {{ date(sub.currentPeriodEnd) }}
                @if (sub.autoRenew) {
                  · {{ (sub.nextPlan ?? sub.plan).price | currency: sub.plan.currency }}
                }
              </dd>
            </div>
          </dl>

          @if (sub.nextPlan; as next) {
            <p class="relative mt-5 flex items-start gap-2 rounded-xl bg-sky-500/10 p-3 text-sm text-sky-200 ring-1 ring-sky-500/20">
              <app-icon name="info" class="mt-0.5 size-4 shrink-0" />
              You'll switch to {{ next.name }} ({{ quality(next.maxQuality) }}) on {{ date(sub.currentPeriodEnd) }}.
              <button type="button" class="ml-auto shrink-0 font-semibold underline underline-offset-2" [disabled]="busy()" (click)="changePlan(sub.plan)">Keep {{ sub.plan.name }}</button>
            </p>
          }

          <div class="relative mt-8 flex flex-wrap items-center gap-3 border-t border-border pt-6">
            @if (sub.autoRenew) {
              @if (confirmingCancel()) {
                <p class="text-sm text-muted">Turn off renewal? You keep access until {{ date(sub.currentPeriodEnd) }}.</p>
                <button type="button" class="btn btn-sm bg-danger text-white hover:bg-danger/90" [disabled]="busy()" (click)="cancel()">Turn off renewal</button>
                <button type="button" class="btn btn-ghost btn-sm" (click)="confirmingCancel.set(false)">Keep it</button>
              } @else {
                <button type="button" class="btn btn-secondary btn-sm" (click)="confirmingCancel.set(true)">Cancel subscription</button>
              }
            } @else {
              <p class="text-sm text-muted">Renewal is off. Your subscription ends on {{ date(sub.currentPeriodEnd) }}.</p>
              <button type="button" class="btn btn-primary btn-sm" [disabled]="busy()" (click)="resume()"><app-icon name="refresh" class="size-4" /> Keep my subscription</button>
            }
          </div>
        </section>

        <!-- Change plan -->
        @if (sub.autoRenew && otherPlans().length) {
          <section class="mt-10">
            <h2 class="section-title">Change plan</h2>
            <p class="mt-1 text-sm text-muted">The new plan starts at your next renewal, with no extra charge now.</p>
            <div class="mt-5 grid gap-4 sm:grid-cols-2">
              @for (plan of otherPlans(); track plan.id) {
                <div class="card flex items-center gap-4 p-5">
                  <div class="flex-1">
                    <p class="font-display font-semibold">{{ plan.name }} <span class="font-sans text-sm font-normal text-muted">· {{ plan.price | currency: plan.currency }}</span></p>
                    <p class="mt-0.5 text-xs text-muted">{{ quality(plan.maxQuality) }} · {{ plan.screens }} {{ plan.screens === 1 ? 'screen' : 'screens' }}</p>
                  </div>
                  <button type="button" class="btn btn-secondary btn-sm" [disabled]="busy() || sub.nextPlan?.id === plan.id" (click)="changePlan(plan)">
                    {{ sub.nextPlan?.id === plan.id ? 'Chosen' : plan.price > sub.plan.price ? 'Upgrade' : 'Downgrade' }}
                  </button>
                </div>
              }
            </div>
          </section>
        }
      } @else {
        <!-- No subscription (or it ended) -->
        <div class="card flex flex-col items-center px-6 py-16 text-center">
          <span class="grid size-16 place-items-center rounded-2xl bg-amber-500/12 text-amber-400 ring-1 ring-amber-500/25"><app-icon name="crown" class="size-7" /></span>
          @if (sub) {
            <p class="mt-5 font-display text-lg font-semibold">Your {{ sub.plan.name }} subscription ended</p>
            <p class="mt-1 max-w-sm text-sm text-muted">
              {{ sub.endReason === 'PAYMENT_FAILED' ? 'The renewal payment was declined' : 'You turned renewal off' }}, so it ended on {{ date(sub.endedAt ?? sub.currentPeriodEnd) }}. Subscribe again anytime.
            </p>
          } @else {
            <p class="mt-5 font-display text-lg font-semibold">You don't have a subscription</p>
            <p class="mt-1 max-w-sm text-sm text-muted">Subscribe to watch every title included with a plan, from {{ cheapest() | currency: currency() }} a month.</p>
          }
          <a routerLink="/plans" class="btn btn-primary mt-6"><app-icon name="crown" class="size-4" /> See plans</a>
        </div>
      }
    }
  `,
})
export class SubscriptionPage {
  private readonly store = inject(StoreService);
  private readonly toast = inject(ToastService);

  private readonly attempt = signal(0);
  /** Replaces the loaded subscription after an action. */
  private readonly updated = signal<Subscription | null>(null);
  protected readonly busy = signal(false);
  protected readonly confirmingCancel = signal(false);

  protected readonly state = toSignal(
    toObservable(this.attempt).pipe(
      switchMap(() =>
        combineLatest([this.store.mySubscription(), this.store.plans().pipe(catchError(() => of([] as Plan[])))]).pipe(
          toLoadState(),
        ),
      ),
    ),
    { initialValue: LOADING as LoadState<[Subscription | null, Plan[]]> },
  );

  protected readonly subscription = computed(() => this.updated() ?? this.state().data?.[0] ?? null);
  private readonly plans = computed(() => this.state().data?.[1] ?? []);
  protected readonly otherPlans = computed(() => this.plans().filter((p) => p.id !== this.subscription()?.plan.id));
  protected readonly cheapest = computed(() => Math.min(...this.plans().map((p) => p.price)));
  protected readonly currency = computed(() => this.plans()[0]?.currency ?? 'EUR');

  protected quality(height: number): string {
    return qualityName(height);
  }

  protected date(instant: string): string {
    return shortDate(instant);
  }

  protected cancel(): void {
    this.run(this.store.cancelSubscription(), (s) => `Renewal turned off. You keep access until ${shortDate(s.currentPeriodEnd)}.`);
  }

  protected resume(): void {
    this.run(this.store.renewSubscription(), () => 'Your subscription will renew automatically.');
  }

  protected changePlan(plan: Plan): void {
    this.run(this.store.changePlan(plan.id), (s) =>
      s.nextPlan ? `You'll switch to ${s.nextPlan.name} on ${shortDate(s.currentPeriodEnd)}.` : `You'll stay on ${s.plan.name}.`,
    );
  }

  protected reload(): void {
    this.updated.set(null);
    this.attempt.update((n) => n + 1);
  }

  private run(action: Observable<Subscription>, message: (s: Subscription) => string): void {
    this.busy.set(true);
    action.subscribe({
      next: (s) => {
        this.updated.set(s);
        this.busy.set(false);
        this.confirmingCancel.set(false);
        this.toast.success(message(s));
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }
}
