import { Component, inject } from '@angular/core';

import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-dashboard-page',
  template: `
    <h1 class="text-2xl font-bold">Welcome back, {{ auth.user()?.firstName }}</h1>
    <p class="mt-1 text-muted">Your watchlist, continue watching and upcoming bookings will appear here.</p>

    <div class="mt-6 grid gap-4 sm:grid-cols-3">
      @for (stat of stats; track stat.label) {
        <div class="card p-5">
          <p class="text-sm text-muted">{{ stat.label }}</p>
          <p class="mt-2 font-display text-3xl font-bold">-</p>
        </div>
      }
    </div>
  `,
})
export class DashboardPage {
  protected readonly auth = inject(AuthService);

  protected readonly stats = [
    { label: 'Watchlist' },
    { label: 'Library' },
    { label: 'Upcoming bookings' },
  ];
}
