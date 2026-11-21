import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, forkJoin, of, switchMap } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage } from '../../../core/http/api-error.util';
import { MediaType } from '../../../core/models/catalog';
import { MAX_REVIEW_LENGTH, Review, ReviewSummary } from '../../../core/models/user-features';
import { ReviewService } from '../../../core/services/review.service';
import { ToastService } from '../../../core/ui/toast.service';
import { Avatar } from '../../../shared/components/avatar/avatar';
import { Icon } from '../../../shared/components/icon/icon';
import { StarRating } from '../../../shared/components/star-rating/star-rating';

@Component({
  selector: 'app-reviews-section',
  imports: [FormsModule, DatePipe, DecimalPipe, Avatar, Icon, StarRating],
  templateUrl: './reviews-section.html',
})
export class ReviewsSection {
  readonly mediaType = input.required<MediaType>();
  readonly tmdbId = input.required<number>();
  readonly title = input('this title');

  protected readonly auth = inject(AuthService);
  private readonly reviews = inject(ReviewService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  protected readonly maxLength = MAX_REVIEW_LENGTH;
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly summary = signal<ReviewSummary | null>(null);
  protected readonly items = signal<Review[]>([]);
  protected readonly page = signal(1);
  protected readonly totalPages = signal(0);
  protected readonly loadingMore = signal(false);

  // Composer
  protected readonly mine = signal<Review | null>(null);
  protected readonly editing = signal(false);
  protected readonly rating = signal(0);
  protected readonly comment = signal('');
  protected readonly saving = signal(false);
  protected readonly confirmingDelete = signal(false);

  /** Others' reviews (mine is shown separately in the composer area). */
  protected readonly others = computed(() => {
    const myId = this.auth.user()?.id;
    return this.items().filter((r) => r.author.id !== myId);
  });

  /** Distribution from 10 down to 1, with bar widths in %. */
  protected readonly bars = computed(() => {
    const s = this.summary();
    if (!s) return [];
    const max = Math.max(1, ...s.distribution);
    return s.distribution
      .map((count, i) => ({ rating: i + 1, count, width: (count / max) * 100 }))
      .reverse();
  });

  private readonly key = computed(() => ({
    type: this.mediaType(),
    id: this.tmdbId(),
    userId: this.auth.user()?.id ?? null,
  }));
  private readonly reload = signal(0);

  constructor() {
    toObservable(computed(() => ({ ...this.key(), reload: this.reload() })))
      .pipe(
        switchMap(({ type, id, userId }) => {
          this.loading.set(true);
          this.error.set(null);
          return forkJoin({
            summary: this.reviews.summary(type, id),
            page: this.reviews.forTitle(type, id, 1),
            mine: userId ? this.reviews.mine(type, id).pipe(catchError(() => of(null))) : of(null),
          }).pipe(catchError((e: unknown) => of({ failed: errorMessage(e) })));
        }),
        takeUntilDestroyed(),
      )
      .subscribe((result) => {
        this.loading.set(false);
        if ('failed' in result) {
          this.error.set(result.failed);
          return;
        }
        this.summary.set(result.summary);
        this.items.set(result.page.content);
        this.page.set(result.page.page);
        this.totalPages.set(result.page.totalPages);
        this.setMine(result.mine);
      });
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }

  protected loadMore(): void {
    this.loadingMore.set(true);
    this.reviews.forTitle(this.mediaType(), this.tmdbId(), this.page() + 1).subscribe({
      next: (page) => {
        const known = new Set(this.items().map((r) => r.id));
        this.items.update((list) => [...list, ...page.content.filter((r) => !known.has(r.id))]);
        this.page.set(page.page);
        this.totalPages.set(page.totalPages);
        this.loadingMore.set(false);
      },
      error: (e: unknown) => {
        this.loadingMore.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }

  protected signIn(): void {
    void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
  }

  protected startEdit(): void {
    const mine = this.mine();
    this.rating.set(mine?.rating ?? 0);
    this.comment.set(mine?.comment ?? '');
    this.editing.set(true);
  }

  protected cancelEdit(): void {
    this.editing.set(false);
    this.confirmingDelete.set(false);
  }

  protected save(): void {
    if (!this.rating() || this.saving()) return;
    this.saving.set(true);
    const comment = this.comment().trim();
    this.reviews
      .save(this.mediaType(), this.tmdbId(), { rating: this.rating(), comment: comment || null })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.editing.set(false);
          this.toast.success('Thanks! Your review has been saved');
          this.retry();
        },
        error: (e: unknown) => {
          this.saving.set(false);
          this.toast.error(errorMessage(e, 'Could not save your review'));
        },
      });
  }

  protected remove(): void {
    this.saving.set(true);
    this.reviews.delete(this.mediaType(), this.tmdbId()).subscribe({
      next: () => {
        this.saving.set(false);
        this.confirmingDelete.set(false);
        this.toast.success('Your review was deleted');
        this.retry();
      },
      error: (e: unknown) => {
        this.saving.set(false);
        this.toast.error(errorMessage(e, 'Could not delete your review'));
      },
    });
  }

  private setMine(review: Review | null): void {
    this.mine.set(review);
    this.editing.set(false);
    this.rating.set(review?.rating ?? 0);
    this.comment.set(review?.comment ?? '');
  }
}
