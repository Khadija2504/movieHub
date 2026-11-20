import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/** MovieHub wordmark: gradient play-mark + name. */
@Component({
  selector: 'app-logo',
  imports: [RouterLink],
  template: `
    <a routerLink="/" class="group flex items-center gap-2.5" aria-label="MovieHub home">
      <span
        class="relative grid size-9 place-items-center rounded-xl bg-brand shadow-glow transition-transform duration-300 group-hover:rotate-[-6deg] group-hover:scale-105"
        aria-hidden="true"
      >
        <svg viewBox="0 0 24 24" class="ml-0.5 size-4 fill-white"><path d="M7 4.5v15a1 1 0 0 0 1.5.86l12.5-7.5a1 1 0 0 0 0-1.72L8.5 3.64A1 1 0 0 0 7 4.5Z" /></svg>
      </span>
      @if (showName()) {
        <span class="font-display text-xl font-bold tracking-tight">
          Movie<span class="text-gradient">Hub</span>
        </span>
      }
    </a>
  `,
})
export class Logo {
  readonly showName = input(true);
}
