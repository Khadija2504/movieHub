import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of } from 'rxjs';

import { environment } from '../../../environments/environment';

export interface HealthStatus {
  status: 'UP' | 'DEGRADED' | 'DOWN';
  database: 'UP' | 'DOWN' | 'UNKNOWN';
  timestamp?: string;
}

@Injectable({ providedIn: 'root' })
export class HealthService {
  private readonly http = inject(HttpClient);

  /** Never errors: an unreachable backend is reported as DOWN. */
  check(): Observable<HealthStatus> {
    return this.http
      .get<HealthStatus>(`${environment.apiUrl}/health`)
      .pipe(catchError(() => of<HealthStatus>({ status: 'DOWN', database: 'UNKNOWN' })));
  }
}
