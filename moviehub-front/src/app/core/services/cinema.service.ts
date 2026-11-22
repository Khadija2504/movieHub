import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { MediaSummary } from '../models/catalog';
import {
  CinemaDetails,
  CinemaSchedule,
  CinemaSummary,
  MovieSchedule,
  ScreeningDetails,
  SeatMap,
} from '../models/cinema';

@Injectable({ providedIn: 'root' })
export class CinemaService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  cinemas(city?: string | null): Observable<CinemaSummary[]> {
    return this.http.get<CinemaSummary[]>(`${this.api}/cinemas`, { params: params({ city }) });
  }

  cities(): Observable<string[]> {
    return this.http.get<string[]>(`${this.api}/cinemas/cities`);
  }

  cinema(id: number): Observable<CinemaDetails> {
    return this.http.get<CinemaDetails>(`${this.api}/cinemas/${id}`);
  }

  /** Without a date: the first day that has showtimes. */
  cinemaSchedule(id: number, date?: string | null): Observable<CinemaSchedule> {
    return this.http.get<CinemaSchedule>(`${this.api}/cinemas/${id}/screenings`, { params: params({ date }) });
  }

  nowShowing(): Observable<MediaSummary[]> {
    return this.http.get<MediaSummary[]>(`${this.api}/movies/now-showing`);
  }

  movieSchedule(tmdbId: number, date?: string | null, city?: string | null): Observable<MovieSchedule> {
    return this.http.get<MovieSchedule>(`${this.api}/movies/${tmdbId}/screenings`, {
      params: params({ date, city }),
    });
  }

  screening(id: number): Observable<ScreeningDetails> {
    return this.http.get<ScreeningDetails>(`${this.api}/screenings/${id}`);
  }

  seats(screeningId: number): Observable<SeatMap> {
    return this.http.get<SeatMap>(`${this.api}/screenings/${screeningId}/seats`);
  }
}

function params(values: Record<string, string | null | undefined>): HttpParams {
  let result = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value) result = result.set(key, value);
  }
  return result;
}
