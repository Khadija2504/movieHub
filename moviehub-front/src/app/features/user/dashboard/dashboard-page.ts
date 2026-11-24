import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { MediaSummary } from '../../../core/models/catalog';
import { CatalogService } from '../../../core/services/catalog.service';
import { WatchedMovie } from '../../../core/models/streaming';
import { ProfileService } from '../../../core/services/profile.service';
import { StreamingService } from '../../../core/services/streaming.service';
import { WatchlistService } from '../../../core/services/watchlist.service';
import { Icon, IconName } from '../../../shared/components/icon/icon';
import { MediaCarousel } from '../../../shared/components/media-carousel/media-carousel';

interface Stat {
  label: string;
  icon: IconName;
  link: string;
  tint: string;
}

@Component({
  selector: 'app-dashboard-page',
  imports: [RouterLink, Icon, MediaCarousel],
  template: `
    <!-- Welcome banner -->
    <section class="relative overflow-hidden rounded-[1.75rem] border border-border bg-surface p-8 sm:p-10">
      <div class="pointer-events-none absolute -right-20 -top-24 size-80 rounded-full bg-primary/25 blur-[100px]" aria-hidden="true"></div>
      <div class="pointer-events-none absolute -bottom-32 right-40 size-72 rounded-full bg-ember/15 blur-[100px]" aria-hidden="true"></div>
      <p class="relative text-sm text-muted">{{ greeting() }},</p>
      <h1 class="relative mt-1 text-3xl font-extrabold sm:text-4xl">{{ auth.user()?.firstName }} 👋</h1>
      <p class="relative mt-3 max-w-lg text-muted">Pick up where you left off, discover something new, or book your next cinema night.</p>
      <div class="relative mt-6 flex flex-wrap gap-3">
        <a routerLink="/movies" class="btn btn-primary"><app-icon name="film" class="size-4" /> Browse movies</a>
        <a routerLink="/cinemas" class="btn btn-secondary"><app-icon name="ticket" class="size-4" /> Book tickets</a>
      </div>
    </section>

    <!-- Stats -->
    <section class="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      @for (stat of stats; track stat.label) {
        <a [routerLink]="stat.link" class="card card-hover group flex items-center gap-4 p-5">
          <span class="grid size-12 place-items-center rounded-2xl ring-1" [class]="stat.tint">
            <app-icon [name]="stat.icon" class="size-5" />
          </span>
          <div class="flex-1">
            <p class="text-sm text-muted">{{ stat.label }}</p>
            <p class="font-display text-2xl font-bold">{{ counts()[stat.label] ?? 0 }}</p>
          </div>
          <app-icon name="chevron-right" class="size-4 text-subtle transition-transform group-hover:translate-x-0.5" />
        </a>
      }
    </section>

    <!-- Continue watching -->
    <section class="mt-10" aria-labelledby="continue-heading">
      <div class="mb-5 flex items-center gap-3">
        <span class="grid size-9 place-items-center rounded-xl bg-primary/12 text-primary ring-1 ring-primary/20">
          <app-icon name="history" class="size-[1.1rem]" />
        </span>
        <h2 id="continue-heading" class="section-title flex-1">Continue watching</h2>
        @if (continueWatching().data?.length) {
          <a routerLink="/account/history" class="text-sm font-semibold text-muted hover:text-foreground">History</a>
        }
      </div>
      @if (continueWatching().loading) {
        <div class="flex gap-4 overflow-hidden">
          @for (i of [1, 2, 3]; track i) {
            <div class="skeleton aspect-video w-72 shrink-0 rounded-card"></div>
          }
        </div>
      } @else if (continueWatching().data?.length) {
        <ul class="-mx-1 flex snap-x gap-4 overflow-x-auto px-1 pb-3">
          @for (w of continueWatching().data; track w.tmdbId) {
            <li class="w-64 shrink-0 snap-start sm:w-72">
              <a [routerLink]="['/watch', w.tmdbId]" class="card card-hover group block overflow-hidden" [attr.aria-label]="'Resume ' + w.title">
                <div class="relative aspect-video bg-surface-raised">
                  @if (w.backdropUrl || w.posterUrl) {
                    <img [src]="w.backdropUrl ?? w.posterUrl" alt="" class="h-full w-full object-cover" loading="lazy" />
                  }
                  <div class="absolute inset-0 bg-linear-to-t from-black/70 to-transparent"></div>
                  <span class="absolute inset-0 m-auto grid size-12 place-items-center rounded-full bg-primary/90 text-white opacity-0 shadow-glow transition group-hover:opacity-100"><app-icon name="play" filled class="ml-0.5 size-5" /></span>
                  <div class="absolute inset-x-0 bottom-0 h-1 bg-white/20"><div class="h-full bg-primary" [style.width.%]="w.percent"></div></div>
                </div>
                <div class="p-3">
                  <p class="truncate font-semibold">{{ w.title }}</p>
                  <p class="text-xs text-muted">{{ remaining(w.duration - w.position) }} left</p>
                </div>
              </a>
            </li>
          }
        </ul>
      } @else {
        <div class="card flex flex-col items-center px-6 py-12 text-center">
          <span class="grid size-14 place-items-center rounded-2xl bg-white/5 text-muted ring-1 ring-border">
            <app-icon name="play" class="size-6" />
          </span>
          <p class="mt-4 font-display font-semibold">Nothing in progress yet</p>
          <p class="mt-1 max-w-sm text-sm text-muted">Titles you start watching will show up here so you can jump right back in.</p>
          <a routerLink="/plans" class="btn btn-secondary btn-sm mt-5"><app-icon name="crown" class="size-4" /> See plans</a>
        </div>
      }
    </section>

    <app-media-carousel
      heading="Recommended for you"
      subheading="Trending picks this week"
      icon="sparkles"
      [items]="recommended().data ?? []"
      [loading]="recommended().loading"
      [error]="recommended().error"
      showType
    />
  `,
})
export class DashboardPage {
  protected readonly auth = inject(AuthService);

