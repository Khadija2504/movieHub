import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { combineLatest, switchMap } from 'rxjs';

import { errorMessage } from '../../../core/http/api-error.util';
import { LOADING, LoadState, toLoadState } from '../../../core/http/load-state';
import { Page } from '../../../core/models/catalog';
import { Review } from '../../../core/models/user-features';
import { ReviewService } from '../../../core/services/review.service';
import { ToastService } from '../../../core/ui/toast.service';
import { ErrorState } from '../../../shared/components/error-state/error-state';
import { Icon } from '../../../shared/components/icon/icon';
import { Pagination } from '../../../shared/components/pagination/pagination';
import { StarRating } from '../../../shared/components/star-rating/star-rating';
import { PageHeader } from '../components/page-header';

@Component({
  selector: 'app-my-reviews-page',
  imports: [RouterLink, DatePipe, ErrorState, Icon, Pagination, StarRating, PageHeader],
  template: `
    <app-page-header heading="My Reviews" icon="message" [subheading]="subheading()" />

    @if (state().error; as error) {
      <app-error-state [message]="error" (retry)="reload()" />
    } @else if (state().loading) {
      <div class="space-y-4" aria-hidden="true">
        @for (i of [1, 2, 3]; track i) {
          <div class="card flex gap-5 p-4"><div class="skeleton aspect-2/3 w-20 rounded-xl"></div><div class="flex-1 space-y-3 py-2"><div class="skeleton h-5 w-1/3 rounded-full"></div><div class="skeleton h-4 w-40 rounded-full"></div><div class="skeleton h-4 w-3/4 rounded-full"></div></div></div>
        }
      </div>
    } @else if (state().data; as page) {
      @if (page.content.length) {
        <ul class="space-y-4">
          @for (review of page.content; track review.id) {
            <li class="card card-hover flex gap-5 p-4 sm:p-5" [class.opacity-50]="deleting() === review.id">
              <a [routerLink]="link(review)" class="block w-20 shrink-0 sm:w-24">
                <div class="aspect-2/3 overflow-hidden rounded-xl bg-surface-raised ring-1 ring-border">
                  @if (review.posterUrl) {
                    <img [src]="review.posterUrl" alt="" class="h-full w-full object-cover" loading="lazy" />
                  }
                </div>
              </a>
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-start justify-between gap-2">
                  <div class="min-w-0">
                    <a [routerLink]="link(review)" class="line-clamp-1 font-display font-semibold transition-colors hover:text-primary">{{ review.title }}</a>
                    <p class="text-xs text-subtle">
                      {{ review.mediaType === 'MOVIE' ? 'Movie' : 'TV Series' }} · {{ review.updatedAt | date: 'mediumDate' }}
                      @if (review.edited) { · edited }
                    </p>
                  </div>
                  <div class="flex gap-1">
                    <a [routerLink]="link(review)" fragment="reviews" class="btn btn-ghost btn-sm">Edit</a>
                    @if (confirmId() === review.id) {
                      <button type="button" class="btn btn-sm bg-danger text-white" (click)="remove(review)">Confirm</button>
                      <button type="button" class="btn btn-ghost btn-sm" (click)="confirmId.set(null)">Cancel</button>
                    } @else {
                      <button type="button" class="btn btn-ghost btn-sm text-danger" (click)="confirmId.set(review.id)">Delete</button>
                    }
                  </div>
                </div>
                <app-star-rating class="mt-2 block" [value]="review.rating" readonly size="sm" />
                @if (review.comment) {
                  <p class="mt-2 line-clamp-3 whitespace-pre-line text-sm leading-relaxed text-muted">{{ review.comment }}</p>
                }
              </div>
            </li>
          }
        </ul>
        <app-pagination [page]="page.page" [totalPages]="page.totalPages" (pageChange)="page$.set($event)" />
      } @else {
        <div class="card flex flex-col items-center px-6 py-16 text-center">
          <span class="grid size-16 place-items-center rounded-2xl bg-accent/12 text-accent ring-1 ring-accent/25">
            <app-icon name="star" filled class="size-7" />
          </span>
          <p class="mt-5 font-display text-lg font-semibold">You haven't reviewed anything yet</p>
          <p class="mt-1 max-w-sm text-sm text-muted">Rate the movies and series you've watched to help others discover great titles.</p>
          <a routerLink="/movies" class="btn btn-primary mt-6"><app-icon name="film" class="size-4" /> Find something to rate</a>
        </div>
      }
    }
  `,
})
export class MyReviewsPage {
  private readonly reviews = inject(ReviewService);
  private readonly toast = inject(ToastService);

  protected readonly page$ = signal(1);
  private readonly attempt = signal(0);
  protected readonly confirmId = signal<number | null>(null);
  protected readonly deleting = signal<number | null>(null);

  protected readonly state = toSignal(
    combineLatest([toObservable(this.page$), toObservable(this.attempt)]).pipe(
      switchMap(([page]) => this.reviews.myReviews(page).pipe(toLoadState())),
    ),
    { initialValue: LOADING as LoadState<Page<Review>> },
  );

  protected subheading(): string {
    const total = this.state().data?.totalResults ?? 0;
    return total ? `${total} review${total > 1 ? 's' : ''}` : 'Your ratings and reviews';
  }

  protected link(review: Review): (string | number)[] {
    return [review.mediaType === 'MOVIE' ? '/movies' : '/series', review.tmdbId];
  }

  protected reload(): void {
    this.attempt.update((n) => n + 1);
  }

  protected remove(review: Review): void {
    this.confirmId.set(null);
    this.deleting.set(review.id);
    this.reviews.delete(review.mediaType, review.tmdbId).subscribe({
      next: () => {
        this.deleting.set(null);
        this.toast.success('Review deleted');
        this.reload();
      },
      error: (e: unknown) => {
        this.deleting.set(null);
        this.toast.error(errorMessage(e, 'Could not delete the review'));
      },
    });
  }
}
