import { Component } from '@angular/core';

@Component({
  selector: 'app-admin-dashboard-page',
  template: `
    <h2 class="text-2xl font-bold">Dashboard</h2>
    <p class="mt-1 text-muted">Platform statistics will appear here (Phase 7).</p>

    <div class="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      @for (kpi of kpis; track kpi) {
        <div class="card p-5">
          <p class="text-sm text-muted">{{ kpi }}</p>
          <p class="mt-2 font-display text-3xl font-bold">-</p>
        </div>
      }
    </div>
  `,
})
export class AdminDashboardPage {
  protected readonly kpis = ['Users', 'Revenue (30d)', 'Bookings (30d)', 'Active subscriptions'];
}
