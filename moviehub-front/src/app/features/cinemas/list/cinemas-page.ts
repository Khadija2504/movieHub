import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, combineLatest, of, switchMap } from 'rxjs';

import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { MediaSummary } from '../../../core/models/catalog';
import { CinemaSummary } from '../../../core/models/cinema';
import { CinemaService } from '../../../core/services/cinema.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { Icon } from '../../../shared/components/icon/icon';
import { MediaCarousel } from '../../../shared/components/media-carousel/media-carousel';
import { SCREEN_TYPE_BADGES, SCREEN_TYPE_LABELS } from '../cinema-format';
import { CinemaMap, MapMarker } from '../components/cinema-map';

@Component({
  selector: 'app-cinemas-page',
  imports: [RouterLink, Icon, ErrorState, MediaCarousel, CinemaMap],
  template: `
    <!-- Header -->
    <section class="relative overflow-hidden border-b border-border">
      <div class="pointer-events-none absolute -top-40 left-1/2 h-80 w-[60rem] -translate-x-1/2 rounded-full bg-primary/15 blur-[120px]" aria-hidden="true"></div>
      <div class="container-page page-top relative pb-8">
        <p class="eyebrow">On the big screen</p>
        <div class="mt-3 flex flex-wrap items-end justify-between gap-4">
          <h1 class="text-4xl font-extrabold sm:text-5xl">Find a cinema</h1>
          @if (state().data; as cinemas) {
            <p class="text-sm text-muted"><span class="font-semibold text-foreground">{{ cinemas.length }}</span> {{ cinemas.length === 1 ? 'cinema' : 'cinemas' }}</p>
          }
        </div>
        <p class="mt-3 max-w-xl text-muted">Pick a cinema to see what's on, then choose your showtime and your seats.</p>

        @if (cities().length > 1) {
          <div class="scrollbar-none -mx-4 mt-8 flex gap-2 overflow-x-auto px-4 sm:-mx-6 sm:px-6 lg:-mx-10 lg:px-10" role="list" aria-label="Cities">
            <button type="button" role="listitem" class="chip" [class.chip-active]="!city()" (click)="setCity(null)">All cities</button>
            @for (c of cities(); track c) {
              <button type="button" role="listitem" class="chip" [class.chip-active]="c === city()" (click)="setCity(c)">
                <app-icon name="map-pin" class="size-3.5" /> {{ c }}
              </button>
            }
          </div>
        }
      </div>
    </section>

    <section class="container-page pt-10">
      @if (state().error; as error) {
        <app-error-state [message]="error" (retry)="retry()" />
      } @else {
        <div class="grid gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
          <!-- Map (top on mobile, sticky on the right on desktop) -->
          <div class="lg:order-2">
            <app-cinema-map
              class="h-64 sm:h-80 lg:sticky lg:top-28 lg:h-[min(34rem,calc(100vh-9rem))]"
              [markers]="markers()"
              [activeId]="hovered()"
              (markerClick)="open($event)"
            />
          </div>

          <!-- List -->
          <div class="space-y-4">
            @if (state().loading) {
              @for (i of [1, 2, 3]; track i) {
                <div class="card flex gap-5 p-5">
                  <div class="skeleton size-14 rounded-2xl"></div>
                  <div class="flex-1 space-y-3">
                    <div class="skeleton h-5 w-2/3 rounded"></div>
                    <div class="skeleton h-4 w-1/2 rounded"></div>
                    <div class="skeleton h-6 w-1/3 rounded-full"></div>
                  </div>
                </div>
              }
            } @else if (state().data; as cinemas) {
              @for (cinema of cinemas; track cinema.id; let i = $index) {
                <a
                  [routerLink]="['/cinemas', cinema.id]"
                  class="card card-hover group flex animate-fade-up gap-5 p-5 sm:p-6"
                  [style.animation-delay.ms]="i * 60"
                  (mouseenter)="hovered.set(cinema.id)"
                  (mouseleave)="hovered.set(null)"
                  (focus)="hovered.set(cinema.id)"
                  (blur)="hovered.set(null)"
                >
                  <span class="grid size-14 shrink-0 place-items-center rounded-2xl bg-brand text-white shadow-glow">
                    <app-icon name="clapperboard" class="size-6" />
                  </span>
                  <div class="min-w-0 flex-1">
                    <div class="flex items-start justify-between gap-3">
                      <h2 class="font-display text-lg leading-snug font-bold group-hover:text-primary">{{ cinema.name }}</h2>
                      <app-icon name="chevron-right" class="mt-1 size-5 shrink-0 text-subtle transition-transform group-hover:translate-x-0.5" />
                    </div>
                    <p class="mt-1 flex items-center gap-1.5 text-sm text-muted">
                      <app-icon name="map-pin" class="size-4 shrink-0" />
                      <span class="truncate">{{ cinema.address }}, {{ cinema.postalCode ? cinema.postalCode + ' ' : '' }}{{ cinema.city }}</span>
                    </p>
                    <div class="mt-4 flex flex-wrap items-center gap-2">
                      @for (type of cinema.screenTypes; track type) {
                        <span class="rounded-full px-2.5 py-1 text-xs font-semibold ring-1" [class]="badges[type]">{{ labels[type] }}</span>
                      }
                      <span class="text-xs text-subtle">{{ cinema.roomCount }} {{ cinema.roomCount === 1 ? 'screen' : 'screens' }} · {{ cinema.seatCount }} seats</span>
                    </div>
                  </div>
                </a>
              } @empty {
                <div class="card flex flex-col items-center px-6 py-16 text-center">
                  <span class="grid size-14 place-items-center rounded-2xl bg-white/5 text-muted ring-1 ring-border"><app-icon name="clapperboard" class="size-6" /></span>
                  <p class="mt-4 font-display font-semibold">No cinemas yet</p>
                  <p class="mt-1 max-w-sm text-sm text-muted">Cinemas will appear here as soon as they open on MovieHub.</p>
                </div>
              }
            }
          </div>
        </div>
      }

      @if (nowShowing().length) {
        <app-media-carousel class="mt-6 block" heading="Now in cinemas" subheading="Movies with upcoming showtimes" icon="ticket" [items]="nowShowing()" />
      }
    </section>
  `,
})
export class CinemasPage {
  private readonly cinemaService = inject(CinemaService);
  private readonly router = inject(Router);

