import { CurrencyPipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, computed, effect, inject, input, numberAttribute, signal, viewChild } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { errorMessage } from '../../../core/http/api-error.util';
import { PaymentSession } from '../../../core/models/booking';
import { Order } from '../../../core/models/streaming';
import { StoreService } from '../../../core/services/store.service';
import { ToastService } from '../../../core/ui/toast.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { Icon } from '../../../shared/components/icon/icon';
import { countdown, secondsUntil } from '../../booking/booking-format';
import { qualityName } from '../streaming-format';
import { StripeForm, mountStripeForm } from './stripe-form';

type Phase = 'loading' | 'ready' | 'paying' | 'processing' | 'ended' | 'error';

const POLL_ATTEMPTS = 10;
const POLL_INTERVAL_MS = 2000;

/** Pays a store order: a movie purchase or rental, or the first period of a subscription. */
@Component({
  selector: 'app-order-checkout-page',
  imports: [RouterLink, Icon, ErrorState, CurrencyPipe],
  templateUrl: './order-checkout-page.html',
})
export class OrderCheckoutPage {
  private readonly store = inject(StoreService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  /** Route param :id */
  readonly id = input.required({ transform: numberAttribute });
  /** Set by Stripe when it sends the customer back after a redirect (3-D Secure, ...). */
  readonly returnedPaymentIntent = input<string | undefined>(undefined, { alias: 'payment_intent' });

  protected readonly phase = signal<Phase>('loading');
  protected readonly order = signal<Order | null>(null);
  protected readonly session = signal<PaymentSession | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly paymentError = signal<string | null>(null);
  protected readonly stripeReady = signal(false);
  protected readonly cancelling = signal(false);

  private readonly now = signal(Date.now());
  protected readonly secondsLeft = computed(() => secondsUntil(this.order()?.expiresAt ?? null, this.now()));
  protected readonly timeLeft = computed(() => countdown(this.secondsLeft()));
  protected readonly hurry = computed(() => this.secondsLeft() < 120);

  /** Where "back" goes: the movie, or the plans. */
  protected readonly backLink = computed(() => {
    const movie = this.order()?.movie;
    return movie ? ['/movies', movie.tmdbId] : ['/plans'];
  });

  protected readonly perks = computed<string[]>(() => {
    const order = this.order();
    if (!order) return [];
    switch (order.kind) {
      case 'PURCHASE':
        return ['Yours to keep: watch it as often as you like', 'Best available picture quality', 'Resume on any device'];
      case 'RENTAL':
        return ['48 hours to watch, starting now', 'Best available picture quality', 'Resume on any device'];
      case 'SUBSCRIPTION': {
        const plan = order.plan!;
        return [`Every title included with a subscription`, `${qualityName(plan.maxQuality)} streaming`,
          `Renews every ${plan.periodDays} days, cancel anytime`];
      }
    }
  });

  private readonly stripeHost = viewChild<ElementRef<HTMLElement>>('stripeHost');
  private form: StripeForm | null = null;
  private mounting = false;
  private destroyed = false;

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      clearInterval(timer);
    });

    effect(() => void this.load(this.id()));

    effect(() => {
      if (this.phase() === 'ready' && this.order()?.expiresAt && this.secondsLeft() <= 0) {
        this.phase.set('ended');
      }
    });

    effect(() => {
      const host = this.stripeHost();
      const session = this.session();
      if (host && session?.provider === 'STRIPE' && !this.form && !this.mounting) {
        this.mounting = true;
        mountStripeForm(host.nativeElement, session, () => this.stripeReady.set(true))
          .then((form) => (this.form = form))
          .catch(() => {
            this.error.set('The payment form could not be loaded. Check your connection and try again.');
            this.phase.set('error');
          })
          .finally(() => (this.mounting = false));
      }
    });
  }

  private async load(id: number): Promise<void> {
    this.phase.set('loading');
    try {
      const order = await firstValueFrom(this.store.order(id));
      this.order.set(order);
      if (order.status === 'PAID') {
        this.done(order);
        return;
      }
      if (order.status !== 'PENDING') {
        this.phase.set('ended');
        return;
      }
      if (this.returnedPaymentIntent()) {
        await this.confirm();
        return;
      }
      this.session.set(await firstValueFrom(this.store.startPayment(id)));
      this.phase.set('ready');
    } catch (e) {
      this.error.set(errorMessage(e));
      this.phase.set('error');
    }
  }

  protected async pay(): Promise<void> {
    const session = this.session();
    if (!session || this.phase() !== 'ready') return;
    this.paymentError.set(null);
    this.phase.set('paying');
    if (session.provider === 'STRIPE') {
      if (!this.form) {
        this.phase.set('ready');
        return;
      }
      const { error } = await this.form.stripe.confirmPayment({
        elements: this.form.elements,
        redirect: 'if_required',
        confirmParams: { return_url: `${location.origin}/pay/${this.id()}` },
      });
      if (error) {
        this.paymentError.set(error.message ?? 'The payment failed. Please try again.');
        this.phase.set('ready');
        return;
      }
    } else {
      await new Promise((resolve) => setTimeout(resolve, 900));
    }
    await this.confirm();
  }

  private async confirm(): Promise<void> {
    this.phase.set('paying');
    try {
      for (let attempt = 0; attempt < POLL_ATTEMPTS && !this.destroyed; attempt++) {
        const order = await firstValueFrom(this.store.confirm(this.id()));
        this.order.set(order);
        if (order.status === 'PAID') {
          this.done(order, true);
          return;
        }
        this.phase.set('processing');
        await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
      }
    } catch (e) {
      this.paymentError.set(errorMessage(e, 'The payment could not be confirmed. Please try again.'));
      if (!this.session()) {
        try {
          this.session.set(await firstValueFrom(this.store.startPayment(this.id())));
          this.phase.set('ready');
        } catch {
          this.phase.set('ended');
        }
        return;
      }
      this.phase.set(this.secondsLeft() > 0 ? 'ready' : 'ended');
    }
  }

  /** Paid: straight to the movie, or to the subscription page. */
  private done(order: Order, justPaid = false): void {
    if (order.kind === 'SUBSCRIPTION') {
      if (justPaid) this.toast.success(`Welcome to MovieHub ${order.plan?.name}! Enjoy the catalog.`);
      void this.router.navigate(['/account/subscription'], { replaceUrl: true });
      return;
    }
    if (justPaid) {
      this.toast.success(order.kind === 'RENTAL' ? 'Rented! You have 48 hours to watch it.' : 'It is yours. Enjoy the movie!');
    }
    void this.router.navigate(['/movies', order.movie!.tmdbId], { replaceUrl: true });
  }

  protected cancel(): void {
    const order = this.order();
    if (!order || this.cancelling()) return;
    this.cancelling.set(true);
    this.store.cancel(order.id).subscribe({
      next: (updated) => {
        if (updated.status === 'PAID') {
          this.done(updated, true);
          return;
        }
        void this.router.navigate(this.backLink(), { replaceUrl: true });
      },
      error: (e: unknown) => {
        this.cancelling.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }

  protected retry(): void {
    this.error.set(null);
    this.form = null;
    this.stripeReady.set(false);
    void this.load(this.id());
  }
}
