import { DecimalPipe } from '@angular/common';
import { Component, booleanAttribute, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { MediaSummary } from '../../../core/models/catalog';

/** Poster card linking to the movie / series details page. */
@Component({
  selector: 'app-media-card',
  imports: [RouterLink, DecimalPipe],
  template: `
    <a [routerLink]="link()" class="group block focus-visible:outline-none" [attr.aria-label]="item().title">
      <div
        class="relative aspect-2/3 overflow-hidden rounded-card border border-border bg-surface-raised transition group-hover:border-muted group-focus-visible:ring-2 group-focus-visible:ring-primary"
      >
        @if (item().posterUrl) {
          <img
            [src]="item().posterUrl"
            [alt]="''"
            loading="lazy"
            class="h-full w-full object-cover transition duration-300 group-hover:scale-105"
          />
        } @else {
          <div class="grid h-full place-items-center p-3 text-center text-sm text-muted">{{ item().title }}</div>
        }
        @if (item().rating) {
          <span class="badge absolute left-2 top-2 bg-black/75 text-accent backdrop-blur">
            ★ {{ item().rating | number: '1.1-1' }}
          </span>
        }
        @if (showType()) {
          <span class="badge absolute right-2 top-2 bg-black/75 text-foreground backdrop-blur">
            {{ item().mediaType === 'MOVIE' ? 'Movie' : 'Series' }}
          </span>
        }
      </div>
      <p class="mt-2 line-clamp-1 text-sm font-medium group-hover:text-primary">{{ item().title }}</p>
      <p class="text-xs text-muted">{{ item().year ?? '—' }}</p>
    </a>
  `,
})
export class MediaCard {
  readonly item = input.required<MediaSummary>();
  /** Show a Movie/Series badge (useful in mixed lists like search or trending). */
  readonly showType = input(false, { transform: booleanAttribute });

  protected readonly link = computed(() => [
    this.item().mediaType === 'MOVIE' ? '/movies' : '/series',
    this.item().id,
  ]);
}

/** Placeholder with the same footprint as a MediaCard. */
@Component({
  selector: 'app-media-card-skeleton',
  template: `
    <div class="animate-pulse" aria-hidden="true">
      <div class="aspect-2/3 rounded-card bg-surface-raised"></div>
      <div class="mt-2 h-3.5 w-3/4 rounded bg-surface-raised"></div>
      <div class="mt-1.5 h-3 w-1/4 rounded bg-surface-raised"></div>
    </div>
  `,
})
export class MediaCardSkeleton {}
