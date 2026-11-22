import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Showtime } from '../../../core/models/cinema';
import { SCREEN_TYPE_LABELS, formatLabel, timeOf, versionLabel } from '../cinema-format';

/** A bookable showtime: time, then format / screen / version. Links to the screening page. */
@Component({
  selector: 'app-showtime-chip',
  imports: [RouterLink],
  host: { class: 'block' },
  template: `
    <a
      [routerLink]="['/screenings', showtime().id]"
      class="group flex min-w-[7.5rem] flex-col rounded-xl bg-white/[0.04] px-3.5 py-2.5 ring-1 ring-border transition hover:-translate-y-0.5 hover:bg-primary/10 hover:ring-primary/40 focus-visible:ring-2 focus-visible:ring-primary"
      [attr.aria-label]="ariaLabel()"
    >
      <span class="font-display text-lg leading-tight font-bold group-hover:text-primary">{{ time() }}</span>
      <span class="mt-0.5 text-[0.7rem] font-medium text-muted">
        {{ tags() }}
      </span>
      <span class="text-[0.7rem] text-subtle">{{ version() }}</span>
    </a>
  `,
})
export class ShowtimeChip {
  readonly showtime = input.required<Showtime>();

  protected readonly time = computed(() => timeOf(this.showtime().localStart));
  protected readonly version = computed(() => versionLabel(this.showtime()));
  protected readonly tags = computed(() => {
    const s = this.showtime();
    const screen = s.screenType === 'STANDARD' ? s.roomName : SCREEN_TYPE_LABELS[s.screenType];
    return `${screen} · ${formatLabel(s.format)}`;
  });
  protected readonly ariaLabel = computed(
    () => `${this.time()}, ${this.tags()}, ${this.version()}, ${this.showtime().seatsLeft} seats left`,
  );
}
