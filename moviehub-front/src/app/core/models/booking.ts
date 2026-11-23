import { ScreeningDetails, SeatType } from './cinema';

/** Mirrors com.moviehub.booking.BookingDtos. */

export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED';
export type PaymentProvider = 'STRIPE' | 'DEMO';
export type BookingScope = 'UPCOMING' | 'PAST';

export interface BookedSeat {
  seatId: number;
  label: string;
  type: SeatType;
  price: number;
}

export interface Booking {
  id: number;
  reference: string;
  status: BookingStatus;
  screening: ScreeningDetails;
  seats: BookedSeat[];
  total: number;
  currency: string;
  createdAt: string;
  /** End of the seat hold (PENDING only). */
  expiresAt: string | null;
  paidAt: string | null;
  cancelledAt: string | null;
  refunded: boolean;
  paymentProvider: PaymentProvider | null;
  /** Last moment the booking can be cancelled, null when it no longer can. */
  cancellableUntil: string | null;
  /** Content of the QR code (CONFIRMED only). */
  ticketCode: string | null;
  checkedInAt: string | null;
}

export interface PaymentSession {
  provider: PaymentProvider;
  /** Stripe only */
  clientSecret: string | null;
  /** Stripe only */
  publishableKey: string | null;
  amount: number;
  currency: string;
  expiresAt: string;
}
