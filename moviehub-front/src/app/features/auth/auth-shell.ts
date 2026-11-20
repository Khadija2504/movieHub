import { Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of } from 'rxjs';

import { CatalogService } from '../../core/services/catalog.service';
import { Icon, IconName } from '../../shared/components/icon/icon';

/**
 * Split layout for login / register: a tilted wall of trending posters on the left (large screens),
 * the form card (projected content) on the right.
 */
@Component({
  selector: 'app-auth-shell',
  imports: [Icon],
  template: `
    <section class="container-page page-top grid min-h-dvh items-center gap-12 pb-16 lg:grid-cols-2 lg:gap-16">
      <!-- Showcase -->
      <div class="relative hidden h-[40rem] overflow-hidden rounded-[2rem] border border-border bg-surface lg:block" aria-hidden="true">
        <div class="absolute -inset-x-20 -inset-y-10 grid rotate-[-8deg] grid-cols-4 gap-4 opacity-80">
          @for (column of columns(); track $index; let c = $index) {
            <div class="space-y-4" [class.mt-24]="c % 2 === 1">
              @for (url of column; track $index) {
                <div class="aspect-2/3 overflow-hidden rounded-2xl bg-surface-raised ring-1 ring-white/10">
                  @if (url) {
                    <img [src]="url" alt="" class="h-full w-full object-cover" />
                  }
                </div>
              }
            </div>
          }
        </div>
        <div class="absolute inset-0 bg-linear-to-t from-background via-background/70 to-background/10"></div>
        <div class="absolute inset-x-0 bottom-0 p-10">
          <h2 class="text-3xl font-extrabold leading-tight">
            Movies, series and cinema nights.<br /><span class="text-gradient">All in one place.</span>
          </h2>
          <ul class="mt-6 space-y-3">
            @for (perk of perks; track perk.text) {
              <li class="flex items-center gap-3 text-sm text-white/80">
                <span class="grid size-8 place-items-center rounded-lg bg-white/10 backdrop-blur"><app-icon [name]="perk.icon" class="size-4" /></span>
                {{ perk.text }}
              </li>
            }
          </ul>
        </div>
      </div>

      <!-- Form -->
      <div class="mx-auto w-full max-w-md animate-fade-up">
        <h1 class="text-3xl font-extrabold sm:text-4xl">{{ heading() }}</h1>
        <p class="mt-2 text-muted">{{ subheading() }}</p>
        <div class="mt-8">
          <ng-content />
        </div>
      </div>
    </section>
  `,
})
export class AuthShell {
  readonly heading = input.required<string>();
  readonly subheading = input('');

  protected readonly perks: { icon: IconName; text: string }[] = [
    { icon: 'bookmark', text: 'Build your personal watchlist' },
    { icon: 'play', text: 'Stream, rent or buy in HD' },
    { icon: 'ticket', text: 'Book cinema seats with a QR ticket' },
  ];

  private readonly posters = toSignal(
    inject(CatalogService)
      .trending()
      .pipe(
        map((page) => page.content.map((item) => item.posterUrl).filter((url): url is string => !!url)),
        catchError(() => of<string[]>([])),
      ),
    { initialValue: [] as string[] },
  );

  /** 4 columns x 4 posters; empty tiles when the catalog is unavailable. */
  protected readonly columns = computed(() => {
    const urls = this.posters();
    return Array.from({ length: 4 }, (_, c) =>
      Array.from({ length: 4 }, (_, r) => (urls.length ? urls[(c * 4 + r) % urls.length] : null)),
    );
  });
}
