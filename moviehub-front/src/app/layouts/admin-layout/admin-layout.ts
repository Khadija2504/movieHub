import { Component, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

import { Logo } from '../../shared/components/logo/logo';
import { Sidebar, SidebarSection } from '../../shared/components/sidebar/sidebar';

@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet, RouterLink, Logo, Sidebar],
  template: `
    <div class="flex min-h-dvh">
      <aside
        class="fixed inset-y-0 left-0 z-40 w-64 overflow-y-auto border-r border-border bg-surface p-4 transition-transform lg:static lg:translate-x-0"
        [class.-translate-x-full]="!sidebarOpen()"
      >
        <div class="mb-6 flex items-center justify-between">
          <app-logo />
          <span class="badge bg-primary/15 text-primary">Admin</span>
        </div>
        <app-sidebar [sections]="sections" label="Administration" />
      </aside>

      @if (sidebarOpen()) {
        <div class="fixed inset-0 z-30 bg-black/50 lg:hidden" (click)="sidebarOpen.set(false)"></div>
      }

      <div class="flex min-w-0 flex-1 flex-col">
        <header class="flex h-16 items-center justify-between border-b border-border px-4 sm:px-6">
          <button
            type="button"
            class="btn btn-ghost lg:hidden"
            (click)="sidebarOpen.set(true)"
            aria-label="Open sidebar"
          >
            <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="M4 7h16M4 12h16M4 17h16" />
            </svg>
          </button>
          <h1 class="text-lg font-semibold">Administration</h1>
          <a routerLink="/" class="btn btn-secondary">Back to site</a>
        </header>
        <main class="flex-1 p-4 sm:p-6">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
})
export class AdminLayout {
  protected readonly sidebarOpen = signal(false);

  protected readonly sections: SidebarSection[] = [
    {
      links: [
        { label: 'Dashboard', path: '/admin/dashboard' },
        { label: 'Reports & Analytics', path: '/admin/reports' },
      ],
    },
    {
      title: 'Content',
      links: [
        { label: 'Movies', path: '/admin/movies' },
        { label: 'Series', path: '/admin/series' },
        { label: 'Reviews', path: '/admin/reviews' },
      ],
    },
    {
      title: 'Cinemas',
      links: [
        { label: 'Cinemas', path: '/admin/cinemas' },
        { label: 'Rooms & Seats', path: '/admin/rooms' },
        { label: 'Screenings', path: '/admin/screenings' },
        { label: 'Bookings', path: '/admin/bookings' },
      ],
    },
    {
      title: 'Business',
      links: [
        { label: 'Users', path: '/admin/users' },
        { label: 'Payments', path: '/admin/payments' },
        { label: 'Subscriptions', path: '/admin/subscriptions' },
      ],
    },
  ];
}
