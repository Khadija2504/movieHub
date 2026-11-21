import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { User } from '../models/user';
import { WatchlistService } from './watchlist.service';

describe('WatchlistService', () => {
  const url = `${environment.apiUrl}/users/me/watchlist`;
  const user = signal<Partial<User> | null>(null);
  let service: WatchlistService;
  let backend: HttpTestingController;

  beforeEach(() => {
    user.set(null);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AuthService, useValue: { user } }],
    });
    service = TestBed.inject(WatchlistService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  function login(keys: { mediaType: string; tmdbId: number }[]) {
    user.set({ id: 1 });
    TestBed.tick();
    backend.expectOne(`${url}/keys`).flush(keys);
  }

  it('loads saved titles when a user logs in and clears them on logout', () => {
    login([{ mediaType: 'MOVIE', tmdbId: 550 }]);
    expect(service.isSaved('MOVIE', 550)).toBe(true);
    expect(service.isSaved('SERIES', 550)).toBe(false);

    user.set(null);
    TestBed.tick();
    expect(service.count()).toBe(0);
  });

  it('adds optimistically', () => {
    login([]);
    let result: boolean | undefined;

    service.toggle('MOVIE', 13).subscribe((saved) => (result = saved));
    expect(service.isSaved('MOVIE', 13)).toBe(true);
    expect(service.isPending('MOVIE', 13)).toBe(true);

    const req = backend.expectOne(url);
    expect(req.request.body).toEqual({ mediaType: 'MOVIE', tmdbId: 13 });
    req.flush({});

    expect(result).toBe(true);
    expect(service.isPending('MOVIE', 13)).toBe(false);
  });

  it('rolls back when the request fails', () => {
    login([{ mediaType: 'SERIES', tmdbId: 1399 }]);

    service.toggle('SERIES', 1399).subscribe({ error: () => {} });
    expect(service.isSaved('SERIES', 1399)).toBe(false);

    backend.expectOne(`${url}/SERIES/1399`).flush(null, { status: 500, statusText: 'Error' });
    expect(service.isSaved('SERIES', 1399)).toBe(true);
  });
});
