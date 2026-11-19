import { Component, input } from '@angular/core';

import { Person } from '../../../core/models/catalog';

/** Horizontally scrollable list of people with photos. */
@Component({
  selector: 'app-cast-row',
  template: `
    @if (people().length) {
      <section class="py-6">
        <h2 class="mb-4 text-xl font-bold sm:text-2xl">{{ heading() }}</h2>
        <ul class="-mx-4 flex gap-4 overflow-x-auto px-4 pb-2 [scrollbar-width:none] sm:mx-0 sm:px-0">
          @for (person of people(); track person.id + (person.role ?? '')) {
            <li class="w-28 shrink-0 sm:w-32">
              <div class="aspect-2/3 overflow-hidden rounded-xl border border-border bg-surface-raised">
                @if (person.profileUrl) {
                  <img [src]="person.profileUrl" [alt]="person.name" loading="lazy" class="h-full w-full object-cover" />
                } @else {
                  <div class="grid h-full place-items-center font-display text-2xl text-muted" aria-hidden="true">
                    {{ person.name.charAt(0) }}
                  </div>
                }
              </div>
              <p class="mt-2 line-clamp-1 text-sm font-medium">{{ person.name }}</p>
              @if (person.role) {
                <p class="line-clamp-2 text-xs text-muted">{{ person.role }}</p>
              }
            </li>
          }
        </ul>
      </section>
    }
  `,
})
export class CastRow {
  readonly heading = input('Cast');
  readonly people = input<Person[]>([]);
}
