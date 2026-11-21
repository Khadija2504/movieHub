import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { MediaType, Page } from '../models/catalog';
import { Review, ReviewRequest, ReviewSummary } from '../models/user-features';

@Injectable({ providedIn: 'root' })
export class ReviewService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  forTitle(type: MediaType, tmdbId: number, page = 1): Observable<Page<Review>> {
    return this.http.get<Page<Review>>(`${this.api}/reviews/${type}/${tmdbId}`, { params: { page } });
  }

  summary(type: MediaType, tmdbId: number): Observable<ReviewSummary> {
    return this.http.get<ReviewSummary>(`${this.api}/reviews/${type}/${tmdbId}/summary`);
  }

  /** The current user's review, or null (the API answers 204 No Content). */
  mine(type: MediaType, tmdbId: number): Observable<Review | null> {
    return this.http
      .get<Review>(`${this.api}/reviews/${type}/${tmdbId}/mine`, { observe: 'response' })
      .pipe(map((response) => (response.status === 204 ? null : response.body)));
  }

  save(type: MediaType, tmdbId: number, request: ReviewRequest): Observable<Review> {
    return this.http.put<Review>(`${this.api}/reviews/${type}/${tmdbId}`, request);
  }

  delete(type: MediaType, tmdbId: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/reviews/${type}/${tmdbId}`);
  }

  myReviews(page = 1): Observable<Page<Review>> {
    return this.http.get<Page<Review>>(`${this.api}/users/me/reviews`, { params: { page } });
  }
}
