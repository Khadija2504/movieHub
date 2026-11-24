import { PaymentProvider } from './booking';

/**
 * Mirrors com.moviehub.subscription.SubscriptionDtos, com.moviehub.store.StoreDtos and
 * com.moviehub.streaming.StreamingDtos. `tmdbId` is the TMDB id used in /movies/:id and /watch/:id.
 */

// ---------------------------------------------------------------- subscriptions

export interface Plan {
  id: number;
  code: string;
  name: string;
  description: string | null;
  price: number;
  currency: string;
  periodDays: number;
  /** Highest picture height streamed (480, 720, 1080). */
  maxQuality: number;
  screens: number;
  features: string[];
}

export type SubscriptionStatus = 'ACTIVE' | 'EXPIRED';
export type EndReason = 'CANCELLED' | 'PAYMENT_FAILED';

export interface Subscription {
  id: number;
  plan: Plan;
  /** Plan billed from the next renewal (after a plan change). */
  nextPlan: Plan | null;
  status: SubscriptionStatus;
  /** Gives access to the streaming catalog right now. */
  active: boolean;
  autoRenew: boolean;
  startedAt: string;
  currentPeriodStart: string;
  currentPeriodEnd: string;
  /** Next automatic payment (auto-renew on). */
  renewsAt: string | null;
  /** Last day of access (auto-renew off). */
  endsAt: string | null;
  cancelledAt: string | null;
  endedAt: string | null;
  endReason: EndReason | null;
  paymentProvider: PaymentProvider | null;
}

// ---------------------------------------------------------------- store

export type OrderKind = 'PURCHASE' | 'RENTAL' | 'SUBSCRIPTION';
export type OrderStatus = 'PENDING' | 'PAID' | 'CANCELLED' | 'EXPIRED' | 'FAILED' | 'REFUNDED';

export interface OrderMovie {
  tmdbId: number;
  title: string;
  posterUrl: string | null;
  backdropUrl: string | null;
  year: number | null;
}

export interface Order {
  id: number;
  reference: string;
  kind: OrderKind;
  status: OrderStatus;
  description: string;
  amount: number;
  currency: string;
  createdAt: string;
  /** Deadline to pay (PENDING only). */
  expiresAt: string | null;
  paidAt: string | null;
  refunded: boolean;
  paymentProvider: PaymentProvider | null;
  movie: OrderMovie | null;
  plan: Plan | null;
  /** Automatic subscription renewal. */
  renewal: boolean;
}

// ---------------------------------------------------------------- streaming

export type AccessVia = 'PURCHASE' | 'RENTAL' | 'SUBSCRIPTION';

export interface MovieAccess {
  tmdbId: number;
  /** The movie can be watched on MovieHub at all. */
  streamable: boolean;
  canWatch: boolean;
  via: AccessVia | null;
  rentalEndsAt: string | null;
  maxQuality: number | null;
  owned: boolean;
  /** The user holds a running subscription. */
  subscribed: boolean;
  /** Subscribers watch it at no extra cost. */
  inSubscription: boolean;
  purchasePrice: number | null;
  rentalPrice: number | null;
  /** Seconds to resume from (0 = start). */
  resumeAt: number;
  progressPercent: number | null;
}

export interface StreamSource {
  /** Signed, short-lived path from the API host (/api/stream/play/...). */
  url: string;
  type: 'HLS' | 'MP4';
  /** Picture height of an MP4; null for HLS. */
  quality: number | null;
}

export interface StreamSession {
  tmdbId: number;
  title: string;
  posterUrl: string | null;
  backdropUrl: string | null;
  via: AccessVia;
  rentalEndsAt: string | null;
  /** Highest picture height of the user's access; null = no limit. */
  maxQuality: number | null;
  sources: StreamSource[];
  /** When the play URLs stop working. */
  expiresAt: string;
  resumeAt: number;
  /** Licence / credit line. */
  credit: string | null;
}

export interface WatchedMovie {
  tmdbId: number;
  title: string;
  posterUrl: string | null;
  backdropUrl: string | null;
  year: number | null;
  position: number;
  duration: number;
  percent: number;
  completed: boolean;
  lastWatchedAt: string;
  canWatch: boolean;
}

export type LibraryFilter = 'ALL' | 'PURCHASED' | 'RENTED';

export interface LibraryEntry {
  tmdbId: number;
  title: string;
  posterUrl: string | null;
  backdropUrl: string | null;
  year: number | null;
  runtime: number | null;
  kind: 'PURCHASE' | 'RENTAL';
  acquiredAt: string;
  expiresAt: string | null;
  /** Purchase, or rental not over yet. */
  active: boolean;
  rentalPrice: number | null;
  progressPercent: number | null;
}
