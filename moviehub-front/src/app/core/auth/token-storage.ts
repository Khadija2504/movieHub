import { Injectable } from '@angular/core';

const ACCESS_KEY = 'mh.accessToken';
const REFRESH_KEY = 'mh.refreshToken';

/**
 * Persists tokens in localStorage so the session survives a page reload.
 * All access is wrapped in try/catch: storage can be unavailable (private mode, blocked cookies).
 */
@Injectable({ providedIn: 'root' })
export class TokenStorage {
  get accessToken(): string | null {
    return this.read(ACCESS_KEY);
  }

  get refreshToken(): string | null {
    return this.read(REFRESH_KEY);
  }

  save(accessToken: string, refreshToken: string): void {
    this.write(ACCESS_KEY, accessToken);
    this.write(REFRESH_KEY, refreshToken);
  }

  clear(): void {
    this.write(ACCESS_KEY, null);
    this.write(REFRESH_KEY, null);
  }

  private read(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }

  private write(key: string, value: string | null): void {
    try {
      if (value === null) {
        localStorage.removeItem(key);
      } else {
        localStorage.setItem(key, value);
      }
    } catch {
      // ignore: the session just won't survive a reload
    }
  }
}
