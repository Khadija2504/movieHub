import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { adminGuard, authGuard, guestGuard } from './auth.guards';
import { AuthService } from './auth.service';

describe('auth guards', () => {
  const authenticated = signal(false);
  const admin = signal(false);

  const run = (guard: typeof authGuard, url = '/account/bookings') =>
    TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );

  const serialize = (result: unknown) =>
    TestBed.inject(Router).serializeUrl(result as UrlTree);

  beforeEach(() => {
    authenticated.set(false);
    admin.set(false);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isAuthenticated: authenticated, isAdmin: admin } },
      ],
    });
  });

  it('authGuard sends anonymous users to login with a return URL', () => {
    expect(serialize(run(authGuard))).toBe('/login?returnUrl=%2Faccount%2Fbookings');
  });

  it('authGuard lets logged-in users through', () => {
    authenticated.set(true);
    expect(run(authGuard)).toBe(true);
  });

  it('adminGuard sends regular users home', () => {
    authenticated.set(true);
    expect(serialize(run(adminGuard, '/admin'))).toBe('/');
  });

  it('adminGuard lets admins through', () => {
    authenticated.set(true);
    admin.set(true);
    expect(run(adminGuard, '/admin')).toBe(true);
  });

  it('guestGuard redirects logged-in users to their area', () => {
    authenticated.set(true);
    expect(serialize(run(guestGuard))).toBe('/account');
    admin.set(true);
    expect(serialize(run(guestGuard))).toBe('/admin');
  });
});
