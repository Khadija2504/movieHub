import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-logo',
  imports: [RouterLink],
  template: `
    <a routerLink="/" class="flex items-center gap-2 font-display text-xl font-bold tracking-tight">
      <span
        class="grid h-8 w-8 place-items-center rounded-lg bg-primary text-sm text-primary-foreground"
        aria-hidden="true"
        >▶</span
      >
      <span>Movie<span class="text-primary">Hub</span></span>
    </a>
  `,
})
export class Logo {}
