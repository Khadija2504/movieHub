import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { Footer } from '../../shared/components/footer/footer';
import { Navbar } from '../../shared/components/navbar/navbar';

/** Public pages. The navbar is fixed: pages either start with a full-bleed hero or use the .page-top spacing. */
@Component({
  selector: 'app-public-layout',
  imports: [RouterOutlet, Navbar, Footer],
  template: `
    <div class="flex min-h-dvh flex-col">
      <app-navbar />
      <main class="flex-1">
        <router-outlet />
      </main>
      <app-footer />
    </div>
  `,
})
export class PublicLayout {}
