import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Icon, IconName } from '../../../shared/components/icon/icon';

interface Kpi {
  label: string;
  icon: IconName;
  tint: string;
}

interface QuickAction {
  label: string;
  description: string;
  icon: IconName;
  link: string;
}

@Component({
  selector: 'app-admin-dashboard-page',
  imports: [RouterLink, Icon],
  template: `
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h2 class="text-3xl font-extrabold">Dashboard</h2>
        <p class="mt-1 text-muted">Platform overview at a glance.</p>
      </div>
      <span class="badge bg-white/5 text-muted ring-1 ring-border">
        <app-icon name="info" class="size-3.5" /> Live statistics arrive with the reports module
      </span>
    </div>

    <!-- KPIs -->
    <section class="mt-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      @for (kpi of kpis; track kpi.label) {
        <div class="card relative overflow-hidden p-6">
          <div class="pointer-events-none absolute -right-8 -top-8 size-28 rounded-full opacity-40 blur-2xl" [class]="kpi.tint"></div>
          <div class="relative flex items-center justify-between">
            <p class="text-sm font-medium text-muted">{{ kpi.label }}</p>
            <span class="grid size-10 place-items-center rounded-xl ring-1 ring-white/10" [class]="kpi.tint">
              <app-icon [name]="kpi.icon" class="size-5" />
            </span>
          </div>
          <p class="relative mt-4 font-display text-3xl font-bold">—</p>
          <p class="relative mt-1 text-xs text-subtle">No data yet</p>
        </div>
      }
    </section>

    <!-- Quick actions -->
    <section class="mt-10">
      <h3 class="section-title">Quick actions</h3>
      <div class="mt-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        @for (action of actions; track action.label) {
          <a [routerLink]="action.link" class="card card-hover group flex items-start gap-4 p-5">
            <span class="grid size-11 shrink-0 place-items-center rounded-xl bg-white/5 text-foreground ring-1 ring-border transition group-hover:bg-brand group-hover:ring-transparent">
              <app-icon [name]="action.icon" class="size-5" />
            </span>
            <div class="min-w-0 flex-1">
              <p class="font-semibold">{{ action.label }}</p>
              <p class="mt-0.5 text-sm text-muted">{{ action.description }}</p>
            </div>
            <app-icon name="arrow-right" class="mt-1 size-4 text-subtle transition group-hover:translate-x-0.5 group-hover:text-foreground" />
          </a>
        }
      </div>
    </section>
  `,
})
export class AdminDashboardPage {
  protected readonly kpis: Kpi[] = [
    { label: 'Total users', icon: 'users', tint: 'bg-sky-500/15 text-sky-400' },
    { label: 'Revenue (30 days)', icon: 'wallet', tint: 'bg-emerald-500/15 text-emerald-400' },
    { label: 'Bookings (30 days)', icon: 'ticket', tint: 'bg-primary/15 text-primary' },
    { label: 'Active subscriptions', icon: 'crown', tint: 'bg-amber-500/15 text-amber-400' },
  ];

  protected readonly actions: QuickAction[] = [
    { label: 'Import a movie', description: 'Add a title from TMDB and set its prices', icon: 'film', link: '/admin/movies' },
    { label: 'Import a series', description: 'Add a show and make it streamable', icon: 'tv', link: '/admin/series' },
    { label: 'Schedule a screening', description: 'Plan showtimes in your cinema rooms', icon: 'calendar', link: '/admin/screenings' },
    { label: 'Manage cinemas', description: 'Cinemas, rooms and seat layouts', icon: 'clapperboard', link: '/admin/cinemas' },
    { label: 'Manage users', description: 'Roles, suspensions and accounts', icon: 'users', link: '/admin/users' },
    { label: 'View reports', description: 'Revenue, bookings and growth', icon: 'chart', link: '/admin/reports' },
  ];
}
