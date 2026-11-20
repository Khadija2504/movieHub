import { Component, computed, inject, input, output } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

import { Icon } from '../icon/icon';

/** YouTube video ids are 11 chars of [A-Za-z0-9_-]; anything else is never embedded. */
const YOUTUBE_KEY = /^[A-Za-z0-9_-]{6,20}$/;

/** Full-screen YouTube player. Closes on backdrop click or Escape. */
@Component({
  selector: 'app-trailer-modal',
  imports: [Icon],
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `
    <div
      class="fixed inset-0 z-[60] grid place-items-center bg-black/80 p-4 backdrop-blur-md animate-fade-in sm:p-8"
      role="dialog"
      aria-modal="true"
      [attr.aria-label]="title()"
      (click)="closed.emit()"
    >
      <div class="w-full max-w-6xl animate-scale-in" (click)="$event.stopPropagation()">
        <div class="mb-3 flex items-center justify-between gap-4">
          <p class="truncate font-display font-semibold">{{ title() }}</p>
          <button type="button" class="btn btn-secondary btn-icon" (click)="closed.emit()" aria-label="Close trailer">
            <app-icon name="x" class="size-5" />
          </button>
        </div>
        @if (src(); as url) {
          <div class="aspect-video overflow-hidden rounded-2xl bg-black shadow-2xl ring-1 ring-white/10">
            <iframe
              class="h-full w-full"
              [src]="url"
              [title]="title()"
              allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
              allowfullscreen
            ></iframe>
          </div>
        }
      </div>
    </div>
  `,
})
export class TrailerModal {
  readonly videoKey = input.required<string>();
  readonly title = input('Trailer');
  readonly closed = output<void>();

  private readonly sanitizer = inject(DomSanitizer);

  protected readonly src = computed<SafeResourceUrl | null>(() => {
    const key = this.videoKey();
    return YOUTUBE_KEY.test(key)
      ? this.sanitizer.bypassSecurityTrustResourceUrl(
          `https://www.youtube-nocookie.com/embed/${key}?autoplay=1&rel=0`,
        )
      : null;
  });
}
