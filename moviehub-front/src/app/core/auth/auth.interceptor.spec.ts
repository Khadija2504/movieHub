import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { authInterceptor } from './auth.interceptor';
import { TokenStorage } from './token-storage';

describe('authInterceptor', () => {
  const api = environment.apiUrl;
  let http: HttpClient;
  let backend: HttpTestingController;
  let storage: TokenStorage;

  const authResponse = (access: string, refresh: string) => ({
    accessToken: access,
    refreshToken: refresh,
    tokenType: 'Bearer',
    expiresIn: 900,
    user: { id: 1, email: 'a@b.c', firstName: 'A', lastName: 'B', avatarUrl: null, role: 'USER', createdAt: '' },
  });

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'login', children: [] }]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    storage = TestBed.inject(TokenStorage);
  });

  afterEach(() => backend.verify());

  it('adds the bearer token to API calls only', () => {
    storage.save('access-1', 'refresh-1');

    http.get(`${api}/users/me`).subscribe();
    http.get('https://api.themoviedb.org/3/movie/1').subscribe();

    expect(backend.expectOne(`${api}/users/me`).request.headers.get('Authorization')).toBe('Bearer access-1');
    expect(backend.expectOne('https://api.themoviedb.org/3/movie/1').request.headers.has('Authorization')).toBe(false);
  });

  it('refreshes once on 401 and retries with the new token', () => {
    storage.save('expired', 'refresh-1');
    let result: unknown;

    http.get(`${api}/users/me`).subscribe((r) => (result = r));
    backend.expectOne(`${api}/users/me`).flush(null, { status: 401, statusText: 'Unauthorized' });

    const refresh = backend.expectOne(`${api}/auth/refresh`);
    expect(refresh.request.body).toEqual({ refreshToken: 'refresh-1' });
    refresh.flush(authResponse('access-2', 'refresh-2'));

    const retry = backend.expectOne(`${api}/users/me`);
    expect(retry.request.headers.get('Authorization')).toBe('Bearer access-2');
    retry.flush({ ok: true });

    expect(result).toEqual({ ok: true });
    expect(storage.accessToken).toBe('access-2');
    expect(storage.refreshToken).toBe('refresh-2');
  });

  it('clears the session when the refresh is rejected', () => {
    storage.save('expired', 'revoked');
    let failed = false;

    http.get(`${api}/users/me`).subscribe({ error: () => (failed = true) });
    backend.expectOne(`${api}/users/me`).flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne(`${api}/auth/refresh`).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBe(true);
    expect(storage.accessToken).toBeNull();
    expect(storage.refreshToken).toBeNull();
  });
});
