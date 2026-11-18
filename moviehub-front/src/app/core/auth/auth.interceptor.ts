import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

const withToken = (req: HttpRequest<unknown>, token: string) =>
  req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });

/**
 * - Adds "Authorization: Bearer <token>" to calls to our API (never to third-party URLs).
 * - On 401, refreshes the access token once and retries the request.
 * - If the refresh fails, ends the session and sends the user to /login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(environment.apiUrl)) {
    return next(req);
  }

  const auth = inject(AuthService);
  const isAuthEndpoint = req.url.startsWith(`${environment.apiUrl}/auth/`);
  const token = auth.getAccessToken();

  return next(token && !isAuthEndpoint ? withToken(req, token) : req).pipe(
    catchError((error: unknown) => {
      const canRefresh =
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !isAuthEndpoint &&
        auth.hasRefreshToken();

      if (!canRefresh) {
        return throwError(() => error);
      }

      return auth.refreshAccessToken().pipe(
        switchMap((newToken) => next(withToken(req, newToken))),
        catchError((refreshError: unknown) => {
          // Only end the session when the refresh itself was rejected, not when the retried call failed.
          if (refreshError instanceof HttpErrorResponse && refreshError.url?.includes('/auth/refresh')) {
            auth.handleSessionExpired();
            return throwError(() => error);
          }
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};
