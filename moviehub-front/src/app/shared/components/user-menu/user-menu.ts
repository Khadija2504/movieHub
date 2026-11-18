import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';

/** Avatar button + dropdown shown in the navbar when logged in. */
@Component({
  selector: 'app-user-menu',
  imports: [RouterLink],
  host: { '(document:keydown.escape)': 'open.set(false)' },
  template: `
    @if (auth.user(); as user) {
      <div class="relative">
        <button
          type="button"
          class="flex items-center gap-2 rounded-full p-1 pr-3 hover:bg-surface-raised"
          (click)="open.set(!open())"
          [attr.aria-expanded]="open()"
          aria-haspopup="menu"
        >
          @if (user.avatarUrl) {
            <img [src]="user.avatarUrl" alt="" class="h-8 w-8 rounded-full object-cover" />
          } @else {
            <span class="grid h-8 w-8 place-items-center rounded-full bg-primary/20 text-sm font-semibold text-primary">
              {{ initials() }}
            </span>
          }
          <span class="text-sm font-medium">{{ user.firstName }}</span>
        </button>

        @if (open()) {
          <!-- click-outside catcher -->
          <div class="fixed inset-0 z-40" (click)="open.set(false)"></div>
          <div class="card absolute right-0 z-50 mt-2 w-56 p-1.5 shadow-xl" role="menu">
            <div class="border-b border-border px-3 py-2">
              <p class="truncate text-sm font-medium">{{ user.firstName }} {{ user.lastName }}</p>
              <p class="truncate text-xs text-muted">{{ user.email }}</p>
            </div>
            <a routerLink="/account/dashboard" (click)="open.set(false)" role="menuitem" class="menu-item">Dashboard</a>
            <a routerLink="/account/profile" (click)="open.set(false)" role="menuitem" class="menu-item">My Profile</a>
            <a routerLink="/account/watchlist" (click)="open.set(false)" role="menuitem" class="menu-item">My Watchlist</a>
            @if (auth.isAdmin()) {
              <a routerLink="/admin" (click)="open.set(false)" role="menuitem" class="menu-item text-accent">Admin panel</a>
            }
            <button type="button" (click)="logout()" role="menuitem" class="menu-item w-full text-left text-danger">
              Log out
            </button>
          </div>
        }
      </div>
    }
  `,
  styles: `
    .menu-item {
      display: block;
      border-radius: 0.5rem;
      padding: 0.5rem 0.75rem;
      font-size: 0.875rem;
    }
    .menu-item:hover {
      background: var(--color-surface-raised);
    }
  `,
})
export class UserMenu {
  protected readonly auth = inject(AuthService);
  protected readonly open = signal(false);

  protected readonly initials = computed(() => {
    const user = this.auth.user();
    return user ? `${user.firstName[0] ?? ''}${user.lastName[0] ?? ''}`.toUpperCase() : '';
  });

  protected logout(): void {
    this.open.set(false);
    this.auth.logout();
  }
}
