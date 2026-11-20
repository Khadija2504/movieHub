import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Icon } from '../../shared/components/icon/icon';

@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink, Icon],
  template: `
    <section class="container-page relative grid min-h-[80dvh] place-items-center overflow-hidden page-top pb-16 text-center">
      <div class="pointer-events-none absolute left-1/2 top-1/3 size-[30rem] -translate-x-1/2 rounded-full bg-primary/20 blur-[140px]" aria-hidden="true"></div>
      <div class="relative animate-fade-up">
        <p class="text-gradient font-display text-[8rem] font-extrabold leading-none sm:text-[11rem]">404</p>
        <h1 class="mt-2 text-3xl font-extrabold sm:text-4xl">This scene was cut</h1>
        <p class="mx-auto mt-3 max-w-md text-muted">The page you're looking for doesn't exist, was moved, or is still in production.</p>
        <div class="mt-10 flex flex-wrap justify-center gap-3">
          <a routerLink="/" class="btn btn-primary btn-lg"><app-icon name="home" class="size-4" /> Back to home</a>
          <a routerLink="/search" class="btn btn-secondary btn-lg"><app-icon name="search" class="size-4" /> Search titles</a>
        </div>
      </div>
    </section>
  `,
})
export class NotFoundPage {}
