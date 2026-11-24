import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage } from '../../../core/http/api-error.util';
import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { Plan, Subscription } from '../../../core/models/streaming';
import { StoreService } from '../../../core/services/store.service';
import { ToastService } from '../../../core/ui/toast.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { Icon } from '../../../shared/components/icon/icon';
import { qualityName, shortDate } from '../streaming-format';

/** The plan highlighted as the default choice. */
const FEATURED = 'STANDARD';

@Component({
  selector: 'app-plans-page',
  imports: [RouterLink, Icon, ErrorState, CurrencyPipe],
  template: `
    <section class="relative isolate overflow-hidden">
      <div class="pointer-events-none absolute -top-40 left-1/2 -z-10 size-[42rem] -translate-x-1/2 rounded-full bg-primary/15 blur-[140px]" aria-hidden="true"></div>
      <div class="container-page page-top pb-16">
        <div class="mx-auto max-w-2xl text-center">
          <span class="badge bg-primary/12 text-primary ring-1 ring-primary/25"><app-icon name="crown" class="size-3.5" /> MovieHub plans</span>
          <h1 class="mt-5 text-4xl font-extrabold sm:text-5xl">Stream without limits</h1>
          <p class="mt-4 text-lg text-muted">Every title included with a subscription, on every device. No contract: cancel whenever you like.</p>
        </div>

        @if (current(); as sub) {
          @if (sub.active) {
            <div class="mx-auto mt-8 flex max-w-2xl flex-wrap items-center justify-center gap-x-3 gap-y-2 rounded-2xl bg-success/10 px-5 py-3 text-sm text-success ring-1 ring-success/25">
              <app-icon name="check" class="size-4" />
              <span>You're on <strong>{{ sub.plan.name }}</strong>{{ sub.renewsAt ? ', renews ' + date(sub.renewsAt) : sub.endsAt ? ', ends ' + date(sub.endsAt) : '' }}.</span>
              <a routerLink="/account/subscription" class="font-semibold underline underline-offset-4">Manage</a>
            </div>
          }
        }

        @if (plans().error; as error) {
          <div class="mt-12"><app-error-state [message]="error" (retry)="reload()" /></div>
        } @else if (plans().loading) {
          <div class="mt-12 grid gap-6 md:grid-cols-3">
            @for (i of [1, 2, 3]; track i) {
              <div class="skeleton h-[30rem] rounded-[1.75rem]"></div>
            }
          </div>
        } @else {
          <div class="mt-12 grid items-stretch gap-6 md:grid-cols-3">
            @for (plan of plans().data; track plan.id; let i = $index) {
              @let featured = plan.code === featuredCode;
              @let mine = activePlanId() === plan.id;
              <article
                class="card relative flex animate-fade-up flex-col p-6 sm:p-8"
                [style.animation-delay.ms]="i * 70"
                [class]="featured ? 'ring-2 ring-primary/60 shadow-glow md:-my-3 md:py-11' : ''"
                [attr.aria-labelledby]="'plan-' + plan.id"
              >
                @if (featured) {
                  <span class="badge absolute -top-3 left-1/2 -translate-x-1/2 bg-primary text-white shadow-glow">Most popular</span>
                }
                <h2 [id]="'plan-' + plan.id" class="font-display text-xl font-bold">{{ plan.name }}</h2>
                <p class="mt-1 min-h-10 text-sm text-muted">{{ plan.description }}</p>
                <p class="mt-6 flex items-baseline gap-1">
                  <span class="font-display text-4xl font-extrabold">{{ plan.price | currency: plan.currency }}</span>
                  <span class="text-sm text-muted">/ {{ plan.periodDays }} days</span>
                </p>
                <p class="mt-2 inline-flex w-fit items-center gap-1.5 rounded-full bg-white/5 px-3 py-1 text-xs font-semibold ring-1 ring-border">
                  <app-icon name="tv" class="size-3.5" /> {{ quality(plan.maxQuality) }}
                </p>
                <ul class="mt-6 flex-1 space-y-3 text-sm">
                  @for (feature of plan.features; track feature) {
                    <li class="flex gap-2.5"><app-icon name="check" class="mt-0.5 size-4 shrink-0 text-success" /> {{ feature }}</li>
                  }
                </ul>
                <button
                  type="button"
                  class="btn mt-8 w-full"
                  [class]="featured ? 'btn-primary btn-lg' : 'btn-secondary btn-lg'"
                  [disabled]="busy() !== null || mine || nextPlanId() === plan.id"
                  (click)="choose(plan)"
                >
                  @if (busy() === plan.id) {
                    <span class="size-4 animate-spin rounded-full border-2 border-white/30 border-t-white" aria-hidden="true"></span>
                  }
                  {{ buttonLabel(plan) }}
                </button>
              </article>
            }
          </div>
        }

        <div class="mx-auto mt-16 grid max-w-4xl gap-6 text-sm sm:grid-cols-3">
          <div class="flex gap-3"><app-icon name="refresh" class="mt-0.5 size-5 shrink-0 text-primary" /><p><strong class="font-semibold">Renews automatically.</strong> <span class="text-muted">Turn it off anytime; you keep access until the end of the period you paid for.</span></p></div>
          <div class="flex gap-3"><app-icon name="library" class="mt-0.5 size-5 shrink-0 text-primary" /><p><strong class="font-semibold">New releases too.</strong> <span class="text-muted">Some titles are rent or buy only. They show their price on the movie page.</span></p></div>
          <div class="flex gap-3"><app-icon name="lock" class="mt-0.5 size-5 shrink-0 text-primary" /><p><strong class="font-semibold">Secure payment.</strong> <span class="text-muted">Cards are handled by Stripe. MovieHub never sees your card number.</span></p></div>
        </div>
      </div>
    </section>
  `,
})
export class PlansPage {
  private readonly store = inject(StoreService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly featuredCode = FEATURED;
  protected readonly busy = signal<number | null>(null);
  private readonly attempt = signal(0);

  protected readonly plans = toSignal(
    toObservable(this.attempt).pipe(switchMap(() => this.store.plans().pipe(toLoadState()))),
    { initialValue: LOADING as LoadState<Plan[]> },
  );

  /** The user's subscription (null when logged out or never subscribed). */
  protected readonly current = signal<Subscription | null>(null);
  protected readonly activePlanId = computed(() => (this.current()?.active ? this.current()!.plan.id : null));
  protected readonly nextPlanId = computed(() => this.current()?.nextPlan?.id ?? null);

  constructor() {
    if (this.auth.isAuthenticated()) {
      this.store
        .mySubscription()
        .pipe(catchError(() => of(null)))
        .subscribe((sub) => this.current.set(sub));
    }
  }

  protected quality(height: number): string {
    return qualityName(height);
  }

  protected date(instant: string): string {
    return shortDate(instant);
  }

  protected buttonLabel(plan: Plan): string {
    if (this.activePlanId() === plan.id) return 'Your current plan';
    if (this.nextPlanId() === plan.id) return 'Starts at your next renewal';
    if (this.activePlanId() !== null) return `Switch to ${plan.name}`;
    return `Choose ${plan.name}`;
  }

  protected choose(plan: Plan): void {
    if (!this.auth.isAuthenticated()) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: '/plans' } });
      return;
    }
    this.busy.set(plan.id);
    if (this.activePlanId() !== null) {
      // Already subscribed: the new plan starts at the next renewal.
      this.store.changePlan(plan.id).subscribe({
        next: (sub) => {
          this.busy.set(null);
          this.current.set(sub);
          this.toast.success(`You'll switch to ${plan.name} on ${shortDate(sub.currentPeriodEnd)}.`);
        },
        error: (e: unknown) => this.fail(e),
      });
      return;
    }
    this.store.subscribe(plan.id).subscribe({
      next: (order) => void this.router.navigate(['/pay', order.id]),
      error: (e: unknown) => this.fail(e),
    });
  }

  protected reload(): void {
    this.attempt.update((n) => n + 1);
  }

  private fail(e: unknown): void {
    this.busy.set(null);
    this.toast.error(errorMessage(e));
  }
}
