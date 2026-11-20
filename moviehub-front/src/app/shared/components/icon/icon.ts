import { Component, booleanAttribute, computed, input } from '@angular/core';

type Shape =
  | { p: string } // path d
  | { c: [number, number, number] } // circle cx, cy, r
  | { r: [number, number, number, number, number?] } // rect x, y, w, h, rx
  | { pg: string }; // polygon points

/** 24x24 stroke icons (Lucide-style geometry). */
const ICONS = {
  search: [{ c: [11, 11, 8] }, { p: 'm21 21-4.3-4.3' }],
  home: [{ p: 'm3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z' }, { p: 'M9 22V12h6v10' }],
  film: [{ r: [2, 2, 20, 20, 2.5] }, { p: 'M7 2v20M17 2v20M2 12h20M2 7h5M2 17h5M17 17h5M17 7h5' }],
  tv: [{ r: [2, 7, 20, 15, 2] }, { p: 'm17 2-5 5-5-5' }],
  ticket: [
    { p: 'M2 9a3 3 0 0 1 0 6v2a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-2a3 3 0 0 1 0-6V7a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2Z' },
    { p: 'M13 5v2M13 17v2M13 11v2' },
  ],
  clapperboard: [
    { p: 'M20.2 6 3 11l-.9-2.4c-.3-1.1.3-2.2 1.3-2.5l13.5-4c1.1-.3 2.2.3 2.5 1.3Z' },
    { p: 'm6.2 5.3 3.1 3.9M12.4 3.4l3.1 4M3 11h18v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z' },
  ],
  user: [{ c: [12, 8, 4] }, { p: 'M20 21a8 8 0 0 0-16 0' }],
  users: [
    { p: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2' },
    { c: [9, 7, 4] },
    { p: 'M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75' },
  ],
  bookmark: [{ p: 'm19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z' }],
  library: [{ p: 'm16 6 4 14M12 6v14M8 8v12M4 4v16' }],
  history: [{ p: 'M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8' }, { p: 'M3 3v5h5M12 7v5l4 2' }],
  star: [{ pg: '12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2' }],
  message: [{ p: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z' }],
  card: [{ r: [2, 5, 20, 14, 2] }, { p: 'M2 10h20' }],
  wallet: [{ p: 'M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6' }],
  crown: [{ p: 'm2 4 3 12h14l3-12-6 7-4-7-4 7-6-7zM5 20h14' }],
  settings: [{ p: 'M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6' }],
  logout: [{ p: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4' }, { p: 'm16 17 5-5-5-5M21 12H9' }],
  play: [{ pg: '6 3 20 12 6 21 6 3' }],
  plus: [{ p: 'M5 12h14M12 5v14' }],
  check: [{ p: 'M20 6 9 17l-5-5' }],
  x: [{ p: 'M18 6 6 18M6 6l12 12' }],
  menu: [{ p: 'M4 6h16M4 12h16M4 18h16' }],
  'chevron-left': [{ p: 'm15 18-6-6 6-6' }],
  'chevron-right': [{ p: 'm9 18 6-6-6-6' }],
  'chevron-down': [{ p: 'm6 9 6 6 6-6' }],
  'arrow-right': [{ p: 'M5 12h14M12 5l7 7-7 7' }],
  calendar: [{ r: [3, 4, 18, 18, 2] }, { p: 'M16 2v4M8 2v4M3 10h18' }],
  clock: [{ c: [12, 12, 10] }, { p: 'M12 6v6l4 2' }],
  globe: [
    { c: [12, 12, 10] },
    { p: 'M2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z' },
  ],
  chart: [{ p: 'M3 3v18h18' }, { p: 'M18 17V9M13 17V5M8 17v-3' }],
  dashboard: [
    { r: [3, 3, 7, 9, 1] },
    { r: [14, 3, 7, 5, 1] },
    { r: [14, 12, 7, 9, 1] },
    { r: [3, 16, 7, 5, 1] },
  ],
  seat: [
    { p: 'M19 9V6a2 2 0 0 0-2-2H7a2 2 0 0 0-2 2v3' },
    { p: 'M3 16a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-5a2 2 0 0 0-4 0v1.5a.5.5 0 0 1-.5.5h-9a.5.5 0 0 1-.5-.5V11a2 2 0 0 0-4 0z' },
    { p: 'M5 18v2M19 18v2' },
  ],
  shield: [{ p: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10' }],
  info: [{ c: [12, 12, 10] }, { p: 'M12 16v-4M12 8h.01' }],
  alert: [
    { p: 'm21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z' },
    { p: 'M12 9v4M12 17h.01' },
  ],
  refresh: [
    { p: 'M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8' },
    { p: 'M21 3v5h-5M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16' },
    { p: 'M8 16H3v5' },
  ],
  mail: [{ r: [2, 4, 20, 16, 2] }, { p: 'm22 7-10 5L2 7' }],
  lock: [{ r: [3, 11, 18, 11, 2] }, { p: 'M7 11V7a5 5 0 0 1 10 0v4' }],
  eye: [{ p: 'M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z' }, { c: [12, 12, 3] }],
  'eye-off': [
    { p: 'M9.88 9.88a3 3 0 1 0 4.24 4.24M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68' },
    { p: 'M6.61 6.61A13.53 13.53 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61M2 2l20 20' },
  ],
  sparkles: [
    { p: 'm12 3-1.9 5.8a2 2 0 0 1-1.3 1.3L3 12l5.8 1.9a2 2 0 0 1 1.3 1.3L12 21l1.9-5.8a2 2 0 0 1 1.3-1.3L21 12l-5.8-1.9a2 2 0 0 1-1.3-1.3Z' },
  ],
  flame: [
    { p: 'M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.07-2.14-.22-4.05 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.15.43-2.29 1-3a2.5 2.5 0 0 0 2.5 2.5z' },
  ],
  trophy: [
    { p: 'M6 9H4.5a2.5 2.5 0 0 1 0-5H6M18 9h1.5a2.5 2.5 0 0 0 0-5H18M4 22h16' },
    { p: 'M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22' },
    { p: 'M18 2H6v7a6 6 0 0 0 12 0V2Z' },
  ],
  'map-pin': [{ p: 'M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z' }, { c: [12, 10, 3] }],
  qr: [
    { r: [3, 3, 7, 7, 1] },
    { r: [14, 3, 7, 7, 1] },
    { r: [3, 14, 7, 7, 1] },
    { p: 'M14 14h3v3h-3zM20 14v.01M14 20h.01M17 20h4v-3' },
  ],
  grid: [{ r: [3, 3, 7, 7, 1] }, { r: [14, 3, 7, 7, 1] }, { r: [14, 14, 7, 7, 1] }, { r: [3, 14, 7, 7, 1] }],
} satisfies Record<string, Shape[]>;

export type IconName = keyof typeof ICONS;

/** <app-icon name="film" class="size-5" />. Size and color come from the host's CSS (currentColor). */
@Component({
  selector: 'app-icon',
  host: { class: 'inline-block shrink-0', 'aria-hidden': 'true' },
  template: `
    <svg
      viewBox="0 0 24 24"
      class="block h-full w-full"
      [attr.fill]="filled() ? 'currentColor' : 'none'"
      stroke="currentColor"
      [attr.stroke-width]="strokeWidth()"
      stroke-linecap="round"
      stroke-linejoin="round"
    >
      @for (shape of shapes(); track $index) {
        @if (shape.p) {
          <path [attr.d]="shape.p" />
        } @else if (shape.c) {
          <circle [attr.cx]="shape.c[0]" [attr.cy]="shape.c[1]" [attr.r]="shape.c[2]" />
        } @else if (shape.r) {
          <rect
            [attr.x]="shape.r[0]"
            [attr.y]="shape.r[1]"
            [attr.width]="shape.r[2]"
            [attr.height]="shape.r[3]"
            [attr.rx]="shape.r[4] || 0"
          />
        } @else if (shape.pg) {
          <polygon [attr.points]="shape.pg" />
        }
      }
    </svg>
  `,
  styles: `
    :host {
      width: 1.25rem;
      height: 1.25rem;
    }
  `,
})
export class Icon {
  readonly name = input.required<IconName>();
  readonly filled = input(false, { transform: booleanAttribute });
  readonly strokeWidth = input(2);

  protected readonly shapes = computed(
    () => ICONS[this.name()] as { p?: string; c?: number[]; r?: number[]; pg?: string }[],
  );
}
