import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { Observable, catchError, defer, finalize, map, of, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { MediaType, Page } from '../models/catalog';
import { TitleKey, WatchlistItem, titleKey } from '../models/user-features';

/**
 * The current user's watchlist. Keeps the set of saved titles in memory (loaded once per login),
 * so every card and details page can show the bookmark state without extra requests.
 */
@Injectable({ providedIn: 'root' })
export class WatchlistService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly url = `${environment.apiUrl}/users/me/watchlist`;

  private readonly saved = signal<ReadonlySet<string>>(new Set());
  private readonly pending = signal<ReadonlySet<string>>(new Set());

  readonly count = computed(() => this.saved().size);

  constructor() {
    // (Re)load keys whenever the logged-in user changes; clear them on logout.
    effect(() => {
      const userId = this.auth.user()?.id;
      if (userId == null) {
        this.saved.set(new Set());
        return;
      }
      this.http
        .get<TitleKey[]>(`${this.url}/keys`)
        .pipe(catchError(() => of<TitleKey[]>([])))
        .subscribe((keys) => this.saved.set(new Set(keys.map((k) => titleKey(k.mediaType, k.tmdbId)))));
    });
  }

  isSaved(type: MediaType, tmdbId: number): boolean {
    return this.saved().has(titleKey(type, tmdbId));
  }

  isPending(type: MediaType, tmdbId: number): boolean {
    return this.pending().has(titleKey(type, tmdbId));
  }

  list(type: MediaType | null, page: number): Observable<Page<WatchlistItem>> {
    let params = new HttpParams().set('page', page);
    if (type) params = params.set('type', type);
    return this.http.get<Page<WatchlistItem>>(this.url, { params });
  }

  /**
   * Adds or removes a title. The UI updates immediately (optimistic) and rolls back on error.
   * @returns true when the title is now saved
   */
  toggle(type: MediaType, tmdbId: number): Observable<boolean> {
    const key = titleKey(type, tmdbId);
    return defer(() => {
      const wasSaved = this.saved().has(key);
      this.setSaved(key, !wasSaved);
      this.setPending(key, true);

      const request$: Observable<unknown> = wasSaved
        ? this.http.delete(`${this.url}/${type}/${tmdbId}`)
        : this.http.post(this.url, { mediaType: type, tmdbId });

      return request$.pipe(
        map(() => !wasSaved),
        catchError((error: unknown) => {
          this.setSaved(key, wasSaved); // roll back
          return throwError(() => error);
        }),
        finalize(() => this.setPending(key, false)),
      );
    });
  }

  private setSaved(key: string, value: boolean): void {
    this.saved.update((set) => toggled(set, key, value));
  }

  private setPending(key: string, value: boolean): void {
    this.pending.update((set) => toggled(set, key, value));
  }
}

function toggled(set: ReadonlySet<string>, key: string, present: boolean): ReadonlySet<string> {
  const next = new Set(set);
  if (present) next.add(key);
  else next.delete(key);
  return next;
}
