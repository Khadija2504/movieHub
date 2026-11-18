import { Component, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { Logo } from '../logo/logo';

interface NavLink {
  label: string;
  path: string;
}

@Component({
  selector: 'app-navbar',
  imports: [RouterLink, RouterLinkActive, Logo],
  templateUrl: './navbar.html',
})
export class Navbar {
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
}
