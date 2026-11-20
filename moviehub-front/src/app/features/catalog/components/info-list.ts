import { Component, computed, input } from '@angular/core';

import { Icon } from '../../../shared/components/icon/icon';

export interface InfoItem {
  label: string;
  value: string | null | undefined;
}

/** "Details" section: a grid of label/value tiles. Empty values are skipped. */
@Component({
  selector: 'app-info-list',
  imports: [Icon],
  template: `
    @if (visible().length) {
      <section class="py-8">
        <div class="mb-5 flex items-center gap-3">
          <span class="grid size-9 place-items-center rounded-xl bg-primary/12 text-primary ring-1 ring-primary/20">
            <app-icon name="info" class="size-[1.1rem]" />
          </span>
          <h2 class="section-title">{{ heading() }}</h2>
        </div>
        <dl class="grid grid-cols-2 gap-3 sm:grid-cols-3">
          @for (item of visible(); track item.label) {
            <div class="card px-5 py-4">
              <dt class="text-xs font-medium tracking-wide text-subtle uppercase">{{ item.label }}</dt>
              <dd class="mt-1.5 text-sm font-semibold">{{ item.value }}</dd>
            </div>
          }
        </dl>
      </section>
    }
  `,
})
export class InfoList {
  readonly heading = input('Details');
  readonly items = input.required<InfoItem[]>();

  protected readonly visible = computed(() => this.items().filter((item) => !!item.value));
}
