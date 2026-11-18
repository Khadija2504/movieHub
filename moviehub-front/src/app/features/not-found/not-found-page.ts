import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink],
  template: `
    <section class="container-page grid place-items-center py-28 text-center">
      <p class="font-display text-7xl font-extrabold text-primary">404</p>
      <h1 class="mt-4 text-2xl font-bold">This scene was cut</h1>
      <p class="mt-2 text-muted">The page you are looking for does not exist (yet).</p>
      <a routerLink="/" class="btn btn-primary mt-8">Back to home</a>
    </section>
  `,
})
export class NotFoundPage {}
