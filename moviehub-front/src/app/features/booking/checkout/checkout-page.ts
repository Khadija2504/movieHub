import { CurrencyPipe } from '@angular/common';
import {
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  numberAttribute,
  signal,
  viewChild,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import type { Stripe, StripeElements } from '@stripe/stripe-js';
import { firstValueFrom } from 'rxjs';

import { errorMessage } from '../../../core/http/api-error.util';
import { Booking, PaymentSession } from '../../../core/models/booking';
import { BookingService } from '../../../core/services/booking.service';
import { ToastService } from '../../../core/ui/toast.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { Icon } from '../../../shared/components/icon/icon';
import { countdown, secondsUntil } from '../booking-format';
import { BookingSummary } from '../components/booking-summary';

type Phase =
  | 'loading'
  | 'ready' // waiting for the customer to pay
  | 'paying' // payment submitted
  | 'processing' // paid, waiting for the bank / provider
  | 'expired' // the hold ran out (or the booking ended)
  | 'error';

/** How long to wait for a processing payment before handing over to "My bookings". */
const POLL_ATTEMPTS = 10;
const POLL_INTERVAL_MS = 2000;

@Component({
  selector: 'app-checkout-page',
  imports: [RouterLink, Icon, ErrorState, BookingSummary, CurrencyPipe],
  templateUrl: './checkout-page.html',
})
export class CheckoutPage {
  private readonly bookings = inject(BookingService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  /** Route param :id */
  readonly id = input.required({ transform: numberAttribute });
  /** Set by Stripe when it sends the customer back after a redirect-based payment (3-D Secure, ...). */
  readonly returnedPaymentIntent = input<string | undefined>(undefined, { alias: 'payment_intent' });

  protected readonly phase = signal<Phase>('loading');
  protected readonly booking = signal<Booking | null>(null);
  protected readonly session = signal<PaymentSession | null>(null);
  protected readonly error = signal<string | null>(null);
  /** Card error shown under the payment form (declined, ...). */
  protected readonly paymentError = signal<string | null>(null);
  protected readonly stripeReady = signal(false);
  protected readonly cancelling = signal(false);

  private readonly now = signal(Date.now());
  protected readonly secondsLeft = computed(() => secondsUntil(this.booking()?.expiresAt ?? null, this.now()));
  protected readonly timeLeft = computed(() => countdown(this.secondsLeft()));
  /** Less than 2 minutes: the timer turns red. */
  protected readonly hurry = computed(() => this.secondsLeft() < 120);

  private readonly stripeHost = viewChild<ElementRef<HTMLElement>>('stripeHost');
  private stripe: Stripe | null = null;
  private elements: StripeElements | null = null;
  private destroyed = false;

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      clearInterval(timer);
    });

    // Load once the route input is known.
    effect(() => {
      const id = this.id();
      void this.load(id);
    });

    // The hold ran out while the customer was still on the form.
    effect(() => {
      if (this.phase() === 'ready' && this.booking()?.expiresAt && this.secondsLeft() <= 0) {
        this.phase.set('expired');
      }
    });

    // Mount the Stripe Payment Element once its container is on the page.
    effect(() => {
      const host = this.stripeHost();
      const session = this.session();
      if (host && session?.provider === 'STRIPE' && !this.elements) {
        void this.mountStripe(host.nativeElement, session);
      }
    });
  }

  private async load(id: number): Promise<void> {
    this.phase.set('loading');
    try {
      const booking = await firstValueFrom(this.bookings.get(id));
      this.booking.set(booking);
      if (booking.status === 'CONFIRMED') {
        void this.router.navigate(['/account/bookings', id], { replaceUrl: true });
        return;
      }
      if (booking.status !== 'PENDING') {
        this.phase.set('expired');
        return;
      }
      if (this.returnedPaymentIntent()) {
        // Back from a Stripe redirect: the payment is done (or failed) already.
        await this.confirm();
        return;
      }
      this.session.set(await firstValueFrom(this.bookings.startPayment(id)));
      this.phase.set('ready');
    } catch (e) {
      this.error.set(errorMessage(e));
      this.phase.set('error');
    }
  }

  private async mountStripe(host: HTMLElement, session: PaymentSession): Promise<void> {
    try {
      // The "pure" entry point loads Stripe.js only when called (checkout page only).
      const { loadStripe } = await import('@stripe/stripe-js/pure');
      const stripe = await loadStripe(session.publishableKey!);
      if (!stripe || this.destroyed) throw new Error('Stripe.js not available');
      this.stripe = stripe;
      this.elements = stripe.elements({
        clientSecret: session.clientSecret!,
        appearance: {
          theme: 'night',
          variables: {
            colorPrimary: '#ff3b5c',
            colorBackground: '#171722',
            colorText: '#f6f6f9',
            colorDanger: '#f43f5e',
            borderRadius: '12px',
            fontFamily: 'Inter, system-ui, sans-serif',
          },
        },
      });
      const paymentElement = this.elements.create('payment', { layout: 'tabs' });
      paymentElement.on('ready', () => this.stripeReady.set(true));
      paymentElement.mount(host);
    } catch {
      this.error.set('The payment form could not be loaded. Check your connection and try again.');
      this.phase.set('error');
    }
  }

  protected async pay(): Promise<void> {
    const session = this.session();
    if (!session || this.phase() !== 'ready') return;
    this.paymentError.set(null);
    this.phase.set('paying');

    if (session.provider === 'STRIPE') {
      if (!this.stripe || !this.elements) {
        this.phase.set('ready');
        return;
      }
      const { error } = await this.stripe.confirmPayment({
        elements: this.elements,
        redirect: 'if_required',
        confirmParams: { return_url: `${location.origin}/checkout/${this.id()}` },
      });
      if (error) {
        // Card declined, wrong CVC, ... : the customer can fix it and try again.
        this.paymentError.set(error.message ?? 'The payment failed. Please try again.');
        this.phase.set('ready');
        return;
      }
    } else {
      // Demo: a short pause so it feels like a payment.
      await new Promise((resolve) => setTimeout(resolve, 900));
    }
    await this.confirm();
  }

  /** Asks the backend to confirm; waits a little while the payment is processing. */
  private async confirm(): Promise<void> {
    this.phase.set('paying');
    try {
      for (let attempt = 0; attempt < POLL_ATTEMPTS && !this.destroyed; attempt++) {
        const booking = await firstValueFrom(this.bookings.confirm(this.id()));
        this.booking.set(booking);
        if (booking.status === 'CONFIRMED') {
          this.toast.success('Booking confirmed. Enjoy the movie!');
          void this.router.navigate(['/account/bookings', booking.id], { replaceUrl: true });
          return;
        }
        this.phase.set('processing');
        await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
      }
    } catch (e) {
      this.paymentError.set(errorMessage(e, 'The payment could not be confirmed. Please try again.'));
      // Back from a failed redirect: reload so the form shows up again.
      if (!this.session()) {
        await this.reloadSession();
        return;
      }
      this.phase.set(this.secondsLeft() > 0 ? 'ready' : 'expired');
    }
  }

  private async reloadSession(): Promise<void> {
    try {
      this.session.set(await firstValueFrom(this.bookings.startPayment(this.id())));
      this.phase.set('ready');
    } catch {
      this.phase.set('expired');
    }
  }

  /** Gives the seats back and returns to the seat map. */
  protected cancel(): void {
    const booking = this.booking();
    if (!booking || this.cancelling()) return;
    this.cancelling.set(true);
    this.bookings.cancel(booking.id).subscribe({
      next: () => {
        this.toast.info('Your seats have been released.');
        void this.router.navigate(['/screenings', booking.screening.id], { replaceUrl: true });
      },
      error: (e: unknown) => {
        this.cancelling.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }

  protected retry(): void {
    this.error.set(null);
    this.elements = null;
    this.stripeReady.set(false);
    void this.load(this.id());
  }
}
