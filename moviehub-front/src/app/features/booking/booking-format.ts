import { BookingStatus } from '../../core/models/booking';

export const STATUS_LABELS: Record<BookingStatus, string> = {
  PENDING: 'Awaiting payment',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
};

export const STATUS_BADGES: Record<BookingStatus, string> = {
  PENDING: 'bg-warning/12 text-warning ring-warning/25',
  CONFIRMED: 'bg-success/12 text-success ring-success/25',
  CANCELLED: 'bg-white/5 text-muted ring-border',
  EXPIRED: 'bg-white/5 text-muted ring-border',
};

/** "9:58" from a number of seconds (never negative). */
export function countdown(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

/**
 * "Mon 24 Nov, 18:30". Defaults to the browser's zone (when something happened, e.g. a payment); pass the
 * cinema's zone for deadlines tied to a screening so they read like its showtimes.
 */
export function shortDateTime(instant: string, timeZone?: string): string {
  return new Intl.DateTimeFormat('en-GB', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    timeZone,
  }).format(new Date(instant));
}

/** Seconds left until an instant. */
export function secondsUntil(instant: string | null, now: number): number {
  return instant ? (new Date(instant).getTime() - now) / 1000 : 0;
}
