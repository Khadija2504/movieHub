import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  Observable,
  catchError,
  finalize,
  map,
  of,
  shareReplay,
  tap,
  throwError,
} from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthResponse, LoginRequest, RegisterRequest } from '../models/auth';
import { User } from '../models/user';
import { TokenStorage } from './token-storage';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly storage = inject(TokenStorage);
  private readonly authUrl = `${environment.apiUrl}/auth`;

  private readonly currentUser = signal<User | null>(null);
  private refreshInFlight$: Observable<string> | null = null;

  /** The logged-in user, or null. */
  readonly user = this.currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUser() !== null);
  readonly isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');

  /**
   * Called once at startup (provideAppInitializer): restores the session from stored tokens.
   * Never fails; an invalid session is simply cleared.
   */
  init(): Observable<unknown> {
    if (!this.storage.accessToken && !this.storage.refreshToken) {
      return of(null);
    }
    return this.http.get<User>(`${environment.apiUrl}/users/me`).pipe(
      tap((user) => this.currentUser.set(user)),
      catchError(() => {
        this.clearSession();
        return of(null);
      }),
    );
  }

  login(request: LoginRequest): Observable<User> {
    return this.http
      .post<AuthResponse>(`${this.authUrl}/login`, request)
      .pipe(map((response) => this.startSession(response)));
  }

  register(request: RegisterRequest): Observable<User> {
    return this.http
      .post<AuthResponse>(`${this.authUrl}/register`, request)
      .pipe(map((response) => this.startSession(response)));
  }

  logout(): void {
    const refreshToken = this.storage.refreshToken;
    if (refreshToken) {
      // Fire and forget: the local session is cleared whatever the server answers.
      this.http.post(`${this.authUrl}/logout`, { refreshToken }).subscribe({ error: () => {} });
    }
    this.clearSession();
    void this.router.navigateByUrl('/');
  }

  getAccessToken(): string | null {
    return this.storage.accessToken;
  }

  hasRefreshToken(): boolean {
    return this.storage.refreshToken !== null;
  }

  /**
   * Gets a new access token using the refresh token. Concurrent callers share one request,
   * so several parallel 401s only trigger a single refresh.
   */
  refreshAccessToken(): Observable<string> {
    const refreshToken = this.storage.refreshToken;
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token'));
    }
    this.refreshInFlight$ ??= this.http
      .post<AuthResponse>(`${this.authUrl}/refresh`, { refreshToken })
      .pipe(
        map((response) => {
          this.startSession(response);
          return response.accessToken;
        }),
        finalize(() => (this.refreshInFlight$ = null)),
        shareReplay(1),
      );
    return this.refreshInFlight$;
  }

  /** Called by the interceptor when the session cannot be renewed. */
  handleSessionExpired(): void {
    const returnUrl = this.router.url;
    this.clearSession();
    void this.router.navigate(['/login'], {
      queryParams: returnUrl && returnUrl !== '/' ? { returnUrl } : {},
    });
  }

  /** Replaces the stored tokens and user (e.g. after a password change returns a new token pair). */
  applySession(response: AuthResponse): User {
    return this.startSession(response);
  }

  /** Updates the cached user after a profile change (name, avatar). */
  updateUser(user: User): void {
    this.currentUser.set(user);
  }

  /** Ends the session locally without calling the server (e.g. the account was just deleted). */
  endSession(): void {
    this.clearSession();
    void this.router.navigateByUrl('/');
  }

  private startSession(response: AuthResponse): User {
    this.storage.save(response.accessToken, response.refreshToken);
    this.currentUser.set(response.user);
    return response.user;
  }

  private clearSession(): void {
    this.storage.clear();
    this.currentUser.set(null);
  }
}