  /** Query param ?city= */
  readonly city = input<string | undefined>();

  protected readonly labels = SCREEN_TYPE_LABELS;
  protected readonly badges = SCREEN_TYPE_BADGES;
  protected readonly hovered = signal<number | null>(null);
  private readonly attempt = signal(0);

  protected readonly state = toSignal(
    combineLatest([toObservable(this.city), toObservable(this.attempt)]).pipe(
      switchMap(([city]) => this.cinemaService.cinemas(city).pipe(toLoadState())),
    ),
    { initialValue: LOADING as LoadState<CinemaSummary[]> },
  );

  protected readonly cities = toSignal(this.cinemaService.cities().pipe(catchError(() => of<string[]>([]))), {
    initialValue: [] as string[],
  });

  protected readonly nowShowing = toSignal(
    this.cinemaService.nowShowing().pipe(catchError(() => of<MediaSummary[]>([]))),
    { initialValue: [] as MediaSummary[] },
  );

  protected readonly markers = computed<MapMarker[]>(() =>
    (this.state().data ?? [])
      .filter((c) => c.latitude != null && c.longitude != null)
      .map((c) => ({ id: c.id, name: c.name, subtitle: `${c.address}, ${c.city}`, latitude: c.latitude!, longitude: c.longitude! })),
  );

  protected setCity(city: string | null): void {
    this.router.navigate([], { queryParams: { city }, queryParamsHandling: 'merge', replaceUrl: true });
  }

  protected open(id: number): void {
    this.router.navigate(['/cinemas', id]);
  }

  protected retry(): void {
    this.attempt.update((n) => n + 1);
  }
}
