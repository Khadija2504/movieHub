import { Component, input } from '@angular/core';

import { Icon, IconName } from '../../../shared/components/icon/icon';

/** Title row used by the account pages. Projected content goes on the right. */
@Component({
  selector: 'app-page-header',
  imports: [Icon],
  template: `
    <div class="mb-8 flex flex-wrap items-end justify-between gap-4">
      <div class="flex items-center gap-4">
        <span class="grid size-12 place-items-center rounded-2xl bg-primary/12 text-primary ring-1 ring-primary/20">
          <app-icon [name]="icon()" class="size-5" />
        </span>
        <div>
          <h1 class="text-2xl font-extrabold sm:text-3xl">{{ heading() }}</h1>
          @if (subheading()) {
            <p class="mt-0.5 text-sm text-muted">{{ subheading() }}</p>
          }
        </div>
      </div>
      <ng-content />
    </div>
  `,
})
export class PageHeader {
  readonly heading = input.required<string>();
  readonly subheading = input<string | null>(null);
  readonly icon = input.required<IconName>();
}
