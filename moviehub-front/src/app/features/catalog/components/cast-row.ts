import { Component, input } from '@angular/core';

import { Person } from '../../../core/models/catalog';
import { Icon } from '../../../shared/components/icon/icon';

/** Horizontally scrollable list of people with round portraits. */
@Component({
  selector: 'app-cast-row',
  imports: [Icon],
  template: `
    @if (people().length) {
      <section class="py-8">
        <div class="mb-5 flex items-center gap-3">
          <span class="grid size-9 place-items-center rounded-xl bg-primary/12 text-primary ring-1 ring-primary/20">
            <app-icon name="users" class="size-[1.1rem]" />
          </span>
          <h2 class="section-title">{{ heading() }}</h2>
        </div>
        <ul class="scrollbar-none -mx-4 flex gap-5 overflow-x-auto px-4 pb-2 sm:mx-0 sm:px-0">
          @for (person of people(); track person.id + (person.role ?? '')) {
            <li class="w-28 shrink-0 text-center">
              <div class="mx-auto size-24 overflow-hidden rounded-full bg-surface-raised p-0.5 ring-2 ring-border transition hover:ring-primary/60">
                @if (person.profileUrl) {
                  <img [src]="person.profileUrl" [alt]="person.name" loading="lazy" class="h-full w-full rounded-full object-cover object-top" />
                } @else {
                  <div class="grid h-full place-items-center rounded-full bg-linear-to-br from-surface-hover to-surface font-display text-xl font-semibold text-muted" aria-hidden="true">
                    {{ initials(person.name) }}
                  </div>
                }
              </div>
              <p class="mt-3 line-clamp-1 text-sm font-semibold">{{ person.name }}</p>
              @if (person.role) {
                <p class="mt-0.5 line-clamp-2 text-xs text-muted">{{ person.role }}</p>
              }
            </li>
          }
        </ul>
      </section>
    }
  `,
})
export class CastRow {
  readonly heading = input('Top cast');
  readonly people = input<Person[]>([]);

  protected initials(name: string): string {
    return name
      .split(' ')
      .map((part) => part.charAt(0))
      .slice(0, 2)
      .join('');
  }
}
