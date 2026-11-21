import { Component, booleanAttribute, computed, input, model, signal } from '@angular/core';

import { Icon } from '../icon/icon';

const LABELS = ['', 'Awful', 'Very bad', 'Bad', 'Poor', 'Average', 'Fair', 'Good', 'Very good', 'Excellent', 'Masterpiece'];

/**
 * 1-10 rating shown as 10 stars. Interactive by default (two-way [(value)]), or [readonly].
 * Keyboard: arrow keys change the value when focused.
 */
@Component({
  selector: 'app-star-rating',
  imports: [Icon],
  template: `
    <div
      class="inline-flex items-center gap-0.5"
      [attr.role]="readonly() ? 'img' : 'slider'"
      [attr.aria-label]="readonly() ? value() + ' out of 10' : 'Rating'"
      [attr.aria-valuemin]="readonly() ? null : 1"
      [attr.aria-valuemax]="readonly() ? null : 10"
      [attr.aria-valuenow]="readonly() ? null : value()"
      [attr.aria-valuetext]="readonly() ? null : value() + ' out of 10, ' + label()"
      [attr.tabindex]="readonly() ? null : 0"
      (keydown)="onKey($event)"
      (mouseleave)="hover.set(0)"
      [class.outline-none]="!readonly()"
    >
      @for (star of stars; track star) {
        <button
          type="button"
          tabindex="-1"
          class="transition-transform"
          [class]="(readonly() ? 'cursor-default ' : 'hover:scale-125 ') + (star <= shown() ? 'text-accent' : 'text-white/15')"
          [disabled]="readonly()"
          (mouseenter)="!readonly() && hover.set(star)"
          (click)="select(star)"
          [attr.aria-hidden]="true"
        >
          <app-icon name="star" filled [class]="sizeClass()" [strokeWidth]="0" />
        </button>
      }
      @if (showLabel() && !readonly()) {
        <span class="ml-3 min-w-24 text-sm font-medium" [class.text-muted]="!shown()">
          {{ shown() ? shown() + '/10 · ' + label() : 'Tap to rate' }}
        </span>
      }
    </div>
  `,
})
export class StarRating {
  readonly value = model(0);
  readonly readonly = input(false, { transform: booleanAttribute });
  readonly size = input<'sm' | 'md' | 'lg'>('md');
  readonly showLabel = input(true);

  protected readonly stars = Array.from({ length: 10 }, (_, i) => i + 1);
  protected readonly hover = signal(0);
  protected readonly shown = computed(() => this.hover() || this.value());
  protected readonly label = computed(() => LABELS[this.shown()] ?? '');
  protected readonly sizeClass = computed(() => ({ sm: 'size-3.5', md: 'size-5', lg: 'size-7' })[this.size()]);

  protected select(star: number): void {
    if (!this.readonly()) this.value.set(star);
  }

  protected onKey(event: KeyboardEvent): void {
    if (this.readonly()) return;
    const delta = { ArrowRight: 1, ArrowUp: 1, ArrowLeft: -1, ArrowDown: -1 }[event.key];
    if (delta) {
      event.preventDefault();
      this.value.set(Math.min(10, Math.max(1, (this.value() || 0) + delta)));
    }
  }
}
