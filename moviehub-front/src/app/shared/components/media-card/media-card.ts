import { DecimalPipe } from '@angular/common';
import { Component, booleanAttribute, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { MediaSummary } from '../../../core/models/catalog';
import { WatchlistService } from '../../../core/services/watchlist.service';
import { Icon } from '../icon/icon';
import { WatchlistButton } from '../watchlist-button/watchlist-button';

/** Poster card linking to the movie / series details page, with a hover reveal. */
@Component({
  selector: 'app-media-card',
  imports: [RouterLink, DecimalPipe, Icon, WatchlistButton],
  host: { class: 'group/card relative block' },
  template: `
    <a [routerLink]="link()" class="group block focus-visible:outline-none" [attr.aria-label]="item().title">
      <div
        class="relative aspect-2/3 overflow-hidden rounded-card bg-surface-raised ring-1 ring-border transition duration-300 ease-out group-hover:-translate-y-1.5 group-hover:shadow-[0_24px_48px_-16px_rgb(0_0_0/0.8)] group-hover:ring-white/25 group-focus-visible:ring-2 group-focus-visible:ring-primary"
      >
        @if (item().posterUrl) {
          <img
            [src]="item().posterUrl"
            alt=""
            loading="lazy"
            class="h-full w-full object-cover transition duration-500 group-hover:scale-[1.06]"
          />
        } @else {
          <div class="grid h-full place-items-center bg-linear-to-br from-surface-hover to-surface p-4 text-center">
            <app-icon name="film" class="size-8 text-subtle" />
          </div>
        }

        <!-- Hover overlay -->
        <div class="absolute inset-0 flex flex-col justify-end bg-linear-to-t from-black/95 via-black/40 to-transparent p-3 opacity-0 transition duration-300 group-hover:opacity-100 group-focus-visible:opacity-100">
          <span class="mb-2 grid size-10 translate-y-2 place-items-center rounded-full bg-white text-background shadow-lg transition duration-300 group-hover:translate-y-0">
            <app-icon name="play" filled class="ml-0.5 size-4" />
          </span>
          @if (item().overview) {
            <p class="line-clamp-3 text-xs leading-relaxed text-white/80">{{ item().overview }}</p>
          }
        </div>

        <div class="absolute left-2 top-2 flex flex-col items-start gap-1.5">
          @if (item().rating) {
            <span class="badge bg-black/60 text-white backdrop-blur-md">
              <app-icon name="star" filled class="size-3 text-accent" />
              {{ item().rating | number: '1.1-1' }}
            </span>
          }
          @if (showType()) {
            <span class="badge bg-black/60 text-[0.65rem] tracking-wider text-white/90 uppercase backdrop-blur-md">
              {{ item().mediaType === 'MOVIE' ? 'Movie' : 'Series' }}
            </span>
          }
        </div>
      </div>
      <p class="mt-3 line-clamp-1 text-sm font-semibold transition-colors group-hover:text-primary">{{ item().title }}</p>
      <p class="mt-0.5 text-xs text-muted">
        {{ item().year ?? 'TBA' }}
        <span class="text-subtle">·</span>
        {{ item().mediaType === 'MOVIE' ? 'Movie' : 'Series' }}
      </p>
    </a>

    <!-- Sibling of the link (a button must not be nested in an <a>); follows the poster's hover lift. -->
    <app-watchlist-button
      class="absolute right-2 top-2 transition duration-300 group-hover/card:-translate-y-1.5 group-hover/card:opacity-100 focus-within:opacity-100 [@media(hover:none)]:opacity-100"
      [class.opacity-0]="!saved()"
      [mediaType]="item().mediaType"
      [tmdbId]="item().id"
      [title]="item().title"
    />
  `,
})
export class MediaCard {
  readonly item = input.required<MediaSummary>();
  /** Show a Movie/Series badge on the poster (useful in mixed lists like search or trending). */
  readonly showType = input(false, { transform: booleanAttribute });

  private readonly watchlist = inject(WatchlistService);
  protected readonly saved = computed(() => this.watchlist.isSaved(this.item().mediaType, this.item().id));

  protected readonly link = computed(() => [
    this.item().mediaType === 'MOVIE' ? '/movies' : '/series',
    this.item().id,
  ]);
}

/** Placeholder with the same footprint as a MediaCard. */
@Component({
  selector: 'app-media-card-skeleton',
  template: `
    <div aria-hidden="true">
      <div class="skeleton aspect-2/3 rounded-card"></div>
      <div class="skeleton mt-3 h-3.5 w-3/4 rounded-full"></div>
      <div class="skeleton mt-2 h-3 w-1/3 rounded-full"></div>
    </div>
  `,
})
export class MediaCardSkeleton {}
