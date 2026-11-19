import { Component, computed, input, output } from '@angular/core';

/** Previous / numbered pages / next. Emits the 1-based page to show. */
@Component({
  selector: 'app-pagination',
  template: `
    @if (totalPages() > 1) {
      <nav class="mt-10 flex flex-wrap items-center justify-center gap-1" aria-label="Pagination">
        <button type="button" class="btn btn-ghost" [disabled]="page() <= 1" (click)="go(page() - 1)">‹ Prev</button>
        @for (p of pages(); track $index) {
          @if (p === null) {
            <span class="px-2 text-muted">…</span>
          } @else {
            <button
              type="button"
              class="btn min-w-10"
              [class.btn-primary]="p === page()"
              [class.btn-ghost]="p !== page()"
              [attr.aria-current]="p === page() ? 'page' : null"
              (click)="go(p)"
            >
              {{ p }}
            </button>
          }
        }
        <button type="button" class="btn btn-ghost" [disabled]="page() >= totalPages()" (click)="go(page() + 1)">
          Next ›
        </button>
      </nav>
    }
  `,
})
export class Pagination {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly pageChange = output<number>();

  /** e.g. [1, null, 4, 5, 6, null, 20] (null = gap). */
  protected readonly pages = computed(() => pageWindow(this.page(), this.totalPages()));

  protected go(page: number): void {
    if (page >= 1 && page <= this.totalPages() && page !== this.page()) {
      this.pageChange.emit(page);
    }
  }
}

export function pageWindow(current: number, total: number, radius = 1): (number | null)[] {
  const pages = new Set<number>([1, total]);
  for (let p = current - radius; p <= current + radius; p++) {
    if (p >= 1 && p <= total) pages.add(p);
  }
  const sorted = [...pages].sort((a, b) => a - b);
  const result: (number | null)[] = [];
  sorted.forEach((p, i) => {
    if (i > 0 && p - sorted[i - 1] > 1) result.push(null);
    result.push(p);
  });
  return result;
}
