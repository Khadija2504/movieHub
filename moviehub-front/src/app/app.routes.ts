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
        path: 'movies',
        title: 'Movies · MovieHub',
        data: { mediaType: 'MOVIE' },
        loadComponent: () => import('./features/catalog/browse/browse-page').then((m) => m.BrowsePage),
      },
      {
        path: 'movies/:id',
        title: 'Movie · MovieHub',
        loadComponent: () =>
          import('./features/catalog/movie-details/movie-details-page').then((m) => m.MovieDetailsPage),
      },
      {
        path: 'series',
        title: 'TV Series · MovieHub',
        data: { mediaType: 'SERIES' },
        loadComponent: () => import('./features/catalog/browse/browse-page').then((m) => m.BrowsePage),
      },
      {
        path: 'series/:id',
        title: 'TV Series · MovieHub',
        loadComponent: () =>
          import('./features/catalog/series-details/series-details-page').then((m) => m.SeriesDetailsPage),
      },
      {
        path: 'search',
        title: 'Search · MovieHub',
        loadComponent: () => import('./features/catalog/search/search-page').then((m) => m.SearchPage),
      },
      {
        path: 'cinemas',
        title: 'Cinemas · MovieHub',
        loadComponent: () => import('./features/cinemas/list/cinemas-page').then((m) => m.CinemasPage),
      },
      {
        path: 'cinemas/:id',
        title: 'Cinema · MovieHub',
        loadComponent: () =>
          import('./features/cinemas/details/cinema-details-page').then((m) => m.CinemaDetailsPage),
      },
      {
        path: 'screenings/:id',
        title: 'Screening · MovieHub',
        loadComponent: () => import('./features/cinemas/screening/screening-page').then((m) => m.ScreeningPage),
      },
      {
        path: 'checkout/:id',
        title: 'Checkout · MovieHub',
        canActivate: [authGuard],
        loadComponent: () => import('./features/booking/checkout/checkout-page').then((m) => m.CheckoutPage),
      },
      {
        path: 'plans',
        title: 'Plans · MovieHub',
        loadComponent: () => import('./features/streaming/plans/plans-page').then((m) => m.PlansPage),
      },
      {
        path: 'pay/:id',
        title: 'Checkout · MovieHub',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/streaming/checkout/order-checkout-page').then((m) => m.OrderCheckoutPage),
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
    // Full-screen player, outside the layouts.
    path: 'watch/:id',
    title: 'Watch · MovieHub',
    canActivate: [authGuard],
    loadComponent: () => import('./features/streaming/player/player-page').then((m) => m.PlayerPage),
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
      {
        path: 'profile',
        title: 'My Profile · MovieHub',
        loadComponent: () => import('./features/user/profile/profile-page').then((m) => m.ProfilePage),
      },
      {
        path: 'watchlist',
        title: 'My Watchlist · MovieHub',
        loadComponent: () =>
          import('./features/user/watchlist/watchlist-page').then((m) => m.WatchlistPage),
      },
      {
        path: 'reviews',
        title: 'My Reviews · MovieHub',
        loadComponent: () =>
          import('./features/user/reviews/my-reviews-page').then((m) => m.MyReviewsPage),
      },
      {
        path: 'bookings',
        title: 'My Bookings · MovieHub',
        loadComponent: () =>
          import('./features/user/bookings/my-bookings-page').then((m) => m.MyBookingsPage),
      },
      {
        path: 'bookings/:id',
        title: 'Ticket · MovieHub',
        loadComponent: () =>
          import('./features/user/bookings/booking-details-page').then((m) => m.BookingDetailsPage),
      },
      {
        path: 'library',
        title: 'My Library · MovieHub',
        loadComponent: () => import('./features/user/library/library-page').then((m) => m.LibraryPage),
      },
      {
        path: 'history',
        title: 'Watch History · MovieHub',
        loadComponent: () => import('./features/user/history/history-page').then((m) => m.HistoryPage),
      },
      {
        path: 'subscription',
        title: 'Subscription · MovieHub',
        loadComponent: () =>
          import('./features/user/subscription/subscription-page').then((m) => m.SubscriptionPage),
      },
      {
        path: 'payments',
        title: 'Payments · MovieHub',
        loadComponent: () => import('./features/user/payments/payments-page').then((m) => m.PaymentsPage),
      },
      {
        path: 'settings',
        title: 'Settings · MovieHub',
        loadComponent: () =>
          import('./features/user/settings/settings-page').then((m) => m.SettingsPage),
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
