import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { Avatar } from '../../shared/components/avatar/avatar';
import { Icon } from '../../shared/components/icon/icon';
import { Logo } from '../../shared/components/logo/logo';
import { Sidebar, SidebarSection } from '../../shared/components/sidebar/sidebar';

@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet, RouterLink, Logo, Sidebar, Icon, Avatar],
  host: { '(document:keydown.escape)': 'sidebarOpen.set(false)' },
  template: `
    <div class="flex min-h-dvh">
      <!-- Sidebar (drawer on mobile) -->
      <aside
        class="fixed inset-y-0 left-0 z-40 flex w-72 flex-col border-r border-border bg-surface/95 backdrop-blur-xl transition-transform duration-300 lg:sticky lg:top-0 lg:h-dvh lg:translate-x-0"
        [class.-translate-x-full]="!sidebarOpen()"
      >
        <div class="flex h-[4.5rem] items-center justify-between px-6">
          <app-logo />
          <span class="badge bg-primary/15 text-primary">Admin</span>
        </div>
        <div class="flex-1 overflow-y-auto px-4 pb-6">
          <app-sidebar [sections]="sections" label="Administration" (navigate)="sidebarOpen.set(false)" />
        </div>
        <div class="border-t border-border p-4">
          <a routerLink="/" class="btn btn-ghost w-full justify-start">
            <app-icon name="arrow-right" class="size-4 rotate-180" /> Back to MovieHub
          </a>
        </div>
      </aside>

      @if (sidebarOpen()) {
        <div class="fixed inset-0 z-30 bg-black/60 backdrop-blur-sm lg:hidden animate-fade-in" (click)="sidebarOpen.set(false)"></div>
      }

      <div class="flex min-w-0 flex-1 flex-col">
        <header class="glass sticky top-0 z-20 flex h-[4.5rem] items-center gap-4 border-x-0! border-t-0! px-4 sm:px-8">
          <button type="button" class="btn btn-ghost btn-icon lg:hidden" (click)="sidebarOpen.set(true)" aria-label="Open sidebar">
            <app-icon name="menu" class="size-6" />
          </button>
          <div>
            <p class="text-xs text-muted">MovieHub</p>
            <h1 class="text-base font-semibold">Administration</h1>
          </div>
          <div class="ml-auto flex items-center gap-3">
            @if (auth.user(); as user) {
              <div class="hidden items-center gap-3 sm:flex">
                <div class="text-right">
                  <p class="text-sm font-medium">{{ user.firstName }} {{ user.lastName }}</p>
                  <p class="text-xs text-muted">{{ user.email }}</p>
                </div>
                <app-avatar class="size-10" [name]="user.firstName + ' ' + user.lastName" [url]="user.avatarUrl" />
              </div>
            }
            <button type="button" class="btn btn-ghost btn-icon" (click)="auth.logout()" aria-label="Sign out" title="Sign out">
              <app-icon name="logout" class="size-5" />
            </button>
          </div>
        </header>
        <main class="flex-1 p-4 sm:p-8 animate-fade-up">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
})
export class AdminLayout {
  protected readonly auth = inject(AuthService);
  protected readonly sidebarOpen = signal(false);

  protected readonly sections: SidebarSection[] = [
    {
      links: [
        { label: 'Dashboard', path: '/admin/dashboard', icon: 'dashboard' },
        { label: 'Reports & Analytics', path: '/admin/reports', icon: 'chart' },
      ],
    },
    {
      title: 'Content',
      links: [
        { label: 'Movies', path: '/admin/movies', icon: 'film' },
        { label: 'Series', path: '/admin/series', icon: 'tv' },
        { label: 'Reviews', path: '/admin/reviews', icon: 'message' },
      ],
    },
    {
      title: 'Cinemas',
      links: [
        { label: 'Cinemas', path: '/admin/cinemas', icon: 'clapperboard' },
        { label: 'Rooms & Seats', path: '/admin/rooms', icon: 'seat' },
        { label: 'Screenings', path: '/admin/screenings', icon: 'calendar' },
        { label: 'Bookings', path: '/admin/bookings', icon: 'ticket' },
      ],
    },
    {
      title: 'Business',
      links: [
        { label: 'Users', path: '/admin/users', icon: 'users' },
        { label: 'Payments', path: '/admin/payments', icon: 'card' },
        { label: 'Subscriptions', path: '/admin/subscriptions', icon: 'crown' },
      ],
    },
  ];
}
