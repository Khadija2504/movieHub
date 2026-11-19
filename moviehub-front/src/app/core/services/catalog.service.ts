import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  BrowseFilter,
  Genre,
  MediaSummary,
  MediaType,
  MovieDetails,
  Page,
  SeasonDetails,
  SeriesDetails,
} from '../models/catalog';

@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;
  /** Genres rarely change: fetch each list once per session. */
  private readonly genreCache = new Map<MediaType, Observable<Genre[]>>();

  browse(type: MediaType, filter: BrowseFilter = {}): Observable<Page<MediaSummary>> {
    const path = type === 'MOVIE' ? 'movies' : 'series';
    return this.http.get<Page<MediaSummary>>(`${this.api}/${path}`, { params: toParams(filter) });
  }

  trending(type?: MediaType, daily = false): Observable<Page<MediaSummary>> {
    return this.http.get<Page<MediaSummary>>(`${this.api}/trending`, {
      params: toParams({ type, daily: daily || undefined }),
    });
  }

  search(query: string, page = 1): Observable<Page<MediaSummary>> {
    return this.http.get<Page<MediaSummary>>(`${this.api}/search`, { params: toParams({ q: query, page }) });
  }

  genres(type: MediaType): Observable<Genre[]> {
    let genres$ = this.genreCache.get(type);
    if (!genres$) {
      genres$ = this.http
        .get<Genre[]>(`${this.api}/genres`, { params: { type } })
        .pipe(shareReplay({ bufferSize: 1, refCount: false }));
      this.genreCache.set(type, genres$);
    }
    return genres$;
  }

  movie(id: number): Observable<MovieDetails> {
    return this.http.get<MovieDetails>(`${this.api}/movies/${id}`);
  }

  series(id: number): Observable<SeriesDetails> {
    return this.http.get<SeriesDetails>(`${this.api}/series/${id}`);
  }

  season(seriesId: number, seasonNumber: number): Observable<SeasonDetails> {
    return this.http.get<SeasonDetails>(`${this.api}/series/${seriesId}/seasons/${seasonNumber}`);
  }
}

/** Drops undefined/null/empty values so they are not sent as "undefined". */
function toParams(values: object): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== null && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}
