import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Logo } from '../logo/logo';

interface FooterColumn {
  title: string;
  links: { label: string; path: string }[];
}

@Component({
  selector: 'app-footer',
  imports: [RouterLink, Logo],
  template: `
    <footer class="relative mt-24 border-t border-border bg-surface/40">
      <div class="pointer-events-none absolute inset-x-0 -top-px h-px bg-linear-to-r from-transparent via-primary/60 to-transparent" aria-hidden="true"></div>

      <div class="container-page grid gap-12 py-16 lg:grid-cols-[1.4fr_repeat(3,1fr)]">
        <div class="max-w-sm space-y-4">
          <app-logo />
          <p class="text-sm leading-relaxed text-muted">
            Stream movies and series, rent or buy the latest releases, and book the best seats at a cinema near you.
          </p>
        </div>

        @for (column of columns; track column.title) {
          <div>
            <h3 class="mb-4 text-sm font-semibold">{{ column.title }}</h3>
            <ul class="space-y-3 text-sm text-muted">
              @for (link of column.links; track link.path) {
                <li><a [routerLink]="link.path" class="transition-colors hover:text-foreground">{{ link.label }}</a></li>
              }
            </ul>
          </div>
        }
      </div>

      <div class="border-t border-border">
        <div class="container-page flex flex-col gap-3 py-6 text-xs text-subtle sm:flex-row sm:items-center sm:justify-between">
          <p>© {{ year }} MovieHub. All rights reserved.</p>
          <p>Movie and TV data provided by TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.</p>
        </div>
      </div>
    </footer>
  `,
})
export class Footer {
  protected readonly year = new Date().getFullYear();

  protected readonly columns: FooterColumn[] = [
    {
      title: 'Discover',
      links: [
        { label: 'Movies', path: '/movies' },
        { label: 'TV Series', path: '/series' },
        { label: 'Search', path: '/search' },
        { label: 'Cinemas', path: '/cinemas' },
      ],
    },
    {
      title: 'Account',
      links: [
        { label: 'My account', path: '/account' },
        { label: 'Watchlist', path: '/account/watchlist' },
        { label: 'Bookings', path: '/account/bookings' },
        { label: 'Subscription plans', path: '/plans' },
      ],
    },
    {
      title: 'MovieHub',
      links: [
        { label: 'About us', path: '/about' },
        { label: 'Contact', path: '/contact' },
      ],
    },
  ];
}
