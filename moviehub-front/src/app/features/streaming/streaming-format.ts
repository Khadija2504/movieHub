import { AccessVia, OrderKind, OrderStatus } from '../../core/models/streaming';

/** "1:02:03" / "12:34" from seconds. */
export function clock(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = String(s % 60).padStart(2, '0');
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
}

/** "1 day 4 h left", "3 h 20 min left", "12 min left", "Ended". */
export function timeLeft(until: string | null, now: number): string {
  if (!until) return '';
  const minutes = Math.floor((new Date(until).getTime() - now) / 60_000);
  if (minutes <= 0) return 'Ended';
  const days = Math.floor(minutes / 1440);
  const hours = Math.floor((minutes % 1440) / 60);
  const mins = minutes % 60;
  if (days > 0) return `${days} ${days === 1 ? 'day' : 'days'}${hours ? ` ${hours} h` : ''} left`;
  if (hours > 0) return `${hours} h${mins ? ` ${mins} min` : ''} left`;
  return `${mins} min left`;
}

/** "720p", "1080p". */
export function qualityLabel(height: number): string {
  return `${height}p`;
}

/** "Full HD (1080p)" for plan cards. */
export function qualityName(height: number): string {
  if (height >= 2160) return '4K (2160p)';
  if (height >= 1080) return 'Full HD (1080p)';
  if (height >= 720) return 'HD (720p)';
  return `SD (${height}p)`;
}

/** "24 Nov 2026" */
export function shortDate(instant: string): string {
  return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' }).format(
    new Date(instant),
  );
}

export const VIA_LABELS: Record<AccessVia, string> = {
  PURCHASE: 'You own this movie',
  RENTAL: 'Rented',
  SUBSCRIPTION: 'Included in your plan',
};

export const ORDER_KIND_LABELS: Record<OrderKind, string> = {
  PURCHASE: 'Purchase',
  RENTAL: 'Rental',
  SUBSCRIPTION: 'Subscription',
};

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: 'Awaiting payment',
  PAID: 'Paid',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
  FAILED: 'Declined',
  REFUNDED: 'Refunded',
};

export const ORDER_STATUS_BADGES: Record<OrderStatus, string> = {
  PENDING: 'bg-warning/12 text-warning ring-warning/25',
  PAID: 'bg-success/12 text-success ring-success/25',
  CANCELLED: 'bg-white/5 text-muted ring-border',
  EXPIRED: 'bg-white/5 text-muted ring-border',
  FAILED: 'bg-danger/12 text-danger ring-danger/25',
  REFUNDED: 'bg-sky-500/12 text-sky-300 ring-sky-500/25',
};
