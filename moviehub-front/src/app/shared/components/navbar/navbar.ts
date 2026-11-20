import { Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { Icon, IconName } from '../icon/icon';
import { Logo } from '../logo/logo';
import { UserMenu } from '../user-menu/user-menu';

interface NavLink {
  label: string;
  path: string;
  icon: IconName;
}

/**
 * Fixed top bar. Transparent over hero images at the top of the page,
 * frosted glass once the user scrolls.
 */
@Component({
  selector: 'app-navbar',
  imports: [RouterLink, RouterLinkActive, Logo, UserMenu, Icon],
  templateUrl: './navbar.html',
  host: { '(window:scroll)': 'onScroll()' },
})
export class Navbar {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly menuOpen = signal(false);
  protected readonly scrolled = signal(typeof window !== 'undefined' && window.scrollY > 8);

  protected readonly links: NavLink[] = [
    { label: 'Home', path: '/', icon: 'home' },
    { label: 'Movies', path: '/movies', icon: 'film' },
    { label: 'TV Series', path: '/series', icon: 'tv' },
    { label: 'Cinemas', path: '/cinemas', icon: 'clapperboard' },
    { label: 'Plans', path: '/plans', icon: 'crown' },
  ];

  constructor() {
    // Close the mobile menu after any navigation.
    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.menuOpen.set(false));
  }

  protected onScroll(): void {
    this.scrolled.set(window.scrollY > 8);
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  protected search(event: Event, input: HTMLInputElement): void {
    event.preventDefault();
    const q = input.value.trim();
    if (q) {
      input.value = '';
      input.blur();
      void this.router.navigate(['/search'], { queryParams: { q } });
    }
  }

  protected logout(): void {
    this.closeMenu();
    this.auth.logout();
  }
}
