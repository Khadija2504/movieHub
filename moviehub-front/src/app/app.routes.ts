import { Routes } from '@angular/router';

import { adminGuard, authGuard, guestGuard } from './core/auth/auth.guards';
import { PublicLayout } from './layouts/public-layout/public-layout';

/**
 * Three areas, each with its own layout:
 *  - public pages        -> PublicLayout (navbar + footer)
 *  - /account/**         -> UserLayout   (authGuard: logged-in users)
 *  - /admin/**           -> AdminLayout  (adminGuard: ADMIN role)
 * Feature pages are lazy-loaded with loadComponent.
 */
export const routes: Routes = [
  {
    path: '',
    component: PublicLayout,
    children: [
      {
        path: '',
        title: 'MovieHub',
        loadComponent: () => import('./features/public/home/home-page').then((m) => m.HomePage),
      },
      {
        path: 'login',
        title: 'Log in · MovieHub',
        canActivate: [guestGuard],
        loadComponent: () => import('./features/auth/login/login-page').then((m) => m.LoginPage),
      },
      {
        path: 'register',
        title: 'Create account · MovieHub',
        canActivate: [guestGuard],
        loadComponent: () =>
          import('./features/auth/register/register-page').then((m) => m.RegisterPage),
      },
    ],
  },
  {
    path: 'account',
    canActivate: [authGuard],
    loadComponent: () => import('./layouts/user-layout/user-layout').then((m) => m.UserLayout),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'My Dashboard · MovieHub',
        loadComponent: () =>
          import('./features/user/dashboard/dashboard-page').then((m) => m.DashboardPage),
      },
    ],
  },
  {
    path: 'admin',
    canActivate: [adminGuard],
    loadComponent: () => import('./layouts/admin-layout/admin-layout').then((m) => m.AdminLayout),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Admin · MovieHub',
        loadComponent: () =>
          import('./features/admin/dashboard/admin-dashboard-page').then(
            (m) => m.AdminDashboardPage,
          ),
      },
    ],
  },
  {
    path: '**',
    component: PublicLayout,
    children: [
      {
        path: '',
        title: 'Page not found · MovieHub',
        loadComponent: () =>
          import('./features/not-found/not-found-page').then((m) => m.NotFoundPage),
      },
    ],
  },
];