  protected readonly greeting = computed(() => {
    const hour = new Date().getHours();
    return hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
  });

  protected readonly stats: Stat[] = [
    { label: 'Watchlist', icon: 'bookmark', link: '/account/watchlist', tint: 'bg-primary/12 text-primary ring-primary/20' },
    { label: 'Library', icon: 'library', link: '/account/library', tint: 'bg-violet-500/12 text-violet-400 ring-violet-500/20' },
    { label: 'Upcoming bookings', icon: 'ticket', link: '/account/bookings', tint: 'bg-accent/12 text-accent ring-accent/25' },
    { label: 'Reviews', icon: 'message', link: '/account/reviews', tint: 'bg-sky-500/12 text-sky-400 ring-sky-500/20' },
  ];

  private readonly watchlist = inject(WatchlistService);
  private readonly stats$ = toSignal(
    inject(ProfileService)
      .stats()
      .pipe(catchError(() => of(null))),
    { initialValue: null },
  );

  /** Watchlist count is live (updates as titles are added/removed). */
  protected readonly counts = computed<Partial<Record<string, number>>>(() => ({
    Watchlist: this.watchlist.count(),
    Library: this.stats$()?.libraryCount ?? 0,
    Reviews: this.stats$()?.reviewCount ?? 0,
    'Upcoming bookings': this.stats$()?.upcomingBookingCount ?? 0,
  }));

  protected readonly continueWatching = toSignal(inject(StreamingService).continueWatching().pipe(toLoadState()), {
    initialValue: LOADING as LoadState<WatchedMovie[]>,
  });

  /** "1 h 12 min" / "8 min" */
  protected remaining(seconds: number): string {
    const minutes = Math.max(1, Math.round(seconds / 60));
    return minutes >= 60 ? `${Math.floor(minutes / 60)} h ${minutes % 60} min` : `${minutes} min`;
  }

  protected readonly recommended = toSignal(
    inject(CatalogService)
      .trending()
      .pipe(
        map((page) => page.content),
        toLoadState(),
      ),
    { initialValue: LOADING as LoadState<MediaSummary[]> },
  );
}
