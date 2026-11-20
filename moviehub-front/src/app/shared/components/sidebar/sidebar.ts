import { Component, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { Icon, IconName } from '../icon/icon';

export interface SidebarLink {
  label: string;
  path: string;
  icon: IconName;
}

export interface SidebarSection {
  title?: string;
  links: SidebarLink[];
}

/** Vertical navigation used by the user and admin layouts. */
@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive, Icon],
  template: `
    <nav class="flex flex-col gap-6" [attr.aria-label]="label()">
      @for (section of sections(); track $index) {
        <div>
          @if (section.title) {
            <p class="mb-2 px-3 text-[0.68rem] font-semibold tracking-[0.18em] text-subtle uppercase">
              {{ section.title }}
            </p>
          }
          <ul class="space-y-0.5">
            @for (link of section.links; track link.path) {
              <li>
                <a
                  [routerLink]="link.path"
                  routerLinkActive="active"
                  (click)="navigate.emit()"
                  class="group relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-muted transition hover:bg-white/[0.04] hover:text-foreground"
                >
                  <app-icon [name]="link.icon" class="size-[1.15rem] transition-colors group-[.active]:text-primary" />
                  {{ link.label }}
                </a>
              </li>
            }
          </ul>
        </div>
      }
    </nav>
  `,
  styles: `
    a.active {
      color: var(--color-foreground);
      background: linear-gradient(90deg, rgb(255 59 92 / 0.14), rgb(255 59 92 / 0.02));
    }
    a.active::before {
      content: '';
      position: absolute;
      left: 0;
      top: 0.5rem;
      bottom: 0.5rem;
      width: 3px;
      border-radius: 999px;
      background: linear-gradient(180deg, var(--color-primary), var(--color-ember));
    }
  `,
})
export class Sidebar {
  readonly sections = input.required<SidebarSection[]>();
  readonly label = input('Sidebar');
  /** Emitted when a link is clicked (used to close the mobile drawer). */
  readonly navigate = output<void>();
}
