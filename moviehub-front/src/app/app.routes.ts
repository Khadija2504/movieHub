import { Routes } from '@angular/router';

import { PublicLayout } from './layouts/public-layout/public-layout';

/**
 * Three areas, each with its own layout:
 *  - public pages        -> PublicLayout (navbar + footer)
 *  - /account/**         -> UserLayout   (requires login: authGuard added in Phase 1)
 *  - /admin/**           -> AdminLayout  (requires ADMIN role: adminGuard added in Phase 1)
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
    ],
  },
  {
    path: 'account',
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
