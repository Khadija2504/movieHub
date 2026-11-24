import type { Stripe, StripeElements } from '@stripe/stripe-js';

import { PaymentSession } from '../../../core/models/booking';

export interface StripeForm {
  stripe: Stripe;
  elements: StripeElements;
}

/**
 * Loads Stripe.js (only when called) and mounts the Payment Element in the MovieHub dark theme.
 *
 * @param onReady called once the card form is shown
 */
export async function mountStripeForm(host: HTMLElement, session: PaymentSession, onReady: () => void): Promise<StripeForm> {
  const { loadStripe } = await import('@stripe/stripe-js/pure');
  const stripe = await loadStripe(session.publishableKey!);
  if (!stripe) throw new Error('Stripe.js not available');
  const elements = stripe.elements({
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
  const paymentElement = elements.create('payment', { layout: 'tabs' });
  paymentElement.on('ready', onReady);
  paymentElement.mount(host);
  return { stripe, elements };
}
