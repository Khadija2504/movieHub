import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { Avatar } from '../../shared/components/avatar/avatar';
import { Footer } from '../../shared/components/footer/footer';
import { Navbar } from '../../shared/components/navbar/navbar';
import { Sidebar, SidebarSection } from '../../shared/components/sidebar/sidebar';

@Component({
  selector: 'app-user-layout',
  imports: [RouterOutlet, Navbar, Sidebar, Footer, Avatar],
  template: `
    <div class="flex min-h-dvh flex-col">
      <app-navbar />
      <div class="container-page page-top flex flex-1 flex-col gap-8 pb-10 lg:flex-row">
        <aside class="lg:sticky lg:top-24 lg:h-fit lg:w-64 lg:shrink-0">
          @if (auth.user(); as user) {
            <div class="card mb-4 flex items-center gap-3 p-4">
              <app-avatar class="size-12 shadow-glow" [name]="user.firstName + ' ' + user.lastName" [url]="user.avatarUrl" />
              <div class="min-w-0">
                <p class="truncate font-semibold">{{ user.firstName }} {{ user.lastName }}</p>
                <p class="truncate text-xs text-muted">{{ user.email }}</p>
              </div>
            </div>
          }
          <div class="card p-3">
            <app-sidebar [sections]="sections" label="Account" />
          </div>
        </aside>
        <main class="min-w-0 flex-1 animate-fade-up">
          <router-outlet />
        </main>
      </div>
      <app-footer />
    </div>
  `,
})
export class UserLayout {
  protected readonly auth = inject(AuthService);

  protected readonly sections: SidebarSection[] = [
    {
      links: [
        { label: 'Dashboard', path: '/account/dashboard', icon: 'dashboard' },
        { label: 'My Profile', path: '/account/profile', icon: 'user' },
      ],
    },
    {
      title: 'Watch',
      links: [
        { label: 'Watchlist', path: '/account/watchlist', icon: 'bookmark' },
        { label: 'My Library', path: '/account/library', icon: 'library' },
        { label: 'Watch History', path: '/account/history', icon: 'history' },
        { label: 'My Reviews', path: '/account/reviews', icon: 'message' },
      ],
    },
    {
      title: 'Cinema',
      links: [{ label: 'My Bookings', path: '/account/bookings', icon: 'ticket' }],
    },
    {
      title: 'Billing',
      links: [
        { label: 'Subscription', path: '/account/subscription', icon: 'crown' },
        { label: 'Payments', path: '/account/payments', icon: 'card' },
        { label: 'Settings', path: '/account/settings', icon: 'settings' },
      ],
    },
  ];
}
