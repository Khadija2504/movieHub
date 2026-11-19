import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { Logo } from '../logo/logo';
import { UserMenu } from '../user-menu/user-menu';

interface NavLink {
  label: string;
  path: string;
}

@Component({
  selector: 'app-navbar',
  imports: [RouterLink, RouterLinkActive, Logo, UserMenu],
  templateUrl: './navbar.html',
})
export class Navbar {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly menuOpen = signal(false);

  protected readonly links: NavLink[] = [
    { label: 'Home', path: '/' },
    { label: 'Movies', path: '/movies' },
    { label: 'TV Series', path: '/series' },
    { label: 'Cinemas', path: '/cinemas' },
    { label: 'Plans', path: '/plans' },
  ];

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  protected search(event: Event, query: string): void {
    event.preventDefault();
    const q = query.trim();
    if (q) {
      void this.router.navigate(['/search'], { queryParams: { q } });
    }
  }

  protected logout(): void {
    this.closeMenu();
    this.auth.logout();
  }
}
