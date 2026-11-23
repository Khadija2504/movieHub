import { TestBed } from '@angular/core/testing';

import { SeatMap, SeatView } from '../../../core/models/cinema';
import { SeatMapView } from './seat-map';

function seat(id: number, label: string, column: number, extra: Partial<SeatView> = {}): SeatView {
  return { id, label, row: 'A', number: column + 1, gridRow: 0, gridColumn: column, type: 'STANDARD', status: 'AVAILABLE', ...extra };
}

const MAP: SeatMap = {
  screeningId: 1,
  rows: 1,
  columns: 4,
  capacity: 4,
  available: 3,
  seats: [seat(1, 'A1', 0), seat(2, 'A2', 1, { status: 'TAKEN' }), seat(3, 'A3', 2, { type: 'VIP' }), seat(4, 'A4', 3)],
};

describe('SeatMapView', () => {
  function render(inputs: Record<string, unknown>) {
    const fixture = TestBed.createComponent(SeatMapView);
    for (const [key, value] of Object.entries(inputs)) fixture.componentRef.setInput(key, value);
    fixture.detectChanges();
    return fixture;
  }

  it('is read-only by default', () => {
    const el: HTMLElement = render({ map: MAP }).nativeElement;
    expect(el.querySelectorAll('button').length).toBe(0);
  });

  it('lets free seats be picked and blocks taken ones', () => {
    const fixture = render({ map: MAP, selectable: true, selected: [3] });
    const picked: number[] = [];
    fixture.componentInstance.toggle.subscribe((s) => picked.push(s.id));
    const buttons = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')];

    expect(buttons.map((b) => b.disabled)).toEqual([false, true, false, false]);
    expect(buttons[2].getAttribute('aria-pressed')).toBe('true');
    expect(buttons[2].getAttribute('aria-label')).toBe('A3 · VIP (selected)');

    buttons[0].click();
    expect(picked).toEqual([1]);
  });

  it('disables other seats once the limit is reached', () => {
    const fixture = render({ map: MAP, selectable: true, selected: [1, 3], maxSelection: 2 });
    const buttons = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')];
    // Chosen seats stay clickable (to unselect them), the others are blocked.
    expect(buttons.map((b) => b.disabled)).toEqual([false, true, false, true]);
  });
});
