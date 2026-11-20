import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { Icon, IconName } from '../icon/icon';

interface MenuLink {
  label: string;
  path: string;
  icon: IconName;
}

/** Avatar button + dropdown shown in the navbar when logged in. */
@Component({
  selector: 'app-user-menu',
  imports: [RouterLink, Icon],
  host: { '(document:keydown.escape)': 'open.set(false)' },
  template: `
    @if (auth.user(); as user) {
      <div class="relative">
        <button
          type="button"
          class="flex items-center gap-2 rounded-full border border-border bg-white/[0.04] py-1 pl-1 pr-3 transition hover:border-border-strong"
          (click)="open.set(!open())"
          [attr.aria-expanded]="open()"
          aria-haspopup="menu"
        >
          @if (user.avatarUrl) {
            <img [src]="user.avatarUrl" alt="" class="size-8 rounded-full object-cover" />
          } @else {
            <span class="grid size-8 place-items-center rounded-full bg-brand font-display text-xs font-bold">{{ initials() }}</span>
          }
          <span class="text-sm font-medium">{{ user.firstName }}</span>
          <app-icon name="chevron-down" class="size-4 text-muted transition-transform" [class.rotate-180]="open()" />
        </button>

        @if (open()) {
          <div class="fixed inset-0 z-40" (click)="open.set(false)"></div>
          <div class="glass absolute right-0 z-50 mt-3 w-64 origin-top-right rounded-2xl p-2 shadow-2xl animate-scale-in" role="menu">
            <div class="flex items-center gap-3 px-3 py-3">
              <span class="grid size-10 shrink-0 place-items-center rounded-full bg-brand font-display text-sm font-bold">{{ initials() }}</span>
              <div class="min-w-0">
                <p class="truncate text-sm font-semibold">{{ user.firstName }} {{ user.lastName }}</p>
                <p class="truncate text-xs text-muted">{{ user.email }}</p>
              </div>
            </div>
            <div class="my-1 h-px bg-border"></div>
            @for (link of links; track link.path) {
              <a [routerLink]="link.path" (click)="open.set(false)" role="menuitem" class="menu-item">
                <app-icon [name]="link.icon" class="size-4 text-muted" />
                {{ link.label }}
              </a>
            }
            @if (auth.isAdmin()) {
              <a routerLink="/admin" (click)="open.set(false)" role="menuitem" class="menu-item text-accent">
                <app-icon name="shield" class="size-4" />
                Admin panel
              </a>
            }
            <div class="my-1 h-px bg-border"></div>
            <button type="button" (click)="logout()" role="menuitem" class="menu-item w-full text-danger">
              <app-icon name="logout" class="size-4" />
              Sign out
            </button>
          </div>
        }
      </div>
    }
  `,
  styles: `
    .menu-item {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      border-radius: 0.75rem;
      padding: 0.6rem 0.75rem;
      font-size: 0.875rem;
      transition: background-color 0.15s;
    }
    .menu-item:hover {
      background: rgb(255 255 255 / 0.06);
    }
  `,
})
export class UserMenu {
  protected readonly auth = inject(AuthService);
  protected readonly open = signal(false);

  protected readonly links: MenuLink[] = [
    { label: 'Dashboard', path: '/account/dashboard', icon: 'dashboard' },
    { label: 'My Watchlist', path: '/account/watchlist', icon: 'bookmark' },
    { label: 'My Library', path: '/account/library', icon: 'library' },
    { label: 'My Bookings', path: '/account/bookings', icon: 'ticket' },
    { label: 'Settings', path: '/account/settings', icon: 'settings' },
  ];

  protected readonly initials = computed(() => {
    const user = this.auth.user();
    return user ? `${user.firstName[0] ?? ''}${user.lastName[0] ?? ''}`.toUpperCase() : '';
  });

  protected logout(): void {
    this.open.set(false);
    this.auth.logout();
  }
}
