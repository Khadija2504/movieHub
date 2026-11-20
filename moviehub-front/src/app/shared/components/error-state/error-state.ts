import { Component, booleanAttribute, input, output } from '@angular/core';

import { Icon } from '../icon/icon';

/** Error message with a "Try again" button. */
@Component({
  selector: 'app-error-state',
  imports: [Icon],
  template: `
    <div
      class="card flex items-center gap-4 text-left"
      [class]="compact() ? 'flex-row p-4' : 'flex-col p-10 text-center sm:p-14'"
      role="alert"
    >
      <span
        class="grid shrink-0 place-items-center rounded-2xl bg-danger/10 text-danger ring-1 ring-danger/20"
        [class]="compact() ? 'size-10' : 'size-14'"
      >
        <app-icon name="alert" [class]="compact() ? 'size-5' : 'size-6'" />
      </span>
      <div class="min-w-0 flex-1" [class.text-center]="!compact()">
        @if (!compact()) {
          <p class="font-display text-lg font-semibold">Something went wrong</p>
        }
        <p class="text-sm text-muted" [class.mt-1]="!compact()">{{ message() }}</p>
      </div>
      <button type="button" class="btn btn-secondary" [class.btn-sm]="compact()" (click)="retry.emit()">
        <app-icon name="refresh" class="size-4" /> Try again
      </button>
    </div>
  `,
})
export class ErrorState {
  readonly message = input.required<string>();
  readonly compact = input(false, { transform: booleanAttribute });
  readonly retry = output<void>();
}
