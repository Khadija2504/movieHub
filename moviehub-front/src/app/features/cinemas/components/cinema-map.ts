import { Component, DestroyRef, ElementRef, afterNextRender, effect, inject, input, output, signal, viewChild } from '@angular/core';
import type * as Leaflet from 'leaflet';

export interface MapMarker {
  id: number;
  name: string;
  subtitle: string;
  latitude: number;
  longitude: number;
}

/** Standard OpenStreetMap tiles (no key needed), darkened with a CSS filter to match the theme. */
const TILES = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

/**
 * Dark OpenStreetMap map with brand-coloured pins. Leaflet is loaded lazily, only on pages that show a map.
 * Size comes from the host class (e.g. h-72).
 */
@Component({
  selector: 'app-cinema-map',
  host: { class: 'relative block overflow-hidden rounded-2xl bg-surface-raised ring-1 ring-border' },
  template: `
    <div #container class="absolute inset-0 z-0"></div>
    @if (failed()) {
      <p class="absolute inset-0 grid place-items-center p-6 text-center text-sm text-muted">The map could not be loaded.</p>
    }
  `,
  styles: `
    :host ::ng-deep .leaflet-container { background: var(--color-surface-raised); font-family: var(--font-sans); }
    :host ::ng-deep .leaflet-tile-pane { filter: invert(1) hue-rotate(180deg) brightness(0.85) contrast(0.9) saturate(0.35); }
    :host ::ng-deep .leaflet-control-attribution { background: rgb(7 7 12 / 0.7); color: var(--color-subtle); font-size: 10px; }
    :host ::ng-deep .leaflet-control-attribution a { color: var(--color-muted); }
    :host ::ng-deep .leaflet-bar a { background: var(--color-surface-raised); color: var(--color-foreground); border-color: var(--color-border); }
    :host ::ng-deep .leaflet-bar a:hover { background: var(--color-surface-hover); }
    :host ::ng-deep .leaflet-tooltip { background: var(--color-surface-raised); color: var(--color-foreground); border: 1px solid var(--color-border-strong); border-radius: 0.75rem; padding: 0.4rem 0.7rem; box-shadow: 0 10px 30px rgb(0 0 0 / 0.5); }
    :host ::ng-deep .leaflet-tooltip-top::before { border-top-color: var(--color-border-strong); }
    :host ::ng-deep .mh-pin { display: grid; place-items: center; }
    :host ::ng-deep .mh-pin span { width: 18px; height: 18px; border-radius: 9999px; background: linear-gradient(135deg, var(--color-primary), var(--color-ember)); border: 3px solid #fff; box-shadow: 0 0 0 6px rgb(255 59 92 / 0.25), 0 6px 16px rgb(0 0 0 / 0.5); transition: transform 0.2s; }
    :host ::ng-deep .mh-pin.active span { transform: scale(1.35); box-shadow: 0 0 0 10px rgb(255 59 92 / 0.3), 0 6px 16px rgb(0 0 0 / 0.5); }
  `,
})
export class CinemaMap {
  readonly markers = input.required<MapMarker[]>();
  /** Highlighted marker (e.g. the card under the mouse). */
  readonly activeId = input<number | null>(null);
  readonly zoom = input(13);
  readonly markerClick = output<number>();

  protected readonly failed = signal(false);
  private readonly container = viewChild.required<ElementRef<HTMLElement>>('container');
  private readonly leaflet = signal<{ L: typeof Leaflet; map: Leaflet.Map } | null>(null);
  private layer: Leaflet.LayerGroup | null = null;
  private readonly pins = new Map<number, Leaflet.Marker>();
  private destroyed = false;

  constructor() {
    afterNextRender(async () => {
      try {
        // Leaflet is a CommonJS/UMD module: the API may sit on `default`.
        const mod = (await import('leaflet')) as typeof Leaflet & { default?: typeof Leaflet };
        const L = mod.default ?? mod;
        if (this.destroyed) return;
        const map = L.map(this.container().nativeElement, { scrollWheelZoom: false, zoomControl: true, attributionControl: true });
        L.tileLayer(TILES, { attribution: ATTRIBUTION, maxZoom: 19 }).addTo(map);
        this.leaflet.set({ L, map });
      } catch {
        this.failed.set(true);
      }
    });
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      this.leaflet()?.map.remove();
    });

    // (Re)draw markers when the list changes, and fit the view around them.
    effect(() => {
      const ready = this.leaflet();
      const markers = this.markers();
      if (!ready) return;
      const { L, map } = ready;
      this.layer?.remove();
      this.pins.clear();
      this.layer = L.layerGroup().addTo(map);
      for (const m of markers) {
        const pin = L.marker([m.latitude, m.longitude], { icon: this.icon(L, false), title: m.name, keyboard: true })
          .bindTooltip(`<strong>${escapeHtml(m.name)}</strong><br><span style="opacity:.7">${escapeHtml(m.subtitle)}</span>`, {
            direction: 'top',
            offset: [0, -12],
          })
          .on('click', () => this.markerClick.emit(m.id))
          .addTo(this.layer);
        this.pins.set(m.id, pin);
      }
      if (markers.length === 1) {
        map.setView([markers[0].latitude, markers[0].longitude], this.zoom());
      } else if (markers.length > 1) {
        map.fitBounds(L.latLngBounds(markers.map((m) => [m.latitude, m.longitude] as [number, number])), { padding: [40, 40] });
      } else {
        map.setView([46.6, 2.4], 5);
      }
    });

    effect(() => {
      const ready = this.leaflet();
      const active = this.activeId();
      this.markers();
      if (!ready) return;
      for (const [id, pin] of this.pins) {
        pin.setIcon(this.icon(ready.L, id === active));
        pin.setZIndexOffset(id === active ? 1000 : 0);
        if (id === active) pin.openTooltip();
        else pin.closeTooltip();
      }
    });
  }

  private icon(L: typeof Leaflet, active: boolean): Leaflet.DivIcon {
    return L.divIcon({ className: active ? 'mh-pin active' : 'mh-pin', html: '<span></span>', iconSize: [24, 24], iconAnchor: [12, 12] });
  }
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);
}
