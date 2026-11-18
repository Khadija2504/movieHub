import { HttpErrorResponse } from '@angular/common/http';

import { ApiError } from '../models/api-error';

/** Extracts the backend ApiError body from an HttpClient error, if there is one. */
export function toApiError(error: unknown): ApiError | null {
  if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') {
    const body = error.error as Partial<ApiError>;
    if (typeof body.message === 'string') {
      return body as ApiError;
    }
  }
  return null;
}

/** A user-friendly message for any HttpClient error. */
export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (error instanceof HttpErrorResponse && error.status === 0) {
    return 'Cannot reach the server. Check your connection and try again.';
  }
  return toApiError(error)?.message ?? fallback;
}
