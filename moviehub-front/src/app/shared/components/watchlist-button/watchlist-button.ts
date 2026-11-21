import { Component, computed, inject, input } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage } from '../../../core/http/api-error.util';
import { MediaType } from '../../../core/models/catalog';
import { WatchlistService } from '../../../core/services/watchlist.service';
import { ToastService } from '../../../core/ui/toast.service';
import { Icon } from '../icon/icon';

/**
 * Add/remove a title from the watchlist.
 * variant "icon": round button for cards; variant "button": labelled button for details pages.
 * Anonymous users are sent to the login page and come back afterwards.
 */
@Component({
  selector: 'app-watchlist-button',
  imports: [Icon],
  template: `
    @if (variant() === 'icon') {
      <button
        type="button"
        class="grid size-9 place-items-center rounded-full backdrop-blur-md transition duration-200 active:scale-90"
        [class]="saved() ? 'bg-white text-background' : 'bg-black/55 text-white hover:bg-black/75'"
        [attr.aria-pressed]="saved()"
        [attr.aria-label]="(saved() ? 'Remove ' : 'Add ') + title() + (saved() ? ' from' : ' to') + ' watchlist'"
        [title]="saved() ? 'In your watchlist' : 'Add to watchlist'"
        [disabled]="pending()"
        (click)="toggle($event)"
      >
        <app-icon [name]="saved() ? 'check' : 'plus'" class="size-4" [strokeWidth]="2.5" />
      </button>
    } @else {
      <button
        type="button"
        class="btn btn-lg"
        [class]="saved() ? 'btn-light' : 'btn-secondary'"
        [attr.aria-pressed]="saved()"
        [disabled]="pending()"
        (click)="toggle($event)"
      >
        <app-icon [name]="saved() ? 'check' : 'bookmark'" class="size-4" />
        {{ saved() ? 'In watchlist' : 'Watchlist' }}
      </button>
    }
  `,
})
export class WatchlistButton {
  readonly mediaType = input.required<MediaType>();
  readonly tmdbId = input.required<number>();
  readonly title = input('this title');
  readonly variant = input<'icon' | 'button'>('icon');

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly watchlist = inject(WatchlistService);
  private readonly toast = inject(ToastService);

  protected readonly saved = computed(() => this.watchlist.isSaved(this.mediaType(), this.tmdbId()));
  protected readonly pending = computed(() => this.watchlist.isPending(this.mediaType(), this.tmdbId()));

  protected toggle(event: Event): void {
    // Cards are links: don't navigate when the button is clicked.
    event.preventDefault();
    event.stopPropagation();

    if (!this.auth.isAuthenticated()) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    this.watchlist.toggle(this.mediaType(), this.tmdbId()).subscribe({
      next: (saved) => this.toast.success(saved ? `${this.title()} added to your watchlist` : `Removed from your watchlist`),
      error: (error: unknown) => this.toast.error(errorMessage(error, 'Could not update your watchlist')),
    });
  }
}
