import { Component, computed, input, output } from '@angular/core';

import { SeatMap, SeatView } from '../../../core/models/cinema';

interface Cell {
  seat: SeatView;
  /** 1-based CSS grid position (column 1 and the last column hold the row labels). */
  row: number;
  column: number;
  selected: boolean;
  disabled: boolean;
  classes: string;
  title: string;
}

/**
 * Room seat map drawn on a CSS grid. Aisles are simply empty grid cells.
 * With `selectable`, available seats are buttons and clicks are reported through `toggle`.
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
        [style.grid-template-columns]="'repeat(' + (map().columns + 2) + ', minmax(1.15rem, 1.6rem))'"
        [attr.role]="selectable() ? 'group' : 'img'"
        [attr.aria-label]="ariaLabel()"
      >
        @for (label of rowLabels(); track label.row) {
          <span class="grid place-items-center text-[0.65rem] font-semibold text-subtle" [style.grid-row]="label.row" [style.grid-column]="1" aria-hidden="true">{{ label.text }}</span>
          <span class="grid place-items-center text-[0.65rem] font-semibold text-subtle" [style.grid-row]="label.row" [style.grid-column]="map().columns + 2" aria-hidden="true">{{ label.text }}</span>
        }
        @for (cell of cells(); track cell.seat.id) {
          @if (selectable()) {
            <button
              type="button"
              class="seat aspect-square rounded-t-[0.4rem] rounded-b-[0.2rem] transition"
              [class]="cell.classes"
              [style.grid-row]="cell.row"
              [style.grid-column]="cell.column"
              [title]="cell.title"
              [attr.aria-label]="cell.title"
              [attr.aria-pressed]="cell.selected"
              [disabled]="cell.disabled"
              (click)="toggle.emit(cell.seat)"
            >
              @if (cell.selected) {
                <svg viewBox="0 0 24 24" class="mx-auto size-3/5" fill="none" stroke="currentColor" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 6 9 17l-5-5" /></svg>
              }
            </button>
          } @else {
            <span
              class="aspect-square rounded-t-[0.4rem] rounded-b-[0.2rem]"
              [class]="cell.classes"
              [style.grid-row]="cell.row"
              [style.grid-column]="cell.column"
              [title]="cell.title"
            ></span>
          }
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
      @if (selectable()) {
        <span class="flex items-center gap-2"><span class="size-3 rounded-sm bg-primary"></span> Your seats</span>
      }
      <span class="flex items-center gap-2"><span class="size-3 rounded-sm bg-white/8 ring-1 ring-white/10"></span> Taken</span>
    </div>
  `,
  styles: `
    .seat:not(:disabled) { cursor: pointer; }
    .seat:not(:disabled):hover { transform: translateY(-2px); filter: brightness(1.25); }
    .seat:focus-visible { outline: 2px solid var(--color-foreground); outline-offset: 2px; }
  `,
})
export class SeatMapView {
  readonly map = input.required<SeatMap>();
  readonly selectable = input(false);
  /** Ids of the seats chosen by the customer. */
  readonly selected = input<readonly number[]>([]);
  /** When reached, other available seats can no longer be picked. */
  readonly maxSelection = input(Infinity);
  readonly toggle = output<SeatView>();

  protected readonly cells = computed<Cell[]>(() => {
    const chosen = new Set(this.selected());
    const full = chosen.size >= this.maxSelection();
    return this.map().seats.map((seat) => {
      const selected = chosen.has(seat.id);
      const taken = seat.status === 'TAKEN' && !selected;
      return {
        seat,
        row: seat.gridRow + 1,
        column: seat.gridColumn + 2,
        selected,
        disabled: taken || (full && !selected),
        classes: seatClasses(seat, selected, taken),
        title: `${seat.label}${TYPE_SUFFIX[seat.type]}${taken ? ' (taken)' : selected ? ' (selected)' : ''}`,
      };
    });
  });

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

const TYPE_SUFFIX = { STANDARD: '', VIP: ' · VIP', ACCESSIBLE: ' · Wheelchair space' } as const;

function seatClasses(seat: SeatView, selected: boolean, taken: boolean): string {
  if (selected) return 'bg-primary text-white shadow-[0_0_14px_rgb(255_59_92/0.6)]';
  if (taken) return 'bg-white/8 ring-1 ring-white/10';
  switch (seat.type) {
    case 'VIP':
      return 'bg-accent/70';
    case 'ACCESSIBLE':
      return 'bg-sky-400/80';
    default:
      return 'bg-white/25';
  }
}
