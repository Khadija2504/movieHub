import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PaymentSession } from '../models/booking';
import { Page } from '../models/catalog';
import { Order, Plan, Subscription } from '../models/streaming';

/** Buying and renting movies, subscriptions, and paying store orders. */
@Injectable({ providedIn: 'root' })
export class StoreService {
  private readonly http = inject(HttpClient);
  private readonly orders = `${environment.apiUrl}/orders`;
  private readonly subscriptions = `${environment.apiUrl}/subscriptions`;

  // ---------------------------------------------------------------- orders

  /** Starts buying or renting a movie (pay it on /pay/:id). */
  orderMovie(kind: 'PURCHASE' | 'RENTAL', tmdbId: number): Observable<Order> {
    return this.http.post<Order>(this.orders, { kind, movieId: tmdbId });
  }

  order(id: number): Observable<Order> {
    return this.http.get<Order>(`${this.orders}/${id}`);
  }

  /** Paid, declined and refunded orders. */
  history(page = 1): Observable<Page<Order>> {
    return this.http.get<Page<Order>>(this.orders, { params: new HttpParams().set('page', page) });
  }

  startPayment(id: number): Observable<PaymentSession> {
    return this.http.post<PaymentSession>(`${this.orders}/${id}/payment`, {});
  }

  /** Confirms once paid; the order stays PENDING while the payment is processing. */
  confirm(id: number): Observable<Order> {
    return this.http.post<Order>(`${this.orders}/${id}/confirm`, {});
  }

  cancel(id: number): Observable<Order> {
    return this.http.post<Order>(`${this.orders}/${id}/cancel`, {});
  }

  // ---------------------------------------------------------------- subscriptions

  plans(): Observable<Plan[]> {
    return this.http.get<Plan[]>(`${this.subscriptions}/plans`);
  }

  /** The running subscription, or the last ended one; null if the user never subscribed. */
  mySubscription(): Observable<Subscription | null> {
    return this.http
      .get<Subscription | null>(`${this.subscriptions}/me`, { observe: 'response' })
      .pipe(map((response) => (response.status === 204 ? null : response.body)));
  }

  /** Creates the order of the first period. */
  subscribe(planId: number): Observable<Order> {
    return this.http.post<Order>(this.subscriptions, { planId });
  }

  /** Turns auto-renew off. */
  cancelSubscription(): Observable<Subscription> {
    return this.http.post<Subscription>(`${this.subscriptions}/me/cancel`, {});
  }

  /** Turns auto-renew back on. */
  renewSubscription(): Observable<Subscription> {
    return this.http.post<Subscription>(`${this.subscriptions}/me/renew`, {});
  }

  /** Switches plan from the next renewal. */
  changePlan(planId: number): Observable<Subscription> {
    return this.http.put<Subscription>(`${this.subscriptions}/me/plan`, { planId });
  }
}
