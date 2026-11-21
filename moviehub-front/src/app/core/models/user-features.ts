import { MediaType } from './catalog';

/** Mirrors com.moviehub.watchlist.WatchlistDtos. */
export interface TitleKey {
  mediaType: MediaType;
  tmdbId: number;
}

export interface WatchlistItem {
  id: number;
  mediaType: MediaType;
  tmdbId: number;
  title: string;
  posterUrl: string | null;
  year: number | null;
  addedAt: string;
}

/** Mirrors com.moviehub.review.ReviewDtos. */
export interface ReviewAuthor {
  id: number;
  displayName: string;
  avatarUrl: string | null;
}

export interface Review {
  id: number;
  mediaType: MediaType;
  tmdbId: number;
  title: string;
  posterUrl: string | null;
  rating: number;
  comment: string | null;
  author: ReviewAuthor;
  edited: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ReviewSummary {
  /** null when nobody reviewed the title yet */
  average: number | null;
  count: number;
  /** counts for ratings 1..10 (index 0 = rating 1) */
  distribution: number[];
}

export interface ReviewRequest {
  rating: number;
  comment: string | null;
}

export interface UserStats {
  watchlistCount: number;
  reviewCount: number;
}

export const MAX_REVIEW_LENGTH = 2000;

export const titleKey = (type: MediaType, tmdbId: number) => `${type}:${tmdbId}`;
