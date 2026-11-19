import { DecimalPipe } from '@angular/common';
import { Component, input, output } from '@angular/core';

import { Genre } from '../../../core/models/catalog';

/**
 * Top section of a details page: backdrop, poster, title, facts, genres and overview.
 * Projected content (<ng-content>) is rendered in the actions area.
 */
@Component({
  selector: 'app-title-hero',
  imports: [DecimalPipe],
  template: `
    <section class="relative isolate overflow-hidden border-b border-border">
      @if (backdropUrl()) {
        <img [src]="backdropUrl()" alt="" class="absolute inset-0 -z-10 h-full w-full object-cover opacity-30" />
      }
      <div class="absolute inset-0 -z-10 bg-gradient-to-t from-background via-background/80 to-background/40" aria-hidden="true"></div>

      <div class="container-page flex flex-col gap-8 py-10 md:flex-row md:items-end md:py-16">
        <div class="mx-auto w-48 shrink-0 md:mx-0 md:w-64">
          <div class="aspect-2/3 overflow-hidden rounded-card border border-border bg-surface-raised shadow-2xl">
            @if (posterUrl()) {
              <img [src]="posterUrl()" [alt]="title() + ' poster'" class="h-full w-full object-cover" />
            } @else {
              <div class="grid h-full place-items-center p-4 text-center text-muted">{{ title() }}</div>
            }
          </div>
        </div>

        <div class="min-w-0 flex-1">
          <p class="text-sm font-semibold uppercase tracking-widest text-accent">{{ kind() }}</p>
          <h1 class="mt-1 text-3xl font-extrabold sm:text-5xl">
            {{ title() }}
            @if (year()) {
              <span class="font-medium text-muted">({{ year() }})</span>
            }
          </h1>

          <div class="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted">
            @if (rating()) {
              <span class="font-semibold text-accent">★ {{ rating() | number: '1.1-1' }}</span>
              @if (voteCount()) {
                <span>({{ voteCount()!.toLocaleString() }} votes)</span>
              }
            }
            @for (fact of facts(); track fact) {
              <span aria-hidden="true">·</span>
              <span>{{ fact }}</span>
            }
          </div>

          @if (genres().length) {
            <ul class="mt-4 flex flex-wrap gap-2">
              @for (g of genres(); track g.id) {
                <li class="badge border border-border bg-surface/70">{{ g.name }}</li>
              }
            </ul>
          }

          @if (tagline()) {
            <p class="mt-5 italic text-muted">“{{ tagline() }}”</p>
          }
          @if (overview()) {
            <p class="mt-3 max-w-3xl leading-relaxed">{{ overview() }}</p>
          }

          <div class="mt-6 flex flex-wrap items-center gap-3">
            @if (hasTrailer()) {
              <button type="button" class="btn btn-secondary" (click)="playTrailer.emit()">▶ Play trailer</button>
            }
            <ng-content />
          </div>
        </div>
      </div>
    </section>
  `,
})
export class TitleHero {
  readonly kind = input.required<string>();
  readonly title = input.required<string>();
  readonly year = input<number | null>(null);
  readonly posterUrl = input<string | null>(null);
  readonly backdropUrl = input<string | null>(null);
  readonly rating = input<number | null>(null);
  readonly voteCount = input<number | null>(null);
  readonly facts = input<string[]>([]);
  readonly genres = input<Genre[]>([]);
  readonly tagline = input<string | null>(null);
  readonly overview = input<string | null>(null);
  readonly hasTrailer = input(false);
  readonly playTrailer = output<void>();
}
