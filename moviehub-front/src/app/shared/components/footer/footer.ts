import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Logo } from '../logo/logo';

@Component({
  selector: 'app-footer',
  imports: [RouterLink, Logo],
  template: `
    <footer class="mt-16 border-t border-border bg-surface">
      <div class="container-page grid gap-8 py-10 sm:grid-cols-2 lg:grid-cols-4">
        <div class="space-y-3">
          <app-logo />
          <p class="text-sm text-muted">Stream movies and series, and book your next cinema night.</p>
        </div>

        <div>
          <h3 class="mb-3 text-sm font-semibold">Discover</h3>
          <ul class="space-y-2 text-sm text-muted">
            <li><a routerLink="/movies" class="hover:text-foreground">Movies</a></li>
            <li><a routerLink="/series" class="hover:text-foreground">TV Series</a></li>
            <li><a routerLink="/cinemas" class="hover:text-foreground">Cinemas</a></li>
          </ul>
        </div>

        <div>
          <h3 class="mb-3 text-sm font-semibold">MovieHub</h3>
          <ul class="space-y-2 text-sm text-muted">
            <li><a routerLink="/plans" class="hover:text-foreground">Subscription plans</a></li>
            <li><a routerLink="/about" class="hover:text-foreground">About us</a></li>
            <li><a routerLink="/contact" class="hover:text-foreground">Contact</a></li>
          </ul>
        </div>

        <div class="text-sm text-muted">
          <h3 class="mb-3 font-semibold text-foreground">Data source</h3>
          <p>
            This product uses the TMDB API but is not endorsed or certified by TMDB.
          </p>
        </div>
      </div>
      <div class="border-t border-border py-4 text-center text-xs text-muted">
        © {{ year }} MovieHub. All rights reserved.
      </div>
    </footer>
  `,
})
export class Footer {
  protected readonly year = new Date().getFullYear();
}
