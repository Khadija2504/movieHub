import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { HealthService } from '../../../core/services/health.service';

@Component({
  selector: 'app-home-page',
  imports: [RouterLink],
  templateUrl: './home-page.html',
})
export class HomePage {
  /** Temporary backend connectivity check (replaced by real content in Phase 2). */
  protected readonly health = toSignal(inject(HealthService).check());

  protected readonly features = [
    {
      title: 'Stream',
      text: 'Watch movies and series included in your plan, anytime.',
      icon: '▶',
    },
    {
      title: 'Buy or rent',
      text: 'Own your favourites or rent them for 48 hours.',
      icon: '◆',
    },
    {
      title: 'Book a seat',
      text: 'Pick your cinema, choose your seat and get a QR ticket.',
      icon: '◉',
    },
  ];
}
