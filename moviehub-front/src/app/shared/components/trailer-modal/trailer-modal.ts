import { Component, computed, inject, input, output } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

/** YouTube video ids are 11 chars of [A-Za-z0-9_-]; anything else is never embedded. */
const YOUTUBE_KEY = /^[A-Za-z0-9_-]{6,20}$/;

/** Full-screen YouTube player. Closes on backdrop click or Escape. */
@Component({
  selector: 'app-trailer-modal',
  host: { '(document:keydown.escape)': 'closed.emit()' },
  template: `
    <div
      class="fixed inset-0 z-50 grid place-items-center bg-black/85 p-4"
      role="dialog"
      aria-modal="true"
      [attr.aria-label]="title()"
      (click)="closed.emit()"
    >
      <div class="w-full max-w-5xl" (click)="$event.stopPropagation()">
        <div class="mb-2 flex items-center justify-between gap-4">
          <p class="truncate text-sm text-muted">{{ title() }}</p>
          <button type="button" class="btn btn-secondary" (click)="closed.emit()">Close ✕</button>
        </div>
        @if (src(); as url) {
          <div class="aspect-video overflow-hidden rounded-xl bg-black">
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
