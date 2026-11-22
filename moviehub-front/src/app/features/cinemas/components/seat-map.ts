import { Component, computed, input } from '@angular/core';

import { SeatMap, SeatView } from '../../../core/models/cinema';

interface Cell {
  seat: SeatView;
  /** 1-based CSS grid position (column 1 and the last column hold the row labels). */
  row: number;
  column: number;
  classes: string;
  title: string;
}

/**
 * Room seat map drawn on a CSS grid. Aisles are simply empty grid cells.
 * Read-only for now: seat selection is added with online booking.
 */
@Component({
  selector: 'app-seat-map',
  host: { class: 'block' },
  template: `
    <!-- Screen -->
    <div class="mx-auto w-4/5 max-w-lg" aria-hidden="true">
      <div class="h-8 rounded-[50%_50%_0_0/100%_100%_0_0] border-t-4 border-primary/80 shadow-[0_-12px_40px_-8px_rgb(255_59_92/0.55)]"></div>
      <p class="-mt-4 text-center text-[0.65rem] tracking-[0.4em] text-subtle uppercase">Screen</p>
    </div>

    <div class="scrollbar-none mt-6 overflow-x-auto pb-2">
      <div
        class="mx-auto grid w-max gap-1 sm:gap-1.5"
        [style.grid-template-columns]="'repeat(' + (map().columns + 2) + ', minmax(1rem, 1.6rem))'"
        role="img"
        [attr.aria-label]="ariaLabel()"
      >
        @for (label of rowLabels(); track label.row) {
          <span class="grid place-items-center text-[0.65rem] font-semibold text-subtle" [style.grid-row]="label.row" [style.grid-column]="1">{{ label.text }}</span>
          <span class="grid place-items-center text-[0.65rem] font-semibold text-subtle" [style.grid-row]="label.row" [style.grid-column]="map().columns + 2">{{ label.text }}</span>
        }
        @for (cell of cells(); track cell.seat.id) {
          <span
            class="aspect-square rounded-t-[0.4rem] rounded-b-[0.2rem] transition-colors"
            [class]="cell.classes"
            [style.grid-row]="cell.row"
            [style.grid-column]="cell.column"
            [title]="cell.title"
          ></span>
        }
      </div>
    </div>

    <!-- Legend -->
    <div class="mt-6 flex flex-wrap justify-center gap-x-6 gap-y-2 text-xs text-muted">
      <span class="flex items-center gap-2"><span class="size-3 rounded-sm bg-white/25"></span> Standard</span>
      @if (hasVip()) {
        <span class="flex items-center gap-2"><span class="size-3 rounded-sm bg-accent/70"></span> VIP</span>
      }
      @if (hasAccessible()) {
        <span class="flex items-center gap-2"><span class="size-3 rounded-sm bg-sky-400/80"></span> Wheelchair space</span>
      }
      <span class="flex items-center gap-2"><span class="size-3 rounded-sm bg-white/8 ring-1 ring-white/10"></span> Taken</span>
    </div>
  `,
})
export class SeatMapView {
  readonly map = input.required<SeatMap>();

  protected readonly cells = computed<Cell[]>(() =>
    this.map().seats.map((seat) => ({
      seat,
      row: seat.gridRow + 1,
      column: seat.gridColumn + 2,
      classes: seatClasses(seat),
      title: `${seat.label}${seat.type === 'STANDARD' ? '' : seat.type === 'VIP' ? ' · VIP' : ' · Wheelchair space'}${seat.status === 'TAKEN' ? ' (taken)' : ''}`,
    })),
  );

  protected readonly rowLabels = computed(() => {
    const labels = new Map<number, string>();
    for (const seat of this.map().seats) labels.set(seat.gridRow + 1, seat.row);
    return [...labels].map(([row, text]) => ({ row, text }));
  });

  protected readonly hasVip = computed(() => this.map().seats.some((s) => s.type === 'VIP'));
  protected readonly hasAccessible = computed(() => this.map().seats.some((s) => s.type === 'ACCESSIBLE'));
  protected readonly ariaLabel = computed(
    () => `Seat map: ${this.map().available} of ${this.map().capacity} seats available`,
  );
}

function seatClasses(seat: SeatView): string {
  if (seat.status === 'TAKEN') return 'bg-white/8 ring-1 ring-white/10';
  switch (seat.type) {
    case 'VIP':
      return 'bg-accent/70';
    case 'ACCESSIBLE':
      return 'bg-sky-400/80';
    default:
      return 'bg-white/25';
  }
}
