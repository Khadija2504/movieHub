import { Component, computed, input, output } from '@angular/core';

import { dayParts, relativeDay, todayIn } from '../cinema-format';

/** Horizontal list of the days that have showtimes. */
@Component({
  selector: 'app-date-strip',
  template: `
    <div class="scrollbar-none -mx-4 flex gap-2 overflow-x-auto px-4 py-1 sm:mx-0 sm:px-0" role="radiogroup" aria-label="Choose a day">
      @for (day of days(); track day.date) {
        <button
          type="button"
          role="radio"
          [attr.aria-checked]="day.date === selected()"
          class="flex w-[4.75rem] shrink-0 flex-col items-center rounded-2xl px-2 py-2.5 ring-1 transition"
          [class]="day.date === selected() ? 'bg-brand text-white shadow-glow ring-transparent' : 'bg-white/[0.03] text-muted ring-border hover:bg-white/[0.07] hover:text-foreground'"
          (click)="select.emit(day.date)"
        >
          <span class="text-[0.7rem] font-semibold tracking-wide uppercase">{{ day.label }}</span>
          <span class="font-display text-xl leading-tight font-bold" [class.text-foreground]="day.date !== selected()">{{ day.day }}</span>
          <span class="text-[0.7rem]">{{ day.month }}</span>
        </button>
      }
    </div>
  `,
})
export class DateStrip {
  /** Available days, yyyy-MM-dd, sorted. */
  readonly dates = input.required<string[]>();
  readonly selected = input<string | null>(null);
  /** Zone used to decide what "Today" is (defaults to the browser's). */
  readonly timeZone = input<string | undefined>(undefined);
  readonly select = output<string>();

  protected readonly days = computed(() => {
    const today = todayIn(this.timeZone());
    return this.dates().map((date) => ({ date, label: relativeDay(date, today), ...dayParts(date) }));
  });
}
