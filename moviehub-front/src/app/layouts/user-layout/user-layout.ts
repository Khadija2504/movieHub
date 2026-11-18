import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { Navbar } from '../../shared/components/navbar/navbar';
import { Sidebar, SidebarSection } from '../../shared/components/sidebar/sidebar';

@Component({
  selector: 'app-user-layout',
  imports: [RouterOutlet, Navbar, Sidebar],
  template: `
    <div class="flex min-h-dvh flex-col">
      <app-navbar />
      <div class="container-page flex flex-1 flex-col gap-6 py-6 lg:flex-row">
        <aside class="card h-fit p-3 lg:sticky lg:top-22 lg:w-60 lg:shrink-0">
          <app-sidebar [sections]="sections" label="Account" />
        </aside>
        <main class="min-w-0 flex-1">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
})
export class UserLayout {
  protected readonly sections: SidebarSection[] = [
    {
      links: [
        { label: 'Dashboard', path: '/account/dashboard' },
        { label: 'My Profile', path: '/account/profile' },
      ],
    },
    {
      title: 'Watch',
      links: [
        { label: 'My Watchlist', path: '/account/watchlist' },
        { label: 'My Library', path: '/account/library' },
        { label: 'Watch History', path: '/account/history' },
        { label: 'My Reviews', path: '/account/reviews' },
      ],
    },
    {
      title: 'Cinema',
      links: [{ label: 'My Bookings', path: '/account/bookings' }],
    },
    {
      title: 'Billing',
      links: [
        { label: 'Subscription', path: '/account/subscription' },
        { label: 'Payment History', path: '/account/payments' },
        { label: 'Settings', path: '/account/settings' },
      ],
    },
  ];
}
