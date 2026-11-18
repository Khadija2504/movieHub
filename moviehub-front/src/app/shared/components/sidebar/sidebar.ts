import { Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

export interface SidebarLink {
  label: string;
  path: string;
}

export interface SidebarSection {
  title?: string;
  links: SidebarLink[];
}

/** Vertical navigation used by the user and admin layouts. */
@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive],
  template: `
    <nav class="flex flex-col gap-6" [attr.aria-label]="label()">
      @for (section of sections(); track $index) {
        <div>
          @if (section.title) {
            <p class="mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-muted">
              {{ section.title }}
            </p>
          }
          <ul class="space-y-0.5">
            @for (link of section.links; track link.path) {
              <li>
                <a
                  [routerLink]="link.path"
                  routerLinkActive="!bg-surface-raised !text-foreground"
                  class="block rounded-lg px-3 py-2 text-sm text-muted transition-colors hover:text-foreground"
                  >{{ link.label }}</a
                >
              </li>
            }
          </ul>
        </div>
      }
    </nav>
  `,
})
export class Sidebar {
  readonly sections = input.required<SidebarSection[]>();
  readonly label = input('Sidebar');
}
