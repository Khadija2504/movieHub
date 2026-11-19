import { Component, booleanAttribute, input, output } from '@angular/core';

/** Error message with an optional "Try again" button. */
@Component({
  selector: 'app-error-state',
  template: `
    <div
      class="card flex flex-col items-center gap-3 text-center"
      [class.p-10]="!compact()"
      [class.p-5]="compact()"
      role="alert"
    >
      @if (!compact()) {
        <p class="font-display text-4xl" aria-hidden="true">🎞️</p>
      }
      <p class="text-sm text-muted">{{ message() }}</p>
      <button type="button" class="btn btn-secondary" (click)="retry.emit()">Try again</button>
    </div>
  `,
})
export class ErrorState {
  readonly message = input.required<string>();
  readonly compact = input(false, { transform: booleanAttribute });
  readonly retry = output<void>();
}
