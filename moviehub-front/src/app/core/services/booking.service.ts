import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Booking, BookingScope, PaymentSession } from '../models/booking';
import { Page } from '../models/catalog';

/** The current user's cinema bookings. */
@Injectable({ providedIn: 'root' })
export class BookingService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/bookings`;

  /** Holds the seats for 10 minutes. */
  create(screeningId: number, seatIds: number[]): Observable<Booking> {
    return this.http.post<Booking>(this.url, { screeningId, seatIds });
  }

  list(scope: BookingScope, page = 1): Observable<Page<Booking>> {
    return this.http.get<Page<Booking>>(this.url, { params: new HttpParams().set('scope', scope).set('page', page) });
  }

  get(id: number): Observable<Booking> {
    return this.http.get<Booking>(`${this.url}/${id}`);
  }

  startPayment(id: number): Observable<PaymentSession> {
    return this.http.post<PaymentSession>(`${this.url}/${id}/payment`, {});
  }

  /** Confirms once paid; the booking stays PENDING while the payment is processing. */
  confirm(id: number): Observable<Booking> {
    return this.http.post<Booking>(`${this.url}/${id}/confirm`, {});
  }

  cancel(id: number): Observable<Booking> {
    return this.http.post<Booking>(`${this.url}/${id}/cancel`, {});
  }

  /** QR code image (needs the auth header, so it is fetched as a blob). */
  ticketQr(id: number): Observable<Blob> {
    return this.http.get(`${this.url}/${id}/ticket.png`, { responseType: 'blob' });
  }
}
