import { Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { combineLatest, switchMap } from 'rxjs';

import { errorMessage } from '../../../core/http/api-error.util';
import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { Page } from '../../../core/models/catalog';
import { WatchedMovie } from '../../../core/models/streaming';
import { StreamingService } from '../../../core/services/streaming.service';
import { ToastService } from '../../../core/ui/toast.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { Icon } from '../../../shared/components/icon/icon';
import { Pagination } from '../../../shared/components/pagination/pagination';
import { clock, shortDate } from '../../streaming/streaming-format';
import { PageHeader } from '../components/page-header';

@Component({
  selector: 'app-history-page',
  imports: [RouterLink, Icon, ErrorState, Pagination, PageHeader],
  template: `
    <app-page-header heading="Watch History" icon="history" subheading="What you watched on MovieHub">
      @if (state().data?.content?.length) {
        @if (confirmingClear()) {
          <div class="flex items-center gap-2 text-sm">
            <span class="text-muted">Clear everything?</span>
            <button type="button" class="btn btn-sm bg-danger text-white hover:bg-danger/90" [disabled]="busy()" (click)="clear()">Clear</button>
            <button type="button" class="btn btn-ghost btn-sm" (click)="confirmingClear.set(false)">Keep</button>
          </div>
        } @else {
          <button type="button" class="btn btn-ghost btn-sm text-muted" (click)="confirmingClear.set(true)"><app-icon name="x" class="size-4" /> Clear history</button>
        }
      }
    </app-page-header>

    @if (state().error; as error) {
      <app-error-state [message]="error" (retry)="reload()" />
    } @else if (state().loading) {
      <div class="space-y-3">
        @for (i of [1, 2, 3, 4]; track i) {
          <div class="skeleton h-24 rounded-card"></div>
        }
      </div>
    } @else if (state().data; as page) {
      @if (page.content.length) {
        <ul class="space-y-3">
          @for (w of page.content; track w.tmdbId; let i = $index) {
            <li class="card flex animate-fade-up items-center gap-4 p-3 sm:p-4" [style.animation-delay.ms]="i * 40">
              <a [routerLink]="w.canWatch ? ['/watch', w.tmdbId] : ['/movies', w.tmdbId]" class="group relative aspect-video w-32 shrink-0 overflow-hidden rounded-xl bg-surface-raised sm:w-44" [attr.aria-label]="(w.canWatch ? 'Watch ' : 'View ') + w.title">
                @if (w.backdropUrl || w.posterUrl) {
                  <img [src]="w.backdropUrl ?? w.posterUrl" alt="" class="h-full w-full object-cover" loading="lazy" />
                }
                @if (w.canWatch) {
                  <span class="absolute inset-0 grid place-items-center bg-black/40 opacity-0 transition group-hover:opacity-100"><app-icon name="play" filled class="size-6 text-white" /></span>
                }
                <div class="absolute inset-x-0 bottom-0 h-1 bg-white/20"><div class="h-full bg-primary" [style.width.%]="w.percent"></div></div>
              </a>
              <div class="min-w-0 flex-1">
                <a [routerLink]="['/movies', w.tmdbId]" class="block truncate font-display font-semibold hover:text-primary">{{ w.title }}</a>
                <p class="mt-0.5 text-xs text-muted">{{ w.completed ? 'Finished' : clock(w.position) + ' of ' + clock(w.duration) + ' · ' + w.percent + '%' }}</p>
                <p class="mt-0.5 text-xs text-subtle">Watched {{ date(w.lastWatchedAt) }}@if (!w.canWatch) { · no longer in your library }</p>
              </div>
              <div class="flex shrink-0 items-center gap-1">
                @if (w.canWatch) {
                  <a [routerLink]="['/watch', w.tmdbId]" class="btn btn-secondary btn-sm hidden sm:inline-flex">{{ w.completed ? 'Watch again' : 'Resume' }}</a>
                }
                <button type="button" class="btn btn-ghost btn-sm text-muted" [attr.aria-label]="'Remove ' + w.title + ' from history'" [disabled]="busy()" (click)="remove(w)">
                  <app-icon name="x" class="size-4" />
                </button>
              </div>
            </li>
          }
        </ul>
        <app-pagination [page]="page.page" [totalPages]="page.totalPages" (pageChange)="goToPage($event)" />
      } @else {
        <div class="card flex flex-col items-center px-6 py-16 text-center">
          <span class="grid size-16 place-items-center rounded-2xl bg-primary/10 text-primary ring-1 ring-primary/20"><app-icon name="history" class="size-7" /></span>
          <p class="mt-5 font-display text-lg font-semibold">Nothing watched yet</p>
          <p class="mt-1 max-w-sm text-sm text-muted">Movies you play on MovieHub show up here, with where you stopped.</p>
          <a routerLink="/plans" class="btn btn-primary mt-6"><app-icon name="crown" class="size-4" /> See plans</a>
        </div>
      }
    }
  `,
})
export class HistoryPage {
  private readonly streaming = inject(StreamingService);
  private readonly toast = inject(ToastService);

  private readonly page = signal(1);
  private readonly attempt = signal(0);
  protected readonly busy = signal(false);
  protected readonly confirmingClear = signal(false);
  protected readonly clock = clock;

  protected readonly state = toSignal(
    combineLatest([toObservable(this.page), toObservable(this.attempt)]).pipe(
      switchMap(([page]) => this.streaming.history(page).pipe(toLoadState())),
    ),
    { initialValue: LOADING as LoadState<Page<WatchedMovie>> },
  );

  protected date(instant: string): string {
    return shortDate(instant);
  }

  protected remove(w: WatchedMovie): void {
    this.busy.set(true);
    this.streaming.removeFromHistory(w.tmdbId).subscribe({
      next: () => {
        this.busy.set(false);
        this.reload();
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }

  protected clear(): void {
    this.busy.set(true);
    this.streaming.clearHistory().subscribe({
      next: () => {
        this.busy.set(false);
        this.confirmingClear.set(false);
        this.page.set(1);
        this.reload();
        this.toast.success('Watch history cleared.');
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }

  protected goToPage(page: number): void {
    this.page.set(page);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected reload(): void {
    this.attempt.update((n) => n + 1);
  }
}
