import { Component, ElementRef, booleanAttribute, input, output, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';

import { MediaSummary } from '../../../core/models/catalog';
import { ErrorState } from '../error-state/error-state';
import { MediaCard, MediaCardSkeleton } from '../media-card/media-card';

/** A titled, horizontally scrollable row of media cards. */
@Component({
  selector: 'app-media-carousel',
  imports: [RouterLink, MediaCard, MediaCardSkeleton, ErrorState],
  template: `
    <section class="py-6" [attr.aria-label]="heading()">
      <div class="mb-3 flex items-end justify-between gap-4">
        <h2 class="text-xl font-bold sm:text-2xl">{{ heading() }}</h2>
        <div class="flex items-center gap-1">
          @if (viewAllLink()) {
            <a [routerLink]="viewAllLink()" [queryParams]="viewAllParams()" class="btn btn-ghost text-sm">View all</a>
          }
          <button type="button" class="btn btn-ghost hidden px-2 sm:inline-flex" (click)="scroll(-1)" aria-label="Scroll left">‹</button>
          <button type="button" class="btn btn-ghost hidden px-2 sm:inline-flex" (click)="scroll(1)" aria-label="Scroll right">›</button>
        </div>
      </div>

      @if (error()) {
        <app-error-state [message]="error()!" (retry)="retry.emit()" compact />
      } @else {
        <div
          #track
          class="-mx-4 flex snap-x snap-mandatory gap-4 overflow-x-auto scroll-smooth px-4 pb-2 [scrollbar-width:none] sm:mx-0 sm:px-0"
        >
          @if (loading()) {
            @for (i of placeholders; track i) {
              <div class="w-36 shrink-0 snap-start sm:w-44"><app-media-card-skeleton /></div>
            }
          } @else {
            @for (item of items(); track item.mediaType + item.id) {
              <div class="w-36 shrink-0 snap-start sm:w-44">
                <app-media-card [item]="item" [showType]="showType()" />
              </div>
            } @empty {
              <p class="py-8 text-sm text-muted">Nothing to show yet.</p>
            }
          }
        </div>
      }
    </section>
  `,
})
export class MediaCarousel {
  readonly heading = input.required<string>();
  readonly items = input<MediaSummary[]>([]);
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly showType = input(false, { transform: booleanAttribute });
  readonly viewAllLink = input<string | null>(null);
  readonly viewAllParams = input<Record<string, string>>({});
  readonly retry = output<void>();

  protected readonly placeholders = Array.from({ length: 8 }, (_, i) => i);
  private readonly track = viewChild<ElementRef<HTMLElement>>('track');

  protected scroll(direction: 1 | -1): void {
    const el = this.track()?.nativeElement;
    el?.scrollBy({ left: direction * el.clientWidth * 0.85, behavior: 'smooth' });
  }
}
