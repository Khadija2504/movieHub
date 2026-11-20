import { Component, ElementRef, booleanAttribute, input, output, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';

import { MediaSummary } from '../../../core/models/catalog';
import { ErrorState } from '../error-state/error-state';
import { Icon, IconName } from '../icon/icon';
import { MediaCard, MediaCardSkeleton } from '../media-card/media-card';

/** A titled, horizontally scrollable row of media cards. */
@Component({
  selector: 'app-media-carousel',
  imports: [RouterLink, MediaCard, MediaCardSkeleton, ErrorState, Icon],
  template: `
    <section class="py-8" [attr.aria-label]="heading()">
      <div class="mb-5 flex items-end justify-between gap-4">
        <div class="flex items-center gap-3">
          @if (icon()) {
            <span class="grid size-9 place-items-center rounded-xl bg-primary/12 text-primary ring-1 ring-primary/20">
              <app-icon [name]="icon()!" class="size-[1.1rem]" />
            </span>
          }
          <div>
            <h2 class="section-title">{{ heading() }}</h2>
            @if (subheading()) {
              <p class="mt-0.5 text-sm text-muted">{{ subheading() }}</p>
            }
          </div>
        </div>
        <div class="flex items-center gap-2">
          @if (viewAllLink()) {
            <a [routerLink]="viewAllLink()" [queryParams]="viewAllParams()" class="group inline-flex items-center gap-1 text-sm font-medium text-muted transition-colors hover:text-foreground">
              See all
              <app-icon name="arrow-right" class="size-4 transition-transform group-hover:translate-x-0.5" />
            </a>
          }
          <div class="ml-2 hidden gap-2 sm:flex">
            <button type="button" class="btn btn-secondary btn-icon size-9!" (click)="scroll(-1)" aria-label="Scroll left">
              <app-icon name="chevron-left" class="size-4" />
            </button>
            <button type="button" class="btn btn-secondary btn-icon size-9!" (click)="scroll(1)" aria-label="Scroll right">
              <app-icon name="chevron-right" class="size-4" />
            </button>
          </div>
        </div>
      </div>

      @if (error()) {
        <app-error-state [message]="error()!" (retry)="retry.emit()" compact />
      } @else {
        <div
          #track
          class="scrollbar-none -mx-4 flex snap-x snap-mandatory gap-4 overflow-x-auto scroll-smooth scroll-px-4 px-4 pt-2 pb-4 sm:-mx-6 sm:scroll-px-6 sm:px-6 lg:-mx-10 lg:gap-5 lg:scroll-px-10 lg:px-10"
        >
          @if (loading()) {
            @for (i of placeholders; track i) {
              <div class="w-[9.5rem] shrink-0 snap-start sm:w-44 lg:w-48"><app-media-card-skeleton /></div>
            }
          } @else {
            @for (item of items(); track item.mediaType + item.id) {
              <div class="w-[9.5rem] shrink-0 snap-start sm:w-44 lg:w-48">
                <app-media-card [item]="item" [showType]="showType()" />
              </div>
            } @empty {
              <p class="py-10 text-sm text-muted">Nothing to show yet.</p>
            }
          }
        </div>
      }
    </section>
  `,
})
export class MediaCarousel {
  readonly heading = input.required<string>();
  readonly subheading = input<string | null>(null);
  readonly icon = input<IconName | null>(null);
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
    el?.scrollBy({ left: direction * el.clientWidth * 0.8, behavior: 'smooth' });
  }
}
