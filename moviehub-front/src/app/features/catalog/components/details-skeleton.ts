import { Component } from '@angular/core';

@Component({
  selector: 'app-details-skeleton',
  template: `
    <div class="container-page grid gap-10 pt-32 pb-16 md:grid-cols-[16rem_1fr] md:items-end lg:min-h-[44rem] lg:grid-cols-[19rem_1fr]" aria-hidden="true">
      <div class="skeleton mx-auto aspect-2/3 w-52 rounded-card md:mx-0 md:w-full"></div>
      <div class="space-y-5">
        <div class="skeleton h-7 w-24 rounded-full"></div>
        <div class="skeleton h-14 w-3/4 rounded-2xl"></div>
        <div class="flex gap-2">
          <div class="skeleton h-9 w-24 rounded-full"></div>
          <div class="skeleton h-9 w-28 rounded-full"></div>
          <div class="skeleton h-9 w-20 rounded-full"></div>
        </div>
        <div class="skeleton h-24 w-full max-w-3xl rounded-2xl"></div>
        <div class="flex gap-3">
          <div class="skeleton h-12 w-40 rounded-full"></div>
          <div class="skeleton h-12 w-40 rounded-full"></div>
        </div>
      </div>
    </div>
  `,
})
export class DetailsSkeleton {}
