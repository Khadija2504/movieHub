import { Component, computed, input } from '@angular/core';

import { AssetUrlPipe } from '../../pipes/asset-url.pipe';

/** Round user picture, or gradient initials when there is none. Size comes from the host class (e.g. size-10). */
@Component({
  selector: 'app-avatar',
  imports: [AssetUrlPipe],
  // Size comes from the consumer's class (size-8, size-24, ...): no default size here, it would override it.
  host: { class: 'inline-block shrink-0 overflow-hidden rounded-full [container-type:size]' },
  template: `
    @if (url()) {
      <img [src]="url() | assetUrl" [alt]="name()" class="h-full w-full object-cover" />
    } @else {
      <span class="grid h-full w-full place-items-center bg-brand font-display font-bold text-white [font-size:clamp(0.6rem,38cqw,2.5rem)]">
        {{ initials() }}
      </span>
    }
  `,
})
export class Avatar {
  readonly name = input.required<string>();
  readonly url = input<string | null | undefined>(null);

  protected readonly initials = computed(() =>
    this.name()
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase())
      .join(''),
  );
}
