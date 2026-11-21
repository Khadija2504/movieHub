import { Component, inject } from '@angular/core';

import { ToastService } from '../../../core/ui/toast.service';
import { Icon } from '../icon/icon';

/** Renders ToastService notifications. Placed once in the root component. */
@Component({
  selector: 'app-toast-host',
  imports: [Icon],
  template: `
    <div class="pointer-events-none fixed inset-x-0 bottom-6 z-[70] flex flex-col items-center gap-2 px-4" aria-live="polite">
      @for (toast of toasts.toasts(); track toast.id) {
        <div class="glass pointer-events-auto flex w-full max-w-sm items-center gap-3 rounded-2xl px-4 py-3 shadow-2xl animate-fade-up" role="status">
          <span
            class="grid size-8 shrink-0 place-items-center rounded-full"
            [class]="toast.kind === 'success' ? 'bg-success/15 text-success' : toast.kind === 'error' ? 'bg-danger/15 text-danger' : 'bg-white/10 text-foreground'"
          >
            <app-icon [name]="toast.kind === 'success' ? 'check' : toast.kind === 'error' ? 'alert' : 'info'" class="size-4" />
          </span>
          <p class="flex-1 text-sm">{{ toast.message }}</p>
          <button type="button" class="text-muted transition hover:text-foreground" (click)="toasts.dismiss(toast.id)" aria-label="Dismiss">
            <app-icon name="x" class="size-4" />
          </button>
        </div>
      }
    </div>
  `,
})
export class ToastHost {
  protected readonly toasts = inject(ToastService);
}
