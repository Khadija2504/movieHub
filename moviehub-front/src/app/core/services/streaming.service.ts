import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { TokenStorage } from '../auth/token-storage';
import { Page } from '../models/catalog';
import { LibraryEntry, LibraryFilter, MovieAccess, StreamSession, WatchedMovie } from '../models/streaming';

/** Origin of the API ("http://localhost:8088" in development, the site itself in production). */
export const API_ORIGIN = new URL(environment.apiUrl, globalThis.location?.origin ?? 'http://localhost').origin;

/** Watching: access, play sessions, progress, history and the library. */
@Injectable({ providedIn: 'root' })
export class StreamingService {
  private readonly http = inject(HttpClient);
  private readonly tokens = inject(TokenStorage);
  private readonly stream = `${environment.apiUrl}/stream`;
  private readonly me = `${environment.apiUrl}/users/me`;

  access(tmdbId: number): Observable<MovieAccess> {
    return this.http.get<MovieAccess>(`${this.stream}/${tmdbId}/access`);
  }

  /** Signed video URLs (403 without a purchase, rental or plan). */
  session(tmdbId: number): Observable<StreamSession> {
    return this.http.get<StreamSession>(`${this.stream}/${tmdbId}`);
  }

  saveProgress(tmdbId: number, position: number, duration: number): Observable<unknown> {
    return this.http.put(`${this.stream}/${tmdbId}/progress`, progressBody(position, duration));
  }

  /** For when the tab is closing: a keepalive request outlives the page. */
  saveProgressOnExit(tmdbId: number, position: number, duration: number): void {
    const token = this.tokens.accessToken;
    if (!token) return;
    try {
      void fetch(`${this.stream}/${tmdbId}/progress`, {
        method: 'PUT',
        keepalive: true,
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify(progressBody(position, duration)),
      }).catch(() => undefined);
    } catch {
      // Best effort.
    }
  }

  library(filter: LibraryFilter, page = 1): Observable<Page<LibraryEntry>> {
    return this.http.get<Page<LibraryEntry>>(`${this.me}/library`, {
      params: new HttpParams().set('filter', filter).set('page', page),
    });
  }

  history(page = 1): Observable<Page<WatchedMovie>> {
    return this.http.get<Page<WatchedMovie>>(`${this.me}/history`, { params: new HttpParams().set('page', page) });
  }

  removeFromHistory(tmdbId: number): Observable<void> {
    return this.http.delete<void>(`${this.me}/history/${tmdbId}`);
  }

  clearHistory(): Observable<void> {
    return this.http.delete<void>(`${this.me}/history`);
  }

  continueWatching(limit = 12): Observable<WatchedMovie[]> {
    return this.http.get<WatchedMovie[]>(`${this.me}/continue-watching`, { params: new HttpParams().set('limit', limit) });
  }
}

function progressBody(position: number, duration: number) {
  return { position: Math.max(0, Math.floor(position)), duration: Math.max(1, Math.round(duration)) };
}
