import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { AuthResponse } from '../models/auth';
import { User } from '../models/user';
import { UserStats } from '../models/user-features';

/** The current user's own account. Keeps AuthService's cached user in sync. */
@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly url = `${environment.apiUrl}/users/me`;

  updateName(firstName: string, lastName: string): Observable<User> {
    return this.http.put<User>(this.url, { firstName, lastName }).pipe(tap((user) => this.auth.updateUser(user)));
  }

  uploadAvatar(file: File): Observable<User> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<User>(`${this.url}/avatar`, form).pipe(tap((user) => this.auth.updateUser(user)));
  }

  removeAvatar(): Observable<User> {
    return this.http.delete<User>(`${this.url}/avatar`).pipe(tap((user) => this.auth.updateUser(user)));
  }

  /** Other sessions are signed out; this one continues with the returned tokens. */
  changePassword(currentPassword: string, newPassword: string): Observable<User> {
    return this.http
      .put<AuthResponse>(`${this.url}/password`, { currentPassword, newPassword })
      .pipe(map((response) => this.auth.applySession(response)));
  }

  stats(): Observable<UserStats> {
    return this.http.get<UserStats>(`${this.url}/stats`);
  }

  deleteAccount(password: string): Observable<void> {
    return this.http
      .delete<void>(this.url, { body: { password } })
      .pipe(tap(() => this.auth.endSession()));
  }
}
