import { DecimalPipe } from '@angular/common';
import { Component, input, output } from '@angular/core';

import { Genre } from '../../../core/models/catalog';
import { Icon, IconName } from '../../../shared/components/icon/icon';

export interface HeroFact {
  icon: IconName;
  label: string;
}

/**
 * Full-bleed top section of a details page: backdrop, poster, title, facts, genres and overview.
 * Projected content (<ng-content>) is rendered in the actions row.
 */
@Component({
  selector: 'app-title-hero',
  imports: [DecimalPipe, Icon],
  template: `
    <section class="relative isolate overflow-hidden">
      <!-- Backdrop -->
      <div class="absolute inset-0 -z-20">
        @if (backdropUrl()) {
          <img [src]="backdropUrl()" alt="" class="h-full w-full object-cover object-top animate-ken-burns" />
        }
      </div>
      <div class="absolute inset-0 -z-10 bg-linear-to-t from-background via-background/75 to-background/30" aria-hidden="true"></div>
      <div class="absolute inset-0 -z-10 bg-linear-to-r from-background/90 via-background/40 to-transparent" aria-hidden="true"></div>

      <div class="container-page grid gap-8 pt-28 pb-12 sm:pt-32 md:grid-cols-[16rem_1fr] md:items-end md:gap-12 lg:min-h-[44rem] lg:grid-cols-[19rem_1fr] lg:pb-16">
        <!-- Poster -->
        <div class="mx-auto w-52 animate-fade-up md:mx-0 md:w-full">
          <div class="relative">
            @if (posterUrl()) {
              <img [src]="posterUrl()" alt="" class="absolute inset-0 -z-10 h-full w-full scale-95 rounded-card object-cover opacity-70 blur-2xl" aria-hidden="true" />
            }
            <div class="aspect-2/3 overflow-hidden rounded-card bg-surface-raised shadow-2xl ring-1 ring-white/15">
              @if (posterUrl()) {
                <img [src]="posterUrl()" [alt]="title() + ' poster'" class="h-full w-full object-cover" />
              } @else {
                <div class="grid h-full place-items-center"><app-icon name="film" class="size-10 text-subtle" /></div>
              }
            </div>
          </div>
        </div>

        <!-- Info -->
        <div class="min-w-0 animate-fade-up [animation-delay:80ms]">
          <span class="badge glass text-white/90">{{ kind() }}</span>
          <h1 class="mt-4 text-4xl font-extrabold leading-[1.05] sm:text-5xl lg:text-6xl">{{ title() }}</h1>

          @if (tagline()) {
            <p class="mt-3 text-lg italic text-white/60">{{ tagline() }}</p>
          }

          <div class="mt-6 flex flex-wrap items-center gap-2.5">
            @if (rating()) {
              <span class="inline-flex h-9 items-center gap-2 rounded-full bg-accent/15 px-3.5 text-sm font-bold text-accent ring-1 ring-accent/30">
                <app-icon name="star" filled class="size-4" />
                {{ rating() | number: '1.1-1' }}
                @if (voteCount()) {
                  <span class="font-medium text-accent/70">· {{ voteCount() | number }}</span>
                }
              </span>
            }
            @for (fact of facts(); track fact.label) {
              <span class="glass inline-flex h-9 items-center gap-2 rounded-full px-3.5 text-sm text-white/85">
                <app-icon [name]="fact.icon" class="size-4 text-white/60" /> {{ fact.label }}
              </span>
            }
          </div>

          @if (genres().length) {
            <ul class="mt-4 flex flex-wrap gap-2">
              @for (g of genres(); track g.id) {
                <li class="text-sm text-white/70 after:ml-2 after:text-white/30 after:content-['/'] last:after:content-none">{{ g.name }}</li>
              }
            </ul>
          }

          @if (overview()) {
            <p class="mt-6 max-w-3xl text-base leading-relaxed text-white/80 sm:text-lg">{{ overview() }}</p>
          }

          <div class="mt-8 flex flex-wrap items-center gap-3">
            <ng-content />
            @if (hasTrailer()) {
              <button type="button" class="btn btn-secondary btn-lg" (click)="playTrailer.emit()">
                <app-icon name="play" filled class="size-4" /> Play trailer
              </button>
            }
          </div>
        </div>
      </div>
    </section>
  `,
})
export class TitleHero {
  readonly kind = input.required<string>();
  readonly title = input.required<string>();
  readonly posterUrl = input<string | null>(null);
  readonly backdropUrl = input<string | null>(null);
  readonly rating = input<number | null>(null);
  readonly voteCount = input<number | null>(null);
  readonly facts = input<HeroFact[]>([]);
  readonly genres = input<Genre[]>([]);
  readonly tagline = input<string | null>(null);
  readonly overview = input<string | null>(null);
  readonly hasTrailer = input(false);
  readonly playTrailer = output<void>();
}
