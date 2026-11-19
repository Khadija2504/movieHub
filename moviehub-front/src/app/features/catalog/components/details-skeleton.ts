import { Component } from '@angular/core';

@Component({
  selector: 'app-details-skeleton',
  template: `
    <div class="container-page flex animate-pulse flex-col gap-8 py-16 md:flex-row" aria-hidden="true">
      <div class="mx-auto aspect-2/3 w-48 rounded-card bg-surface-raised md:mx-0 md:w-64"></div>
      <div class="flex-1 space-y-4 pt-4">
        <div class="h-4 w-24 rounded bg-surface-raised"></div>
        <div class="h-12 w-3/4 rounded bg-surface-raised"></div>
        <div class="h-4 w-1/3 rounded bg-surface-raised"></div>
        <div class="h-24 w-full max-w-3xl rounded bg-surface-raised"></div>
        <div class="h-10 w-64 rounded bg-surface-raised"></div>
      </div>
    </div>
  `,
})
export class DetailsSkeleton {}
